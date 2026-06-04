package voice.core.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ListeningEvent
import voice.core.data.ListeningEventType
import voice.core.data.repo.internals.AppDb
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ListeningEventDaoTest {

  private lateinit var db: AppDb

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun teardown() {
    db.close()
  }

  @Test
  fun `eventsForBook returns only that book's events ordered by at DESC`() = runTest {
    val bookId1 = BookId("content://books/1")
    val bookId2 = BookId("content://books/2")
    val chapterId = ChapterId("content://chapters/1")

    val t1 = Instant.ofEpochMilli(1000)
    val t2 = Instant.ofEpochMilli(2000)
    val t3 = Instant.ofEpochMilli(3000)

    val dao = db.listeningEventDao()

    dao.insert(
      ListeningEvent(
        bookId = bookId1,
        type = ListeningEventType.Back.id,
        chapterId = chapterId,
        positionMs = 100,
        at = t1,
      ),
    )
    dao.insert(
      ListeningEvent(
        bookId = bookId1,
        type = ListeningEventType.Forward.id,
        chapterId = chapterId,
        positionMs = 200,
        at = t3,
      ),
    )
    dao.insert(
      ListeningEvent(
        bookId = bookId2,
        type = ListeningEventType.Next.id,
        chapterId = chapterId,
        positionMs = 999,
        at = t2,
      ),
    )

    dao.eventsForBook(bookId1).test {
      val items = awaitItem()
      assertEquals(2, items.size)
      // DESC order: t3 first, t1 second
      assertEquals(t3, items[0].at)
      assertEquals(t1, items[1].at)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `eventsForBook does not return events for other books`() = runTest {
    val bookId1 = BookId("content://books/1")
    val bookId2 = BookId("content://books/2")
    val chapterId = ChapterId("content://chapters/1")

    val dao = db.listeningEventDao()
    dao.insert(
      ListeningEvent(
        bookId = bookId1,
        type = ListeningEventType.SetPosition.id,
        chapterId = chapterId,
        positionMs = 500,
        at = Instant.ofEpochMilli(1000),
      ),
    )

    dao.eventsForBook(bookId2).test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `deleteAllForBook clears only that book`() = runTest {
    val bookId1 = BookId("content://books/1")
    val bookId2 = BookId("content://books/2")
    val chapterId = ChapterId("content://chapters/1")

    val dao = db.listeningEventDao()
    dao.insert(
      ListeningEvent(
        bookId = bookId1,
        type = ListeningEventType.Back.id,
        chapterId = chapterId,
        positionMs = 100,
        at = Instant.ofEpochMilli(1000),
      ),
    )
    dao.insert(
      ListeningEvent(
        bookId = bookId1,
        type = ListeningEventType.Forward.id,
        chapterId = chapterId,
        positionMs = 200,
        at = Instant.ofEpochMilli(2000),
      ),
    )
    dao.insert(
      ListeningEvent(
        bookId = bookId2,
        type = ListeningEventType.AutoAdvance.id,
        chapterId = chapterId,
        positionMs = 300,
        at = Instant.ofEpochMilli(3000),
      ),
    )

    dao.deleteAllForBook(bookId1)

    dao.eventsForBook(bookId1).test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }

    dao.eventsForBook(bookId2).test {
      assertEquals(1, awaitItem().size)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `fromPositionMs is nullable and persists correctly`() = runTest {
    val bookId = BookId("content://books/1")
    val chapterId = ChapterId("content://chapters/1")
    val dao = db.listeningEventDao()

    dao.insert(
      ListeningEvent(
        bookId = bookId,
        type = ListeningEventType.SetPosition.id,
        chapterId = chapterId,
        positionMs = 500,
        fromPositionMs = 250,
        at = Instant.ofEpochMilli(1000),
      ),
    )
    dao.insert(
      ListeningEvent(
        bookId = bookId,
        type = ListeningEventType.Back.id,
        chapterId = chapterId,
        positionMs = 100,
        fromPositionMs = null,
        at = Instant.ofEpochMilli(2000),
      ),
    )

    dao.eventsForBook(bookId).test {
      val items = awaitItem()
      assertEquals(2, items.size)
      // DESC: t2000 first
      assertEquals(null, items[0].fromPositionMs)
      assertEquals(250L, items[1].fromPositionMs)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
