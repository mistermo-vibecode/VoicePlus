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
import voice.core.data.repo.internals.dao.ListeningSessionDao
import voice.core.data.repo.internals.transaction
import voice.core.data.store.ExcludedBooksStore
import voice.core.logging.api.Logger

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
  @ExcludedBooksStore private val excludedBooksStore: DataStore<Set<String>>,
  private val appDb: RoomDatabase,
  private val contentRepo: BookContentRepo,
) {

  private val ring = SnapshotRing(listOf(slot0, slot1, slot2))

  suspend fun restoreIfNeeded() {
    runCatching {
      val live = bookContentDao.all()
      val liveActiveIds = live.filter { it.isActive }.map { it.id.value }.toSet()
      // Healthy library: nothing to restore. Skip the (expensive) snapshot JSON decodes.
      if (live.isNotEmpty() && liveActiveIds.isNotEmpty()) return
      val excludedIds = excludedBooksStore.data.first()
      val candidate = RestoreSelector.select(live.size, liveActiveIds, excludedIds, ring.readAll()) ?: return
      apply(candidate, excludedIds, live)
      contentRepo.invalidateCache()
      Logger.i("Restored library from snapshot generation ${candidate.sequence}")
    }.onFailure { Logger.e(it, "Snapshot restore failed; library is unaffected") }
  }

  private suspend fun apply(
    snapshot: LibrarySnapshot,
    excludedIds: Set<String>,
    live: List<BookContent>,
  ) {
    val liveById = live.associateBy { it.id.value }
    val books = snapshot.books
      .filter { it.id !in excludedIds }
      .mapNotNull { dto -> dto.toBookContentOrNull()?.let { dto.id to it } }
    val bookmarks = snapshot.bookmarks.filter { it.bookId !in excludedIds }.map { it.toBookmark() }
    val characters = snapshot.characters.filter { it.bookId !in excludedIds }.map { it.toBookCharacter() }
    val overrides = snapshot.chapterNameOverrides.filter { it.bookId !in excludedIds }.map { it.toOverride() }
    val sessions = snapshot.sessions.filter { it.bookId !in excludedIds }.map { it.toListeningSession() }
    // chapters2 carries no bookId, so restore them all (REPLACE). A chapter with no surviving content2 row is
    // simply invisible; re-inserting is what lets a restored book's BookRepository.book() resolve at all.
    val chapters = snapshot.chapters.map { it.toChapter() }
    appDb.transaction {
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
      characters.forEach { bookCharacterDao.insert(it) }
      overrides.forEach { chapterNameOverrideDao.insert(it) }
      sessions.forEach { listeningSessionDao.upsert(it) }
    }
  }
}
