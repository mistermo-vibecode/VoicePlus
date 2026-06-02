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
  private val noopBackup = object : BackupRepository {
    override val backupFolder = kotlinx.coroutines.flow.flowOf<android.net.Uri?>(null)
    override val lastBackupAt = kotlinx.coroutines.flow.flowOf<java.time.Instant?>(null)
    override suspend fun setBackupFolder(uri: android.net.Uri) {}
    override suspend fun clearBackupFolder() {}
    override suspend fun exportNow() {}
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
    chapterNameOverrideDao = db.chapterNameOverrideDao(),
    slot0 = slot0,
    slot1 = slot1,
    slot2 = slot2,
    excludedBooksStore = excluded,
    backupRepository = noopBackup,
  )

  private fun book(id: String, active: Boolean) = BookContent(
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
}
