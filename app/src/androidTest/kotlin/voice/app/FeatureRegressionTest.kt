package voice.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.datastore.core.DataStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.zacsweers.metro.Inject
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import voice.core.common.rootGraphAs
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.GridMode
import voice.core.data.MarkData
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.core.data.repo.ChapterRepo
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.GridModeStore
import voice.core.data.store.OnboardingCompletedStore
import java.time.Instant

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FeatureRegressionTest {
  @get:Rule
  @Suppress("DEPRECATION") // The v2 test dispatcher disposes real MediaController listeners off the Android main thread.
  val compose = createEmptyComposeRule()

  @Inject lateinit var books: BookContentRepo

  @Inject lateinit var chapters: ChapterRepo

  @Inject lateinit var overrides: ChapterNameOverrideRepo
  @field:[Inject GridModeStore] lateinit var layout: DataStore<GridMode>
  @field:[Inject OnboardingCompletedStore] lateinit var onboarding: DataStore<Boolean>
  @field:[Inject CurrentBookStore] lateinit var currentBook: DataStore<BookId?>

  private val bookId = BookId("v127-feature-regression")
  private val chapterId = ChapterId("file:///v127-feature-regression.m4a")
  private val title = "V127 Regression Fixture"

  @Test
  fun gridSearchLongPressOpensMenuAndTapOpensBook() = searchMenu(GridMode.GRID)

  @Test
  fun listSearchLongPressOpensMenuAndTapOpensBook() = searchMenu(GridMode.LIST)

  private fun searchMenu(mode: GridMode) = withFixture(mode) {
    ActivityScenario.launch(MainActivity::class.java).use {
      search()
      compose.onNode(hasText(title) and hasClickAction() and !hasSetTextAction()).performTouchInput { longClick() }
      compose.waitUntilAtLeastOneExists(hasText("Name"), 10_000)
      compose.onNodeWithText("Name").assertIsDisplayed()
      compose.onNodeWithText("Delete Book").assertIsDisplayed()
      pressBack()
      compose.onNode(hasText(title) and hasClickAction() and !hasSetTextAction()).performClick()
      compose.waitUntilAtLeastOneExists(hasText("Chapter 12"), 10_000)
      compose.onNodeWithText(title).assertIsDisplayed()
    }
  }

  @Test
  fun chapterCorrectionPersistsAcrossReopenAndResetClearsNames() = withFixture(GridMode.GRID) {
    ActivityScenario.launch(MainActivity::class.java).use {
      search()
      compose.onNode(hasText(title) and hasClickAction() and !hasSetTextAction()).performClick()
      compose.waitUntilAtLeastOneExists(hasText("Chapter 12"), 10_000)
      openEditor()
      repeat(2) { compose.onNodeWithContentDescription("Decrease chapter offset").performClick() }
      compose.waitUntilAtLeastOneExists(hasText("-2"), 10_000)
      compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Chapter 8"))
      compose.onNodeWithText("Chapter 8").assertIsDisplayed()
      compose.onNodeWithText("Chapter 9").assertIsDisplayed()
      compose.onNodeWithContentDescription("Close").performClick()
      openEditor()
      compose.onNodeWithContentDescription("Decrease chapter offset").performClick()
      compose.waitUntilAtLeastOneExists(hasText("-3"), 10_000)
      compose.waitUntil(10_000) {
        runBlocking { overrides.overridesForBook(bookId).first().size == 2 }
      }
      runBlocking {
        books.get(bookId)!!.chapterNameOffset shouldBe -3
        overrides.overridesForBook(bookId).first().map { it.name } shouldBe listOf("Chapter 8", "Chapter 9")
      }
      compose.onNodeWithContentDescription("More").performClick()
      compose.onNodeWithText("Reset all to defaults").performClick()
      compose.onNodeWithText("Reset").performClick()
      compose.waitUntil(10_000) {
        runBlocking { books.get(bookId)!!.chapterNameOffset == 0 && overrides.overridesForBook(bookId).first().isEmpty() }
      }
      compose.onNodeWithContentDescription("Close").performClick()
      openEditor()
      compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Chapter 10"))
      compose.onNodeWithText("Chapter 10").assertIsDisplayed()
      compose.onNodeWithText("Chapter 11").assertIsDisplayed()
      compose.onNodeWithText("0").assertIsDisplayed()
    }
  }

  private fun openEditor() {
    compose.onNodeWithContentDescription("More").performClick()
    compose.waitUntilAtLeastOneExists(hasText("Chapter Fix"), 10_000)
    compose.onNodeWithText("Chapter Fix").performClick()
    compose.waitUntilAtLeastOneExists(hasText("Edit chapter names"), 10_000)
  }

  private fun search() {
    compose.waitUntilAtLeastOneExists(hasSetTextAction(), 10_000)
    compose.onNode(hasSetTextAction()).performClick()
    // Clearing explicitly also tests returning to an existing search after closing the editor.
    compose.onNode(hasSetTextAction()).performTextReplacement(title)
    compose.waitUntilAtLeastOneExists(hasText(title) and hasClickAction() and !hasSetTextAction(), 10_000)
  }

  private fun withFixture(
    mode: GridMode,
    block: () -> Unit,
  ) {
    rootGraphAs<TestGraph>().inject(this)
    val oldLayout = runBlocking { layout.data.first() }
    val oldOnboarding = runBlocking { onboarding.data.first() }
    val oldBook = runBlocking { currentBook.data.first() }
    runBlocking {
      layout.updateData { mode }
      onboarding.updateData { true }
      chapters.put(
        Chapter(
          id = chapterId,
          name = "Regression fixture",
          duration = 120_000L,
          fileLastModified = Instant.EPOCH,
          markData = (0..3).map { MarkData(it * 30_000L, "Chapter ${it + 10}") },
        ),
      )
      overrides.deleteAll(bookId)
      books.put(
        BookContent(
          id = bookId, playbackSpeed = 1f, skipSilence = false, isActive = true,
          lastPlayedAt = Instant.now(), author = "Regression", name = title, addedAt = Instant.now(),
          chapters = listOf(chapterId), currentChapter = chapterId, positionInChapter = 65_000L,
          cover = null, gain = 0f, genre = null, narrator = null, series = null, part = null,
        ),
      )
    }
    try {
      block()
    } finally {
      runBlocking {
        books.get(bookId)?.let { books.put(it.copy(isActive = false)) }
        overrides.deleteAll(bookId)
        layout.updateData { oldLayout }
        onboarding.updateData { oldOnboarding }
        currentBook.updateData { oldBook }
      }
    }
  }
}
