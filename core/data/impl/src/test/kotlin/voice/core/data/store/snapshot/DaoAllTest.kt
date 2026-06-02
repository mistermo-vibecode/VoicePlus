package voice.core.data.store.snapshot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookCharacter
import voice.core.data.BookId
import voice.core.data.ChapterNameOverride
import voice.core.data.repo.internals.AppDb
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class DaoAllTest {

  private lateinit var db: AppDb
  private val bookId = BookId("book-1")

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun teardown() = db.close()

  @Test
  fun `character and override all() return every row`() = runTest {
    db.bookCharacterDao().insert(
      BookCharacter(
        bookId = bookId,
        name = "Frodo",
        description = "",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
      ),
    )
    db.chapterNameOverrideDao().insert(
      ChapterNameOverride(chapterId = "c1", markStartMs = 0L, bookId = bookId.value, name = "Intro"),
    )

    db.bookCharacterDao().all() shouldHaveSize 1
    db.chapterNameOverrideDao().all() shouldHaveSize 1
  }
}
