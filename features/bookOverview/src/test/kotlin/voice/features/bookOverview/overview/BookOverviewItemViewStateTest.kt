package voice.features.bookOverview.overview

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.features.bookOverview.book
import voice.features.bookOverview.chapter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class BookOverviewItemViewStateTest {

  private val finishedBook = book(chapters = listOf(chapter(duration = 10_000)), time = 10_000)

  private fun Instant.expectedLabel(): String = atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

  @Test
  fun `a book in progress is not finished and carries no date`() {
    val state = book(chapters = listOf(chapter(duration = 10_000)), time = 4_000).toItemViewState()
    state.finished shouldBe false
    state.finishedOn shouldBe null
  }

  @Test
  fun `session evidence dates the finish`() {
    val finishedAt = Instant.parse("2026-03-12T10:00:00Z")
    val state = finishedBook.toItemViewState(finishedAt = finishedAt)
    state.finished shouldBe true
    state.finishedOn shouldBe finishedAt.expectedLabel()
  }

  @Test
  fun `session evidence wins over the last play`() {
    val finishedAt = Instant.parse("2026-03-12T10:00:00Z")
    val replayed = Instant.parse("2026-08-01T10:00:00Z")
    val book = finishedBook.copy(content = finishedBook.content.copy(lastPlayedAt = replayed))
    book.toItemViewState(finishedAt = finishedAt).finishedOn shouldBe finishedAt.expectedLabel()
  }

  @Test
  fun `without sessions the last play dates the finish`() {
    val lastPlayed = Instant.parse("2026-01-28T10:00:00Z")
    val book = finishedBook.copy(content = finishedBook.content.copy(lastPlayedAt = lastPlayed))
    book.toItemViewState().finishedOn shouldBe lastPlayed.expectedLabel()
  }

  @Test
  fun `a finished book that was never played shows no date`() {
    // Scanned books start at the epoch; marking one complete by hand must not print 1970.
    val state = finishedBook.toItemViewState()
    state.finished shouldBe true
    state.finishedOn shouldBe null
  }
}
