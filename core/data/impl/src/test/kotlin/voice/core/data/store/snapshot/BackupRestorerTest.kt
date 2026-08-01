package voice.core.data.store.snapshot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
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
import voice.core.data.ListeningSession
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
  private val ring = SnapshotRing(listOf(slot0, slot1, slot2))

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build()
    contentRepo = BookContentRepoImpl(db.bookContentDao())
  }

  @After
  fun teardown() = db.close()

  private fun restorer() = BackupRestorer(
    ring = ring,
    bookContentDao = db.bookContentDao(),
    bookmarkDao = db.bookmarkDao(),
    bookCharacterDao = db.bookCharacterDao(),
    chapterDao = db.chapterDao(),
    chapterNameOverrideDao = db.chapterNameOverrideDao(),
    listeningSessionDao = db.listeningSessionDao(),
    listeningEventDao = db.listeningEventDao(),
    excludedBooksStore = excluded,
    appDb = db,
    contentRepo = contentRepo,
    settingsSnapshotter = testSettingsSnapshotter(),
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

  @Test
  fun `all-inactive non-empty library is not resurrected so an intentional removal sticks`() = runTest {
    // The user removed the folder, leaving the book inactive in a non-empty DB. Auto-restore must NOT bring
    // it back — that was the bug where removed books reappeared on the next launch.
    contentRepo.put(book("a", active = false).copy(positionInChapter = 5000))
    slot0.updateData { snapshotOf("a") }

    restorer().restoreIfNeeded()

    val row = db.bookContentDao().all().single { it.id.value == "a" }
    row.isActive shouldBe false
    row.positionInChapter shouldBe 5000L
  }

  @Test
  fun `restores chapters2 so a restored book is resolvable`() = runTest {
    slot0.updateData {
      LibrarySnapshot(
        schemaVersion = 1, dbVersion = AppDb.VERSION, sequence = 1, savedAtEpochMillis = 0,
        totalCount = 1, activeCount = 1,
        books = listOf(book("a", active = true).toDto()),
        bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
        chapters = listOf(Chapter(ChapterId("ca"), "Ch A", 1_000, Instant.EPOCH, emptyList()).toDto()),
      )
    }
    restorer().restoreIfNeeded()
    db.chapterDao().all().map { it.id.value } shouldContainExactlyInAnyOrder listOf("ca")
  }

  @Test
  fun `restores listening sessions when room is empty`() = runTest {
    slot0.updateData {
      LibrarySnapshot(
        schemaVersion = 1, dbVersion = AppDb.VERSION, sequence = 1, savedAtEpochMillis = 0,
        totalCount = 1, activeCount = 1,
        books = listOf(book("a", active = true).toDto()),
        bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
        sessions = listOf(
          ListeningSession(
            id = 7,
            bookId = BookId("a"),
            chapterId = ChapterId("ca"),
            startedAt = Instant.ofEpochMilli(100),
            endedAt = Instant.ofEpochMilli(200),
            durationMs = 100,
            startPositionMs = 0,
            endPositionMs = 100,
          ).toDto(),
        ),
      )
    }
    restorer().restoreIfNeeded()
    // Restored with a FRESH id — snapshot ids belong to a different database generation.
    val restored = db.listeningSessionDao().all().single()
    restored.startedAt shouldBe Instant.ofEpochMilli(100)
    restored.bookId.value shouldBe "a"
  }

  @Test
  fun `applyDirect twice never duplicates sessions, characters or events`() = runTest {
    contentRepo.put(book("a", active = true))
    val snapshot = snapshotOf("a").copy(
      sessions = listOf(
        ListeningSession(
          id = 7, bookId = BookId("a"), chapterId = ChapterId("ca"),
          startedAt = Instant.ofEpochMilli(100), endedAt = Instant.ofEpochMilli(200),
          durationMs = 100, startPositionMs = 0, endPositionMs = 100, endReason = 1,
        ).toDto(),
      ),
      characters = listOf(
        BookCharacterDto(
          id = 3,
          bookId = "a",
          name = "Paul",
          description = "Atreides",
          createdAtEpochMillis = 5,
          updatedAtEpochMillis = 5,
        ),
      ),
      events = listOf(
        ListeningEventDto(bookId = "a", type = 0, chapterId = "ca", positionMs = 50, atEpochMillis = 150),
      ),
    )

    val r = restorer()
    r.applyDirect(snapshot) shouldBe 1
    r.applyDirect(snapshot) shouldBe 1

    db.listeningSessionDao().all().size shouldBe 1
    db.listeningSessionDao().all().single().endReason shouldBe 1
    db.bookCharacterDao().all().size shouldBe 1
    db.listeningEventDao().all().size shouldBe 1
  }

  @Test
  fun `canApplyDirect is true only when every active snapshot book exists live`() = runTest {
    contentRepo.put(book("a", active = true))
    restorer().canApplyDirect(snapshotOf("a")) shouldBe true
    restorer().canApplyDirect(snapshotOf("a", "b")) shouldBe false
    restorer().canApplyDirect(snapshotOf()) shouldBe false
  }

  @Test
  fun `auto-restore brings back the hidden set and keeps hidden books out`() = runTest {
    slot0.updateData { snapshotOf("a", "b").copy(hiddenBooks = setOf("b")) }
    restorer().restoreIfNeeded()
    excluded.data.first() shouldBe setOf("b")
    db.bookContentDao().all().map { it.id.value } shouldContainExactlyInAnyOrder listOf("a")
  }

  @Test
  fun `applyDirect reports only what it actually wrote`() = runTest {
    // Live book is FRESHER than the snapshot: the freshness guard keeps it, so nothing is written.
    contentRepo.put(book("a", active = true).copy(lastPlayedAt = Instant.ofEpochMilli(9_999)))
    val snapshot = snapshotOf("a") // snapshot books carry lastPlayedAt = EPOCH

    restorer().applyDirect(snapshot) shouldBe 0
  }
}
