package voice.features.bookmark

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Bookmark
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride
import voice.core.data.MarkData
import voice.core.data.repo.BookRepository
import voice.core.data.repo.BookmarkRepo
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.navigation.Navigator
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class BookmarkViewModelTest {

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

  private fun bookmark(title: String?): Bookmark = Bookmark(
    bookId = bookId,
    chapterId = chapterId,
    title = title,
    time = 0L,
    addedAt = Instant.EPOCH,
    setBySleepTimer = false,
    id = Bookmark.Id.random(),
  )

  private fun viewModel(
    book: Book,
    bookmarks: List<Bookmark>,
    overrides: List<ChapterNameOverride> = emptyList(),
  ): BookmarkViewModel {
    val repo = mockk<BookRepository>(relaxed = true) {
      coEvery { get(bookId) } returns book
      every { flow(bookId) } returns MutableStateFlow(book)
    }
    val bookmarkRepo = mockk<BookmarkRepo>(relaxed = true) {
      coEvery { bookmarks(book.content) } returns bookmarks
    }
    val overrideRepo = mockk<ChapterNameOverrideRepo>(relaxed = true) {
      every { overridesForBook(bookId) } returns MutableStateFlow(overrides)
    }
    return BookmarkViewModel(
      currentBookStore = mockk(relaxed = true),
      repo = repo,
      bookmarkRepo = bookmarkRepo,
      chapterNameOverrideRepo = overrideRepo,
      playStateManager = mockk(relaxed = true),
      playerController = mockk(relaxed = true),
      navigator = mockk(relaxed = true),
      context = mockk(relaxed = true),
      bookId = bookId,
    )
  }

  @Test
  fun `auto-derived bookmark name reflects offset`() = runTest {
    val vm = viewModel(book(offset = 2), bookmarks = listOf(bookmark(title = null)))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitItem()
      while (state.bookmarks.isEmpty() || state.bookmarks.first().title == "Chapter 5") {
        state = awaitItem()
      }
      assertEquals("Chapter 7", state.bookmarks.first().title)
    }
  }

  @Test
  fun `user-entered title is not overwritten by resolution`() = runTest {
    val vm = viewModel(book(offset = 2), bookmarks = listOf(bookmark(title = "My Note")))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitItem()
      while (state.bookmarks.isEmpty()) state = awaitItem()
      assertEquals("My Note", state.bookmarks.first().title)
    }
  }

  @Test
  fun `override takes precedence for auto-derived bookmark name`() = runTest {
    val override = ChapterNameOverride(
      chapterId = chapterId.value,
      markStartMs = 0L,
      bookId = bookId.value,
      name = "Epilogue",
    )
    val vm = viewModel(book(offset = 2), bookmarks = listOf(bookmark(title = null)), overrides = listOf(override))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitItem()
      while (state.bookmarks.none { it.title == "Epilogue" }) state = awaitItem()
      assertEquals("Epilogue", state.bookmarks.first().title)
    }
  }

  @Test
  fun `bookmark with unknown chapter is omitted instead of crashing`() = runTest {
    val orphan = Bookmark(
      bookId = bookId,
      chapterId = ChapterId("content://chapters/missing"),
      title = "Orphan",
      time = 0L,
      addedAt = Instant.EPOCH,
      setBySleepTimer = false,
      id = Bookmark.Id.random(),
    )
    val vm = viewModel(book(offset = 0), bookmarks = listOf(bookmark(title = "Valid"), orphan))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitItem()
      while (state.bookmarks.none { it.title == "Valid" }) state = awaitItem()
      assertEquals(1, state.bookmarks.size)
      assertEquals("Valid", state.bookmarks.first().title)
    }
  }

  @Test
  fun `titled bookmark shows chapter context in subtitle`() = runTest {
    val vm = viewModel(book(offset = 0), bookmarks = listOf(bookmark(title = "My Note")))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitItem()
      while (state.bookmarks.none { it.title == "My Note" }) state = awaitItem()
      assertEquals("Chapter 5 · 0:00", state.bookmarks.first().subtitle)
    }
  }

  @Test
  fun `untitled bookmark subtitle is position only`() = runTest {
    val vm = viewModel(book(offset = 0), bookmarks = listOf(bookmark(title = null)))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitItem()
      while (state.bookmarks.none { it.title == "Chapter 5" }) state = awaitItem()
      assertEquals("0:00", state.bookmarks.first().subtitle)
    }
  }

  @Test
  fun `undoing a delete restores the bookmark`() = runTest {
    val mark = bookmark(title = "Keep me")
    val vm = viewModel(book(offset = 0), bookmarks = listOf(mark))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitItem()
      while (state.bookmarks.none { it.title == "Keep me" }) state = awaitItem()

      vm.deleteBookmark(mark.id)
      while (state.bookmarks.isNotEmpty()) state = awaitItem()

      vm.undoDelete(mark)
      while (state.bookmarks.none { it.title == "Keep me" }) state = awaitItem()
      assertEquals(1, state.bookmarks.size)
    }
  }

  @Test
  fun `selecting a bookmark whose book is gone emits unavailable and does not navigate`() = runTest {
    val mark = bookmark(title = "Gone")
    val theBook = book(offset = 0)
    var bookAvailable = true
    val navigator = mockk<Navigator>(relaxed = true)
    val repo = mockk<BookRepository>(relaxed = true) {
      coEvery { get(bookId) } coAnswers { if (bookAvailable) theBook else null }
      every { flow(bookId) } returns MutableStateFlow(theBook)
    }
    val bookmarkRepo = mockk<BookmarkRepo>(relaxed = true) {
      coEvery { bookmarks(theBook.content) } returns listOf(mark)
    }
    val overrideRepo = mockk<ChapterNameOverrideRepo>(relaxed = true) {
      every { overridesForBook(bookId) } returns MutableStateFlow(emptyList())
    }
    val vm = BookmarkViewModel(
      currentBookStore = mockk(relaxed = true),
      repo = repo,
      bookmarkRepo = bookmarkRepo,
      chapterNameOverrideRepo = overrideRepo,
      playStateManager = mockk(relaxed = true),
      playerController = mockk(relaxed = true),
      navigator = navigator,
      context = mockk(relaxed = true),
      bookId = bookId,
    )
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitItem()
      while (state.bookmarks.none { it.title == "Gone" }) state = awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
    bookAvailable = false
    vm.viewEffects.test {
      vm.selectBookmark(mark.id)
      assertEquals(BookmarkViewEffect.BookmarkUnavailable, awaitItem())
    }
    verify(exactly = 0) { navigator.goBack() }
  }

  @Test
  fun `blank name does not create a bookmark`() = runTest {
    val theBook = book(offset = 0)
    val bookmarkRepo = mockk<BookmarkRepo>(relaxed = true) {
      coEvery { bookmarks(theBook.content) } returns emptyList()
    }
    val repo = mockk<BookRepository>(relaxed = true) {
      coEvery { get(bookId) } returns theBook
      every { flow(bookId) } returns MutableStateFlow(theBook)
    }
    val overrideRepo = mockk<ChapterNameOverrideRepo>(relaxed = true) {
      every { overridesForBook(bookId) } returns MutableStateFlow(emptyList())
    }
    val vm = BookmarkViewModel(
      currentBookStore = mockk(relaxed = true),
      repo = repo,
      bookmarkRepo = bookmarkRepo,
      chapterNameOverrideRepo = overrideRepo,
      playStateManager = mockk(relaxed = true),
      playerController = mockk(relaxed = true),
      navigator = mockk(relaxed = true),
      context = mockk(relaxed = true),
      bookId = bookId,
    )
    vm.addBookmark("   ")
    coVerify(exactly = 0) { bookmarkRepo.addBookmarkAtBookPosition(any(), any(), any()) }
  }
}
