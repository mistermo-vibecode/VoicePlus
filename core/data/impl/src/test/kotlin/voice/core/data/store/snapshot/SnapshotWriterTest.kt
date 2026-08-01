package voice.core.data.store.snapshot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookContentRepoImpl
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.MemoryDataStore
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class SnapshotWriterTest {

  private lateinit var db: AppDb
  private lateinit var contentRepo: BookContentRepoImpl
  private val slot0 = MemoryDataStore<LibrarySnapshot?>(null)
  private val slot1 = MemoryDataStore<LibrarySnapshot?>(null)
  private val slot2 = MemoryDataStore<LibrarySnapshot?>(null)
  private val excluded = MemoryDataStore<Set<String>>(emptySet())
  private val ring = SnapshotRing(listOf(slot0, slot1, slot2))

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build()
    contentRepo = BookContentRepoImpl(db.bookContentDao())
  }

  @After
  fun teardown() = db.close()

  private fun writer(backup: BackupRepository = noopBackupStub) = SnapshotWriter(
    contentRepo = contentRepo,
    bookmarkDao = db.bookmarkDao(),
    bookCharacterDao = db.bookCharacterDao(),
    chapterDao = db.chapterDao(),
    chapterNameOverrideDao = db.chapterNameOverrideDao(),
    listeningSessionDao = db.listeningSessionDao(),
    listeningEventDao = db.listeningEventDao(),
    slot0 = slot0,
    slot1 = slot1,
    slot2 = slot2,
    excludedBooksStore = excluded,
    settingsSnapshotter = testSettingsSnapshotter(),
    audiobookFolders = EmptyAudiobookFolders,
    backupRepository = backup,
    restoreGate = RestoreGate(),
  )

  private fun book(
    id: String,
    active: Boolean,
  ) = BookContent(
    id = BookId(id), playbackSpeed = 1f, skipSilence = false, isActive = active,
    lastPlayedAt = Instant.EPOCH, author = null, name = id, addedAt = Instant.EPOCH,
    chapters = listOf(ChapterId("c$id")), currentChapter = ChapterId("c$id"), positionInChapter = 0,
    cover = null, gain = 0f, genre = null, narrator = null, series = null, part = null,
  )

  @Test
  fun `writes a snapshot of the current library`() = runTest {
    contentRepo.put(book("b1", active = true))
    writer().writeSnapshot(contentRepo.all())

    val written = ring.best()
    written.shouldNotBeNull()
    written.activeIds() shouldBe setOf("b1")
  }

  @Test
  fun `stamps each chapter with its re-keying relName anchor`() = runTest {
    val auth = "com.android.externalstorage.documents"
    fun docUri(documentId: String): String {
      val enc = java.net.URLEncoder.encode(documentId, "UTF-8").replace("+", "%20")
      return "content://$auth/tree/primary%3ABooks/document/$enc"
    }
    val ch1 = docUri("primary:Books/Dune/01.mp3")
    val ch2 = docUri("primary:Books/Dune/Disc2/02.mp3")
    db.chapterDao().insert(Chapter(ChapterId(ch1), "One", 1_000, Instant.EPOCH, emptyList()))
    db.chapterDao().insert(Chapter(ChapterId(ch2), "Two", 1_000, Instant.EPOCH, emptyList()))
    contentRepo.put(
      BookContent(
        id = BookId(docUri("primary:Books/Dune")), playbackSpeed = 1f, skipSilence = false, isActive = true,
        lastPlayedAt = Instant.EPOCH, author = null, name = "Dune", addedAt = Instant.EPOCH,
        chapters = listOf(ChapterId(ch1), ChapterId(ch2)), currentChapter = ChapterId(ch1),
        positionInChapter = 0, cover = null, gain = 0f, genre = null, narrator = null, series = null, part = null,
      ),
    )

    writer().writeSnapshot(contentRepo.all())

    // No identity stamp is stored (v3 derives it on restore); the chapter relNames are the anchors.
    val written = ring.best().shouldNotBeNull()
    written.chapters.map { it.relName }.toSet() shouldBe setOf("01.mp3", "Disc2/02.mp3")
  }

  @Test
  fun `declines to record a suspicious mass deactivation`() = runTest {
    val w = writer()
    contentRepo.put(book("b1", true))
    contentRepo.put(book("b2", true))
    w.writeSnapshot(contentRepo.all())
    val good = ring.best()!!.sequence

    contentRepo.setAllInactiveExcept(emptyList())
    w.writeSnapshot(contentRepo.all())

    ring.best()!!.sequence shouldBe good
  }

  @Test
  fun `automatic external export runs at most once per UTC day`() = runTest {
    val exportedToday = RecordingBackup(lastBackup = Instant.now())
    contentRepo.put(book("b1", active = true))
    writer(backup = exportedToday).writeSnapshot(contentRepo.all())
    exportedToday.exportCalls shouldBe 0

    val exportedYesterday = RecordingBackup(lastBackup = Instant.now().minusSeconds(24 * 60 * 60 + 60))
    writer(backup = exportedYesterday).writeSnapshot(contentRepo.all())
    exportedYesterday.exportCalls shouldBe 1

    val neverExported = RecordingBackup(lastBackup = null)
    writer(backup = neverExported).writeSnapshot(contentRepo.all())
    neverExported.exportCalls shouldBe 1
  }

  @Test
  fun `a forced flush exports regardless of the daily gate`() = runTest {
    val exportedToday = RecordingBackup(lastBackup = Instant.now())
    contentRepo.put(book("b1", active = true))
    writer(backup = exportedToday).writeSnapshot(contentRepo.all(), forceExternalBackup = true)
    exportedToday.exportCalls shouldBe 1
  }
}

private val noopBackupStub = object : BackupRepository {
  override val backupFolder = kotlinx.coroutines.flow.flowOf<android.net.Uri?>(null)
  override val lastBackupAt = kotlinx.coroutines.flow.flowOf<Instant?>(null)
  override val lastRestore = kotlinx.coroutines.flow.flowOf<RestoreSummary?>(null)
  override val status = kotlinx.coroutines.flow.flowOf<BackupStatus?>(null)
  override val busy = kotlinx.coroutines.flow.flowOf(false)
  override suspend fun setBackupFolder(uri: android.net.Uri) {}
  override suspend fun clearBackupFolder() {}
  override suspend fun listBackups() = emptyList<BackupEntry>()
  override suspend fun deleteBackup(entry: BackupEntry) = false
  override suspend fun exportNow() = BackupExportResult.SkippedNoFolder
  override suspend fun exportAfterSnapshot() = BackupExportResult.SkippedNoFolder
  override suspend fun importAndRestore(entry: BackupEntry?) = false
}

private class RecordingBackup(lastBackup: Instant?) : BackupRepository by noopBackupStub {
  var exportCalls = 0
  override val lastBackupAt = kotlinx.coroutines.flow.flowOf(lastBackup)
  override suspend fun exportAfterSnapshot(): BackupExportResult {
    exportCalls++
    return BackupExportResult.Written
  }
}
