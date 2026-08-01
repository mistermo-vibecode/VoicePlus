package voice.core.data.store.snapshot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookCharacter
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Bookmark
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride
import voice.core.data.ListeningSession
import voice.core.data.repo.BookContentRepoImpl
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.MemoryDataStore
import java.time.Instant

/**
 * End-to-end regression for the data-loss bug: a user-data wipe (sessions / bookmarks / characters /
 * chapter-name-overrides deleted) while the books survive must NOT propagate empty state through the ring.
 *
 * These tests drive the REAL SnapshotWriter + REAL SnapshotRing against a real in-memory Room DB (no mocked
 * surface). They reproduce the on-device evidence: 21 books survive, every user-authored row is gone, the
 * writer fires on the change and clobbers every retained snapshot. The good generation must survive.
 */
@RunWith(RobolectricTestRunner::class)
class SnapshotUserDataLossTest {

  private lateinit var db: AppDb
  private lateinit var contentRepo: BookContentRepoImpl
  private val slot0 = MemoryDataStore<LibrarySnapshot?>(null)
  private val slot1 = MemoryDataStore<LibrarySnapshot?>(null)
  private val slot2 = MemoryDataStore<LibrarySnapshot?>(null)
  private val excluded = MemoryDataStore<Set<String>>(emptySet())
  private val ring = SnapshotRing(listOf(slot0, slot1, slot2))
  private val noopBackup = object : BackupRepository {
    override val backupFolder = kotlinx.coroutines.flow.flowOf<android.net.Uri?>(null)
    override val lastBackupAt = kotlinx.coroutines.flow.flowOf<java.time.Instant?>(null)
    override val lastRestore = kotlinx.coroutines.flow.flowOf<RestoreSummary?>(null)
    override val status = kotlinx.coroutines.flow.flowOf<BackupStatus?>(null)
    override val busy = kotlinx.coroutines.flow.flowOf(false)
    override suspend fun setBackupFolder(uri: android.net.Uri) {}
    override suspend fun clearBackupFolder() {}
    override suspend fun exportNow() = BackupExportResult.SkippedNoFolder
    override suspend fun exportAfterSnapshot() = BackupExportResult.SkippedNoFolder
    override suspend fun importAndRestore() = false
  }

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build()
    contentRepo = BookContentRepoImpl(db.bookContentDao())
  }

  @After
  fun teardown() = db.close()

  private fun writer() = SnapshotWriter(
    contentRepo = contentRepo,
    bookmarkDao = db.bookmarkDao(),
    bookCharacterDao = db.bookCharacterDao(),
    chapterDao = db.chapterDao(),
    chapterNameOverrideDao = db.chapterNameOverrideDao(),
    listeningSessionDao = db.listeningSessionDao(),
    slot0 = slot0,
    slot1 = slot1,
    slot2 = slot2,
    excludedBooksStore = excluded,
    backupRepository = noopBackup,
    restoreGate = RestoreGate(),
  )

  private fun restorer() = BackupRestorer(
    slot0 = slot0, slot1 = slot1, slot2 = slot2,
    bookContentDao = db.bookContentDao(),
    bookmarkDao = db.bookmarkDao(),
    bookCharacterDao = db.bookCharacterDao(),
    chapterDao = db.chapterDao(),
    chapterNameOverrideDao = db.chapterNameOverrideDao(),
    listeningSessionDao = db.listeningSessionDao(),
    excludedBooksStore = excluded,
    appDb = db,
    contentRepo = contentRepo,
  )

  private fun book(
    id: String,
    active: Boolean = true,
  ) = BookContent(
    id = BookId(id), playbackSpeed = 1f, skipSilence = false, isActive = active,
    lastPlayedAt = Instant.EPOCH, author = null, name = id, addedAt = Instant.EPOCH,
    chapters = listOf(ChapterId("c$id")), currentChapter = ChapterId("c$id"), positionInChapter = 0,
    cover = null, gain = 0f, genre = null, narrator = null, series = null, part = null,
  )

  /** Seeds a realistic library: [bookCount] active books, each with a chapter, a session, a bookmark, a character. */
  private suspend fun seedRealisticLibrary(bookCount: Int) {
    repeat(bookCount) { i ->
      val id = "b$i"
      contentRepo.put(book(id, active = true))
      db.chapterDao().insert(Chapter(ChapterId("c$id"), "Ch $id", 1_000, Instant.EPOCH, emptyList()))
      db.listeningSessionDao().insert(
        ListeningSession(
          id = 0,
          bookId = BookId(id),
          chapterId = ChapterId("c$id"),
          startedAt = Instant.ofEpochMilli(100),
          endedAt = Instant.ofEpochMilli(200),
          durationMs = 100,
          startPositionMs = 0,
          endPositionMs = 100,
        ),
      )
      db.bookmarkDao().addBookmark(
        Bookmark(
          bookId = BookId(id),
          chapterId = ChapterId("c$id"),
          title = "bm $id",
          time = 10,
          addedAt = Instant.EPOCH,
          setBySleepTimer = false,
          id = Bookmark.Id.random(),
        ),
      )
      db.bookCharacterDao().insert(
        BookCharacter(
          id = 0,
          bookId = BookId(id),
          name = "char $id",
          description = "desc",
          createdAt = Instant.EPOCH,
          updatedAt = Instant.EPOCH,
        ),
      )
      db.chapterNameOverrideDao().insert(
        ChapterNameOverride(chapterId = "c$id", markStartMs = 0, bookId = id, name = "ovr $id"),
      )
    }
  }

  /** The on-device wipe: every user-authored row deleted, all books left intact and active. */
  private suspend fun wipeUserDataKeepBooks() {
    contentRepo.all().forEach { db.listeningSessionDao().deleteAllForBook(it.id) }
    db.bookmarkDao().all().forEach { db.bookmarkDao().deleteBookmark(it.id) }
    db.bookCharacterDao().all().forEach { db.bookCharacterDao().delete(it.id) }
    contentRepo.all().forEach { db.chapterNameOverrideDao().deleteAll(it.id.value) }
  }

  @Test
  fun `a user-data wipe with books intact does not overwrite the data-rich ring`() = runTest {
    val w = writer()
    seedRealisticLibrary(bookCount = 21)
    w.writeSnapshot(contentRepo.all())

    val good = ring.best().shouldNotBeNull()
    good.sessions.shouldNotBeEmpty()
    good.bookmarks.shouldNotBeEmpty()
    good.characters.shouldNotBeEmpty()
    good.chapterNameOverrides.shouldNotBeEmpty()
    val goodSeq = good.sequence

    // The wipe: books remain, all user-authored data gone.
    wipeUserDataKeepBooks()
    w.writeSnapshot(contentRepo.all())

    // The good generation must survive: best() still points at the data-rich snapshot.
    val afterWipe = ring.best().shouldNotBeNull()
    afterWipe.sequence shouldBe goodSeq
    afterWipe.sessions.shouldNotBeEmpty()
    afterWipe.bookmarks.shouldNotBeEmpty()
    afterWipe.characters.shouldNotBeEmpty()
    afterWipe.chapterNameOverrides.shouldNotBeEmpty()
  }

  @Test
  fun `repeated empty writes never cycle the data-rich snapshot out of the 3-slot ring`() = runTest {
    val w = writer()
    seedRealisticLibrary(bookCount = 21)
    w.writeSnapshot(contentRepo.all())

    wipeUserDataKeepBooks()
    // More empty writes than ring slots: with the bug all 3 slots would be empty.
    repeat(5) { w.writeSnapshot(contentRepo.all()) }

    val best = ring.best().shouldNotBeNull()
    best.sessions.size shouldBeGreaterThan 0
    best.bookmarks.size shouldBeGreaterThan 0
    best.characters.size shouldBeGreaterThan 0
  }

  @Test
  fun `data retained through a vetoed wipe is recoverable by restore`() = runTest {
    val w = writer()
    seedRealisticLibrary(bookCount = 5)
    w.writeSnapshot(contentRepo.all()) // good generation captured in the ring

    wipeUserDataKeepBooks()
    w.writeSnapshot(contentRepo.all()) // vetoed -> ring keeps the good generation

    // Simulate a fresh install / lost database (the on-device snapshot ring survives). Restore must bring
    // the user's history back from the retained good generation — proving the guard's protection is usable
    // end to end, not merely that a good snapshot sits unused in a slot.
    db.clearAllTables()
    restorer().restoreIfNeeded()

    db.listeningSessionDao().all().shouldNotBeEmpty()
    db.bookmarkDao().all().shouldNotBeEmpty()
    db.bookCharacterDao().all().shouldNotBeEmpty()
    db.chapterNameOverrideDao().all().shouldNotBeEmpty()
  }
}
