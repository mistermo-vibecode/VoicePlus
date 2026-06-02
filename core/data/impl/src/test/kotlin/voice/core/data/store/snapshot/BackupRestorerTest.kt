package voice.core.data.store.snapshot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
class BackupRestorerTest {

  private lateinit var db: AppDb
  private lateinit var contentRepo: BookContentRepoImpl
  private val slot0 = MemoryDataStore<LibrarySnapshot?>(null)
  private val slot1 = MemoryDataStore<LibrarySnapshot?>(null)
  private val slot2 = MemoryDataStore<LibrarySnapshot?>(null)
  private val excluded = MemoryDataStore<Set<String>>(emptySet())

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build()
    contentRepo = BookContentRepoImpl(db.bookContentDao())
  }

  @After
  fun teardown() = db.close()

  private fun restorer() = BackupRestorer(
    slot0 = slot0,
    slot1 = slot1,
    slot2 = slot2,
    bookContentDao = db.bookContentDao(),
    bookmarkDao = db.bookmarkDao(),
    bookCharacterDao = db.bookCharacterDao(),
    chapterNameOverrideDao = db.chapterNameOverrideDao(),
    excludedBooksStore = excluded,
    appDb = db,
    contentRepo = contentRepo,
  )

  private fun book(id: String, active: Boolean) = BookContent(
    id = BookId(id), playbackSpeed = 1f, skipSilence = false, isActive = active,
    lastPlayedAt = Instant.EPOCH, author = null, name = id, addedAt = Instant.EPOCH,
    chapters = listOf(ChapterId("c$id")), currentChapter = ChapterId("c$id"), positionInChapter = 0,
    cover = null, gain = 0f, genre = null, narrator = null, series = null, part = null,
  )

  private fun snapshotOf(vararg ids: String) = LibrarySnapshot(
    schemaVersion = 1, dbVersion = AppDb.VERSION, sequence = 1, savedAtEpochMillis = 0,
    totalCount = ids.size, activeCount = ids.size,
    books = ids.map { book(it, true).toDto() },
    bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
  )

  @Test
  fun `restores content2 when room is empty`() = runTest {
    slot0.updateData { snapshotOf("a", "b") }
    restorer().restoreIfNeeded()
    db.bookContentDao().all().map { it.id.value } shouldContainExactlyInAnyOrder listOf("a", "b")
  }

  @Test
  fun `does not resurrect an excluded book`() = runTest {
    slot0.updateData { snapshotOf("a", "b") }
    excluded.updateData { setOf("b") }
    restorer().restoreIfNeeded()
    db.bookContentDao().all().map { it.id.value } shouldContainExactlyInAnyOrder listOf("a")
  }

  @Test
  fun `healthy library is left untouched`() = runTest {
    contentRepo.put(book("live", active = true))
    slot0.updateData { snapshotOf("a", "b") }
    restorer().restoreIfNeeded()
    db.bookContentDao().all().map { it.id.value } shouldContainExactlyInAnyOrder listOf("live")
  }
}
