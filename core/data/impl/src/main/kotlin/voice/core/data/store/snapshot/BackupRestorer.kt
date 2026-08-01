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
  @SnapshotSlot0Store slot0: DataStore<LibrarySnapshot?>,
  @SnapshotSlot1Store slot1: DataStore<LibrarySnapshot?>,
  @SnapshotSlot2Store slot2: DataStore<LibrarySnapshot?>,
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

  private val ring = SnapshotRing(listOf(slot0, slot1, slot2))

  suspend fun restoreIfNeeded() {
    try {
      val live = bookContentDao.all()
      // Only auto-restore when the database is genuinely empty (a real clear-data / reinstall). A library
      // with all books inactive — e.g. the user removed their folders — is intentionally NOT auto-restored;
      // resurrecting it would fight the removal. Explicit Restore covers recovery from a destructive bug.
      if (live.isNotEmpty()) return
      val candidate = RestoreSelector.select(live.size, ring.readAll()) ?: return
      // A wiped device also lost its DataStore: bring the hidden set and settings back first so the
      // restored books respect them.
      excludedBooksStore.updateData { it + candidate.hiddenBooks }
      settingsSnapshotter.apply(candidate.settings)
      val restored = apply(candidate, excludedBooksStore.data.first(), live)
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
   * no re-key. Additive and idempotent; returns the number of restored books. Used by the explicit
   * Restore action's fast path; [OsWipeRestorer] remains the door for dead-URI bundles.
   */
  suspend fun applyDirect(snapshot: LibrarySnapshot): Int = restoreGate.withRestoreActive {
    val excludedIds = excludedBooksStore.data.first()
    val restored = apply(snapshot, excludedIds, bookContentDao.all())
    contentRepo.invalidateCache()
    Logger.i("Directly restored $restored books from an external bundle (same-device ids)")
    restored
  }.also { restoreGate.requestFlush() }

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
          liveRow == null || snap.lastPlayedAt >= liveRow.lastPlayedAt -> bookContentDao.insert(snap)
          // The collapse was only an isActive flip; keep the fresher live progress, just re-activate.
          !liveRow.isActive && snap.isActive -> bookContentDao.insert(liveRow.copy(isActive = true))
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
    return books.size
  }
}
