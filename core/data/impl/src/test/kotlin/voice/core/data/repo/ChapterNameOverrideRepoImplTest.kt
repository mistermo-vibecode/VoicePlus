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
import voice.core.data.repo.internals.AppDb

@RunWith(RobolectricTestRunner::class)
class ChapterNameOverrideRepoImplTest {

  private lateinit var db: AppDb
  private lateinit var repo: ChapterNameOverrideRepoImpl

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java)
      .allowMainThreadQueries()
      .build()
    repo = ChapterNameOverrideRepoImpl(db.chapterNameOverrideDao())
  }

  @After
  fun teardown() {
    db.close()
  }

  @Test
  fun `set and observe override`() = runTest {
    val bookId = BookId("content://books/1")
    val chapterId = ChapterId("content://chapters/1")
    repo.overridesForBook(bookId).test {
      assertTrue(awaitItem().isEmpty())
      repo.set(chapterId, 0L, bookId, "Intro")
      assertEquals("Intro", awaitItem().first().name)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `set replaces existing override for same key`() = runTest {
    val bookId = BookId("content://books/1")
    val chapterId = ChapterId("content://chapters/1")
    repo.set(chapterId, 0L, bookId, "Old Name")
    repo.set(chapterId, 0L, bookId, "New Name")
    repo.overridesForBook(bookId).test {
      val items = awaitItem()
      assertEquals(1, items.size)
      assertEquals("New Name", items.first().name)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `delete removes single override`() = runTest {
    val bookId = BookId("content://books/1")
    val chapterId = ChapterId("content://chapters/1")
    repo.set(chapterId, 0L, bookId, "Intro")
    repo.delete(chapterId, 0L)
    repo.overridesForBook(bookId).test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `deleteAll removes all overrides for book`() = runTest {
    val bookId = BookId("content://books/1")
    val chapterId1 = ChapterId("content://chapters/1")
    val chapterId2 = ChapterId("content://chapters/2")
    repo.set(chapterId1, 0L, bookId, "Intro")
    repo.set(chapterId2, 1000L, bookId, "Prologue")
    repo.deleteAll(bookId)
    repo.overridesForBook(bookId).test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `overrides for different books are independent`() = runTest {
    val bookId1 = BookId("content://books/1")
    val bookId2 = BookId("content://books/2")
    val chapterId = ChapterId("content://chapters/1")
    repo.set(chapterId, 0L, bookId1, "Book1 Intro")
    repo.overridesForBook(bookId2).test {
      assertTrue(awaitItem().isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }
}
