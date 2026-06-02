package voice.core.data.store.snapshot

import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import voice.core.data.BookContent
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.dao.BookCharacterDao
import voice.core.data.repo.internals.dao.BookmarkDao
import voice.core.data.repo.internals.dao.ChapterDao
import voice.core.data.repo.internals.dao.ChapterNameOverrideDao
import voice.core.data.repo.internals.dao.ListeningSessionDao
import voice.core.data.store.ExcludedBooksStore
import voice.core.data.store.snapshot.identity.DeviceRelativePath
import voice.core.data.store.snapshot.identity.IdentityStampBuilder
import voice.core.logging.api.Logger
import kotlin.time.Duration.Companion.seconds

@SingleIn(AppScope::class)
@Inject
internal class SnapshotWriter(
  private val contentRepo: BookContentRepo,
  private val bookmarkDao: BookmarkDao,
  private val bookCharacterDao: BookCharacterDao,
  private val chapterDao: ChapterDao,
  private val chapterNameOverrideDao: ChapterNameOverrideDao,
  private val listeningSessionDao: ListeningSessionDao,
  @SnapshotSlot0Store slot0: DataStore<LibrarySnapshot?>,
  @SnapshotSlot1Store slot1: DataStore<LibrarySnapshot?>,
  @SnapshotSlot2Store slot2: DataStore<LibrarySnapshot?>,
  @ExcludedBooksStore private val excludedBooksStore: DataStore<Set<String>>,
  private val backupRepository: BackupRepository,
  private val restoreGate: RestoreGate,
) {

  private val ring = SnapshotRing(listOf(slot0, slot1, slot2))

  fun start(scope: CoroutineScope) {
    // Re-snapshot on any change to books OR user-authored data (bookmarks, character notes, chapter-name
    // overrides, listening sessions). This keeps the on-device ring AND the external bundle current, so a
    // user deletion is reflected promptly and never silently resurrected by a later restore reading a stale
    // bundle. The count()/allSessions() flows fire on any insert/update/delete to their table.
    merge(
      contentRepo.flow().map { },
      bookmarkDao.count().map { },
      bookCharacterDao.count().map { },
      chapterNameOverrideDao.count().map { },
      listeningSessionDao.allSessions().map { },
    )
      .debounce(DEBOUNCE)
      .onEach { writeSnapshot(contentRepo.all()) }
      .launchIn(scope)
  }

  internal suspend fun writeSnapshot(books: List<BookContent>) {
    withContext(Dispatchers.IO) {
      runCatching {
        // An OS-wipe restore is mid-flight: the live library is the freshly-scanned, not-yet-re-keyed set
        // (active books, no user data). Writing it now would clobber the ring/bundle we are restoring from.
        if (restoreGate.active.value) return@withContext
        val excludedIds = excludedBooksStore.data.first()
        val allChapters = chapterDao.all()
        val chapterById = allChapters.associateBy { it.id.value }
        // Each book's re-grant-invariant identity stamp, and the volume-relative documentId of the book each
        // chapter belongs to, are derived purely from the stored URIs (see IdentityStampBuilder).
        val bookDtos = books.map { book ->
          val bookChapters = book.chapters.mapNotNull { chapterById[it.value] }
          book.toDto().copy(identity = IdentityStampBuilder.build(book, bookChapters))
        }
        val bookRelPathByChapterId: Map<String, String> = buildMap {
          books.forEach { book ->
            val relPath = DeviceRelativePath.documentId(book.id.value.toUri())
            book.chapters.forEach { put(it.value, relPath) }
          }
        }
        val chapterDtos = allChapters.map { chapter ->
          val relName = bookRelPathByChapterId[chapter.id.value]
            ?.let { DeviceRelativePath.relName(chapter.id.value.toUri(), it) }
            .orEmpty()
          chapter.toDto(relName = relName)
        }
        val snapshot = LibrarySnapshot(
          schemaVersion = LibrarySnapshot.SCHEMA_VERSION,
          dbVersion = AppDb.VERSION,
          sequence = 0L, // assigned by the ring
          savedAtEpochMillis = System.currentTimeMillis(),
          totalCount = books.size,
          activeCount = books.count { it.isActive },
          books = bookDtos,
          bookmarks = bookmarkDao.all().map { it.toDto() },
          characters = bookCharacterDao.all().map { it.toDto() },
          chapterNameOverrides = chapterNameOverrideDao.all().map { it.toDto() },
          sessions = listeningSessionDao.all().map { it.toDto() },
          chapters = chapterDtos,
        )
        if (RotationGuard.isSuspiciousShrink(ring.best(), snapshot, excludedIds)) {
          Logger.w(
            "Snapshot rotation declined: suspicious active shrink " +
              "(incoming=${snapshot.activeCount}, totalKnown=${snapshot.totalCount})",
          )
          return@withContext
        }
        ring.writeNext(snapshot)
        backupRepository.exportNow()
      }.onFailure { Logger.w(it, "Snapshot write failed; library is unaffected") }
    }
  }

  companion object {
    private val DEBOUNCE = 3.seconds
  }
}
