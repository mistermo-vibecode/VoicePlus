package voice.core.playback.playstate

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookRepository
import voice.core.featureflag.MemoryFeatureFlag
import voice.core.playback.session.MediaId
import voice.core.playback.session.search.book
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

class PositionUpdaterTest {

  @Test
  fun `disabling experimental persistence restarts the save cadence immediately`() = runTest {
    val repo = mockk<BookRepository> {
      coEvery { updateBook(any(), any()) } just Runs
    }
    val featureFlag = MemoryFeatureFlag(true)
    val playStateManager = PlayStateManager().apply {
      playState = PlayStateManager.PlayState.Playing
    }
    val player = player(mediaItem(BookId("book"), ChapterId("chapter")), positionMs = { 1_000L })
    val updater = PositionUpdater(repo, this, playStateManager, featureFlag)

    updater.attachTo(player)
    runCurrent()
    advanceTimeBy(1.minutes.inWholeMilliseconds)
    coVerify(exactly = 0) { repo.updateBook(any(), any()) }

    featureFlag.value = false
    runCurrent()
    advanceTimeBy(999L)
    runCurrent()
    coVerify(exactly = 0) { repo.updateBook(any(), any()) }

    advanceTimeBy(1L)
    runCurrent()
    coVerify(exactly = 1) { repo.updateBook(any(), any()) }
    updater.release()
  }

  @Test
  fun `book switch saves the old and new book positions`() = runTest {
    val oldBook = testBook("old")
    val newBook = testBook("new")
    val contents = mutableMapOf(
      oldBook.id to oldBook.content,
      newBook.id to newBook.content,
    )
    val repo = mockk<BookRepository> {
      coEvery { updateBook(any(), any()) } answers {
        val id = firstArg<BookId>()
        val update = secondArg<(BookContent) -> BookContent>()
        contents[id] = update(contents.getValue(id))
      }
    }
    val oldItem = mediaItem(oldBook.id, oldBook.currentChapter.id)
    val newItem = mediaItem(newBook.id, newBook.currentChapter.id)
    val updater = PositionUpdater(
      repo,
      this,
      PlayStateManager(),
      MemoryFeatureFlag(false),
    )
    updater.attachTo(player(newItem, positionMs = { 1_200L }))

    updater.onPositionDiscontinuity(
      positionInfo(oldItem, 299_000L),
      positionInfo(newItem, 1_200L),
      Player.DISCONTINUITY_REASON_REMOVE,
    )
    runCurrent()

    contents.getValue(oldBook.id).positionInChapter shouldBe 299_000L
    contents.getValue(newBook.id).positionInChapter shouldBe 1_200L
    updater.release()
  }

  @Test
  fun `event flush captures the position before its coroutine runs`() = runTest {
    val book = testBook("book")
    var positionMs = 1_000L
    var saved = book.content
    val repo = mockk<BookRepository> {
      coEvery { updateBook(book.id, any()) } answers {
        saved = secondArg<(BookContent) -> BookContent>()(saved)
      }
    }
    val updater = PositionUpdater(
      repo,
      this,
      PlayStateManager(),
      MemoryFeatureFlag(false),
    )
    updater.attachTo(player(mediaItem(book.id, book.currentChapter.id)) { positionMs })

    updater.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
    positionMs = 9_000L
    runCurrent()

    saved.positionInChapter shouldBe 1_000L
    updater.release()
  }
}

private fun testBook(id: String) = book(
  id = BookId(id),
  chapters = listOf(
    Chapter(
      id = ChapterId("$id-chapter"),
      name = id,
      duration = 600_000L,
      fileLastModified = Instant.EPOCH,
      markData = emptyList(),
    ),
  ),
)

private fun mediaItem(
  bookId: BookId,
  chapterId: ChapterId,
): MediaItem = MediaItem.Builder()
  .setMediaId(Json.encodeToString(MediaId.serializer(), MediaId.Chapter(bookId, chapterId)))
  .build()

private fun player(
  mediaItem: MediaItem,
  positionMs: () -> Long,
): Player = mockk(relaxed = true) {
  every { currentMediaItem } returns mediaItem
  every { currentPosition } answers { positionMs() }
}

private fun positionInfo(
  mediaItem: MediaItem,
  positionMs: Long,
) = Player.PositionInfo(
  null,
  0,
  mediaItem,
  null,
  0,
  positionMs,
  positionMs,
  C.INDEX_UNSET,
  C.INDEX_UNSET,
)
