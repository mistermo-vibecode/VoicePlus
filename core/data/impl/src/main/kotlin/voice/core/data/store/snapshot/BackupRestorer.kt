package voice.core.data.store.snapshot

import androidx.datastore.core.DataStore
import androidx.room.RoomDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.BookContent
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.internals.dao.BookCharacterDao
import voice.core.data.repo.internals.dao.BookContentDao
import voice.core.data.repo.internals.dao.BookmarkDao
import voice.core.data.repo.internals.dao.ChapterDao
import voice.core.data.repo.internals.dao.ChapterNameOverrideDao
import voice.core.data.repo.internals.dao.ListeningEventDao
import voice.core.data.repo.internals.dao.ListeningSessionDao
import voice.core.data.repo.internals.transaction
import voice.core.data.store.ExcludedBooksStore
import voice.core.logging.api.Logger
import kotlin.coroutines.cancellation.CancellationException

@SingleIn(AppScope::class)
@Inject
internal class BackupRestorer(
  private val ring: SnapshotRing,
  private val bookContentDao: BookContentDao,
  private val bookmarkDao: BookmarkDao,
  private val bookCharacterDao: BookCharacterDao,
  private val chapterDao: ChapterDao,
  private val chapterNameOverrideDao: ChapterNameOverrideDao,
  private val listeningSessionDao: ListeningSessionDao,
  private val listeningEventDao: ListeningEventDao,
  @ExcludedBooksStore private val excludedBooksStore: DataStore<Set<String>>,
  private val appDb: RoomDatabase,
  private val contentRepo: BookContentRepo,
  private val settingsSnapshotter: SettingsSnapshotter,
  private val restoreGate: RestoreGate,
) {

  suspend fun restoreIfNeeded() {
    try {
      val live = bookContentDao.all()
      // Only auto-restore when the database is genuinely empty (a real clear-data / reinstall). A library
      // with all books inactive — e.g. the user removed their folders — is intentionally NOT auto-restored;
      // resurrecting it would fight the removal. Explicit Restore covers recovery from a destructive bug.
      if (live.isNotEmpty()) return
      val candidate = RestoreSelector.select(live.size, ring.readAll()) ?: return
      // The snapshot's hidden set filters the restore; the stores are only written once the
      // apply has succeeded, so a failed restore leaves settings and the hidden set untouched.
      val excluded = excludedBooksStore.data.first() + candidate.hiddenBooks
      val restored = apply(candidate, excluded, live)
      excludedBooksStore.updateData { it + candidate.hiddenBooks }
      settingsSnapshotter.apply(candidate.settings)
      contentRepo.invalidateCache()
      Logger.i("Restored $restored books from snapshot generation ${candidate.sequence}")
    } catch (e: CancellationException) {
      throw e
    } catch (t: Throwable) {
      Logger.e(t, "Snapshot restore failed; library is unaffected")
    }
  }

  /**
   * Same-device restore of an external bundle whose book ids are all still present live — no scan,
   * no re-key. Additive and idempotent; returns the number of restored books. Owns the whole
   * commit: rows first, then the hidden set and settings — the same succeed-then-write ordering
   * as [restoreIfNeeded], stated once so the two paths cannot drift. [OsWipeRestorer] remains the
   * door for dead-URI bundles.
   */
  suspend fun applyDirect(snapshot: LibrarySnapshot): Int {
    val restored = restoreGate.withRestoreActive {
      val excludedIds = excludedBooksStore.data.first() + snapshot.hiddenBooks
      val written = apply(snapshot, excludedIds, bookContentDao.all())
      excludedBooksStore.updateData { it + snapshot.hiddenBooks }
      settingsSnapshotter.apply(snapshot.settings)
      contentRepo.invalidateCache()
      Logger.i("Directly restored $written books from an external bundle (same-device ids)")
      written
    }
    restoreGate.requestFlush()
    return restored
  }

  /** True when every active book in [snapshot] already exists in the live database (same-URI restore). */
  suspend fun canApplyDirect(snapshot: LibrarySnapshot): Boolean {
    val active = snapshot.activeIds()
    if (active.isEmpty()) return false
    val liveIds = bookContentDao.all().mapTo(mutableSetOf()) { it.id.value }
    return liveIds.containsAll(active)
  }

  private suspend fun apply(
    snapshot: LibrarySnapshot,
    excludedIds: Set<String>,
    live: List<BookContent>,
  ): Int {
    val liveById = live.associateBy { it.id.value }
    val books = snapshot.books
      .filter { it.id !in excludedIds }
      .mapNotNull { dto -> dto.toBookContentOrNull()?.let { dto.id to it } }
    val bookmarks = snapshot.bookmarks.filter { it.bookId !in excludedIds }.map { it.toBookmark() }
    val characters = snapshot.characters.filter { it.bookId !in excludedIds }.map { it.toBookCharacter() }
    val overrides = snapshot.chapterNameOverrides.filter { it.bookId !in excludedIds }.map { it.toOverride() }
    val sessions = snapshot.sessions.filter { it.bookId !in excludedIds }.map { it.toListeningSession() }
    val events = snapshot.events.filter { it.bookId !in excludedIds }.map { it.toListeningEvent() }
    // chapters2 carries no bookId, so restore them all (REPLACE). A chapter with no surviving content2 row is
    // simply invisible; re-inserting is what lets a restored book's BookRepository.book() resolve at all.
    val chapters = snapshot.chapters.map { it.toChapter() }
    var written = 0
    appDb.transaction {
      // Natural-key dedup for the autoGenerate-PK tables, seeded INSIDE the transaction: snapshot row
      // ids belong to a different database generation, so inserting by id could collide with (or
      // REPLACE) unrelated live rows. Insert with a fresh id instead, and skip rows already present.
      val seenSessionKeys = listeningSessionDao.all().mapTo(mutableSetOf()) { it.naturalKey() }
      val seenCharacterKeys = bookCharacterDao.all().mapTo(mutableSetOf()) { it.naturalKey() }
      val seenEventKeys = listeningEventDao.all().mapTo(mutableSetOf()) { it.naturalKey() }
      chapters.forEach { chapterDao.insert(it) }
      books.forEach { (id, snap) ->
        val liveRow = liveById[id]
        when {
          // Missing live row, or the snapshot is at least as fresh -> take the snapshot copy.
          liveRow == null || snap.lastPlayedAt >= liveRow.lastPlayedAt -> {
            bookContentDao.insert(snap)
            written++
          }
          // The collapse was only an isActive flip; keep the fresher live progress, just re-activate.
          !liveRow.isActive && snap.isActive -> {
            bookContentDao.insert(liveRow.copy(isActive = true))
            written++
          }
        }
      }
      bookmarks.forEach { bookmarkDao.addBookmark(it) }
      characters.forEach { character ->
        if (seenCharacterKeys.add(character.naturalKey())) bookCharacterDao.insert(character.copy(id = 0))
      }
      overrides.forEach { chapterNameOverrideDao.insert(it) }
      sessions.forEach { session ->
        if (seenSessionKeys.add(session.naturalKey())) listeningSessionDao.insert(session.copy(id = 0))
      }
      events.forEach { event ->
        if (seenEventKeys.add(event.naturalKey())) listeningEventDao.insert(event.copy(id = 0))
      }
    }
    // Books whose live progress was NEWER than the snapshot are deliberately untouched; report
    // only what was actually written so "Restored N books" is never a lie.
    return written
  }
}
