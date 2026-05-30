package voice.features.playbackScreen.listeninglog

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride
import voice.core.data.ListeningSession
import voice.core.data.MarkData
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.core.data.repo.ListeningSessionRepo
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ListeningLogViewModelTest {

  private val bookId = BookId("content://books/1")
  private val chapterId = ChapterId("content://chapters/1")

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun book(offset: Int): Book {
    val chapter = Chapter(
      id = chapterId,
      name = "Chapter 5",
      duration = 60_000L,
      fileLastModified = Instant.EPOCH,
      markData = listOf(MarkData(startMs = 0L, name = "Chapter 5")),
    )
    return Book(
      content = BookContent(
        id = bookId,
        playbackSpeed = 1F,
        skipSilence = false,
        isActive = true,
        lastPlayedAt = Instant.EPOCH,
        author = null,
        name = "TestBook",
        addedAt = Instant.EPOCH,
        chapters = listOf(chapter.id),
        currentChapter = chapter.id,
        positionInChapter = 0L,
        cover = null,
        gain = 0F,
        genre = null,
        narrator = null,
        series = null,
        part = null,
        chapterNameOffset = offset,
      ),
      chapters = listOf(chapter),
    )
  }

  private fun session(): ListeningSession = ListeningSession(
    id = 1L,
    bookId = bookId,
    chapterId = chapterId,
    startedAt = Instant.EPOCH,
    endedAt = Instant.EPOCH.plusSeconds(60),
    durationMs = 60_000L,
    startPositionMs = 0L,
    endPositionMs = 0L,
    endChapterId = null,
  )

  private fun viewModel(
    book: Book,
    overrides: List<ChapterNameOverride> = emptyList(),
  ): ListeningLogViewModel {
    val sessionRepo = mockk<ListeningSessionRepo>(relaxed = true) {
      every { sessions(bookId) } returns MutableStateFlow(listOf(session()))
    }
    val bookRepo = mockk<BookRepository>(relaxed = true) {
      every { flow(bookId) } returns MutableStateFlow(book)
    }
    val overrideRepo = mockk<ChapterNameOverrideRepo>(relaxed = true) {
      every { overridesForBook(bookId) } returns MutableStateFlow(overrides)
    }
    return ListeningLogViewModel(
      sessionRepo = sessionRepo,
      bookRepo = bookRepo,
      chapterNameOverrideRepo = overrideRepo,
      playerController = mockk(relaxed = true),
      navigator = mockk(relaxed = true),
      bookId = bookId,
    )
  }

  @Test
  fun `offset is applied to chapter names in the log`() = runTest {
    val vm = viewModel(book(offset = 2))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitItem()
      while (state.groups.flatMap { it.entries }.none { it.chapterName == "Chapter 7" }) {
        state = awaitItem()
      }
      val names = state.groups.flatMap { it.entries }.map { it.chapterName }
      assertTrue("expected all entries to be 'Chapter 7' but was $names", names.isNotEmpty() && names.all { it == "Chapter 7" })
    }
  }

  @Test
  fun `override takes precedence in the log`() = runTest {
    val override = ChapterNameOverride(
      chapterId = chapterId.value,
      markStartMs = 0L,
      bookId = bookId.value,
      name = "Finale",
    )
    val vm = viewModel(book(offset = 2), overrides = listOf(override))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitItem()
      while (state.groups.flatMap { it.entries }.none { it.chapterName == "Finale" }) {
        state = awaitItem()
      }
      val names = state.groups.flatMap { it.entries }.map { it.chapterName }
      assertTrue("expected all entries to be 'Finale' but was $names", names.all { it == "Finale" })
    }
  }
}
