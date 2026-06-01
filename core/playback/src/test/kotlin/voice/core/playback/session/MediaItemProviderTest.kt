package voice.core.playback.session

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride
import voice.core.data.MarkData
import voice.core.data.repo.ChapterNameOverrideRepo
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class MediaItemProviderTest {

  private val bookId = BookId("content://books/1")

  private fun chapter(
    id: ChapterId,
    name: String?,
  ): Chapter = Chapter(
    id = id,
    name = name,
    duration = 60_000L,
    fileLastModified = Instant.EPOCH,
    markData = listOf(MarkData(startMs = 0L, name = name ?: "")),
  )

  private fun book(
    chapters: List<Chapter>,
    offset: Int = 0,
  ): Book = Book(
    content = BookContent(
      id = bookId,
      playbackSpeed = 1F,
      skipSilence = false,
      isActive = true,
      lastPlayedAt = Instant.EPOCH,
      author = null,
      name = "TestBook",
      addedAt = Instant.EPOCH,
      chapters = chapters.map { it.id },
      currentChapter = chapters.first().id,
      positionInChapter = 0L,
      cover = null,
      gain = 0F,
      genre = null,
      narrator = null,
      series = null,
      part = null,
      chapterNameOffset = offset,
    ),
    chapters = chapters,
  )

  private fun provider(overrides: List<ChapterNameOverride> = emptyList()): MediaItemProvider {
    val overrideRepo = mockk<ChapterNameOverrideRepo>(relaxed = true) {
      every { overridesForBook(any()) } returns MutableStateFlow(overrides)
    }
    return MediaItemProvider(
      bookRepository = mockk(relaxed = true),
      application = mockk(relaxed = true),
      chapterRepo = mockk(relaxed = true),
      contentRepo = mockk(relaxed = true),
      chapterNameOverrideRepo = overrideRepo,
      imageFileProvider = mockk(relaxed = true),
      currentBookStoreId = mockk(relaxed = true),
    )
  }

  @Test
  fun `offset is applied to chapter media item titles`() = runTest {
    val c1 = chapter(ChapterId("content://chapters/1"), "Chapter 5")
    val c2 = chapter(ChapterId("content://chapters/2"), "Chapter 6")
    val titles = provider().chapters(book(listOf(c1, c2), offset = 2))
      .map { it.mediaMetadata.title.toString() }
    assertEquals(listOf("Chapter 7", "Chapter 8"), titles)
  }

  @Test
  fun `override takes precedence over offset`() = runTest {
    val c1 = chapter(ChapterId("content://chapters/1"), "Chapter 5")
    val override = ChapterNameOverride(
      chapterId = c1.id.value,
      markStartMs = 0L,
      bookId = bookId.value,
      name = "Prologue",
    )
    val titles = provider(overrides = listOf(override))
      .chapters(book(listOf(c1), offset = 2))
      .map { it.mediaMetadata.title.toString() }
    assertEquals(listOf("Prologue"), titles)
  }

  @Test
  fun `without offset or override the raw chapter name is used`() = runTest {
    val c1 = chapter(ChapterId("content://chapters/1"), "Introduction")
    val titles = provider().chapters(book(listOf(c1), offset = 0))
      .map { it.mediaMetadata.title.toString() }
    assertEquals(listOf("Introduction"), titles)
  }

  @Test
  fun `multi-mark single-file book titles from the file name, not the first mark`() = runTest {
    // Regression guard for the lockscreen bug: a single .m4b with embedded marks is ONE media item
    // whose title is shown statically in the notification, so it must be the book/file name, not
    // "Chapter 1" (the first mark) which would then show regardless of the current chapter.
    val multi = Chapter(
      id = ChapterId("content://chapters/multi"),
      name = "Moby Dick",
      duration = 60_000L,
      fileLastModified = Instant.EPOCH,
      markData = listOf(
        MarkData(startMs = 0L, name = "Chapter 1"),
        MarkData(startMs = 30_000L, name = "Chapter 2"),
      ),
    )
    val titles = provider().chapters(book(listOf(multi), offset = 0))
      .map { it.mediaMetadata.title.toString() }
    assertEquals(listOf("Moby Dick"), titles)
  }

  @Test
  fun `blank resolved name falls back to chapter id`() = runTest {
    val blank = Chapter(
      id = ChapterId("content://chapters/blank"),
      name = null,
      duration = 60_000L,
      fileLastModified = Instant.EPOCH,
      markData = emptyList(),
    )
    val titles = provider().chapters(book(listOf(blank), offset = 0))
      .map { it.mediaMetadata.title.toString() }
    assertEquals(listOf("content://chapters/blank"), titles)
  }
}
