package voice.core.data.store.snapshot

import androidx.datastore.core.DataStore
import androidx.room.RoomDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.internals.dao.BookCharacterDao
import voice.core.data.repo.internals.dao.BookContentDao
import voice.core.data.repo.internals.dao.BookmarkDao
import voice.core.data.repo.internals.dao.ChapterNameOverrideDao
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
  private val chapterNameOverrideDao: ChapterNameOverrideDao,
  @ExcludedBooksStore private val excludedBooksStore: DataStore<Set<String>>,
  private val appDb: RoomDatabase,
  private val contentRepo: BookContentRepo,
) {

  private val ring = SnapshotRing(listOf(slot0, slot1, slot2))

  suspend fun restoreIfNeeded() {
    runCatching {
      val live = bookContentDao.all()
      val liveActiveIds = live.filter { it.isActive }.map { it.id.value }.toSet()
      val excludedIds = excludedBooksStore.data.first()
      val candidate = RestoreSelector.select(live.size, liveActiveIds, excludedIds, ring.readAll()) ?: return
      apply(candidate, excludedIds)
      contentRepo.invalidateCache()
      Logger.i("Restored library from snapshot generation ${candidate.sequence}")
    }.onFailure { Logger.e(it, "Snapshot restore failed; library is unaffected") }
  }

  private suspend fun apply(snapshot: LibrarySnapshot, excludedIds: Set<String>) {
    val books = snapshot.books.filter { it.id !in excludedIds }.mapNotNull { it.toBookContentOrNull() }
    val bookmarks = snapshot.bookmarks.filter { it.bookId !in excludedIds }.map { it.toBookmark() }
    val characters = snapshot.characters.filter { it.bookId !in excludedIds }.map { it.toBookCharacter() }
    val overrides = snapshot.chapterNameOverrides.filter { it.bookId !in excludedIds }.map { it.toOverride() }
    appDb.transaction {
      books.forEach { bookContentDao.insert(it) }
      bookmarks.forEach { bookmarkDao.addBookmark(it) }
      characters.forEach { bookCharacterDao.insert(it) }
      overrides.forEach { chapterNameOverrideDao.insert(it) }
    }
  }
}
