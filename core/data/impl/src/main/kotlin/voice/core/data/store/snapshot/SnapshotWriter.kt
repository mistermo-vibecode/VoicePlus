package voice.core.data.store.snapshot

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import voice.core.data.BookContent
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.internals.dao.BookCharacterDao
import voice.core.data.repo.internals.dao.BookmarkDao
import voice.core.data.repo.internals.dao.ChapterNameOverrideDao
import voice.core.data.store.ExcludedBooksStore
import voice.core.logging.api.Logger
import kotlin.time.Duration.Companion.seconds

@SingleIn(AppScope::class)
@Inject
internal class SnapshotWriter(
  private val contentRepo: BookContentRepo,
  private val bookmarkDao: BookmarkDao,
  private val bookCharacterDao: BookCharacterDao,
  private val chapterNameOverrideDao: ChapterNameOverrideDao,
  @SnapshotSlot0Store slot0: DataStore<LibrarySnapshot?>,
  @SnapshotSlot1Store slot1: DataStore<LibrarySnapshot?>,
  @SnapshotSlot2Store slot2: DataStore<LibrarySnapshot?>,
  @ExcludedBooksStore private val excludedBooksStore: DataStore<Set<String>>,
) {

  private val ring = SnapshotRing(listOf(slot0, slot1, slot2))

  fun start(scope: CoroutineScope) {
    contentRepo.flow()
      .debounce(DEBOUNCE)
      .onEach { books -> writeSnapshot(books) }
      .launchIn(scope)
  }

  internal suspend fun writeSnapshot(books: List<BookContent>) {
    runCatching {
      val excludedIds = excludedBooksStore.data.first()
      val snapshot = LibrarySnapshot(
        schemaVersion = LibrarySnapshot.SCHEMA_VERSION,
        sequence = 0L, // assigned by the ring
        savedAtEpochMillis = System.currentTimeMillis(),
        totalCount = books.size,
        activeCount = books.count { it.isActive },
        books = books.map { it.toDto() },
        bookmarks = bookmarkDao.all().map { it.toDto() },
        characters = bookCharacterDao.all().map { it.toDto() },
        chapterNameOverrides = chapterNameOverrideDao.all().map { it.toDto() },
      )
      if (RotationGuard.isSuspiciousShrink(ring.best(), snapshot, excludedIds)) {
        Logger.w(
          "Snapshot rotation declined: suspicious active shrink " +
            "(incoming=${snapshot.activeCount}, totalKnown=${snapshot.totalCount})",
        )
        return
      }
      ring.writeNext(snapshot)
    }.onFailure { Logger.w(it, "Snapshot write failed; library is unaffected") }
  }

  companion object {
    private val DEBOUNCE = 3.seconds
  }
}
