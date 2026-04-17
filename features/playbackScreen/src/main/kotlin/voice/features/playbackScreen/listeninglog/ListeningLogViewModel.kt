package voice.features.playbackScreen.listeninglog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ListeningSession
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ListeningSessionRepo
import voice.core.playback.PlayerController
import voice.core.ui.formatTime
import voice.navigation.Navigator
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val SKIP_THRESHOLD_MS = 30_000L

@AssistedInject
class ListeningLogViewModel(
  private val sessionRepo: ListeningSessionRepo,
  private val bookRepo: BookRepository,
  private val playerController: PlayerController,
  private val navigator: Navigator,
  @Assisted private val bookId: BookId,
) {

  private val scope = MainScope()
  private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
  private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

  @Composable
  fun viewState(): ListeningLogViewState {
    val sessions by remember { sessionRepo.sessions(bookId) }.collectAsState(initial = emptyList())
    val book by remember { bookRepo.flow(bookId) }.collectAsState(initial = null)
    val groups = remember(sessions, book) { sessions.toGroups(book) }
    return ListeningLogViewState(
      groups = groups,
      bookTitle = book?.content?.name ?: "",
    )
  }

  private fun List<ListeningSession>.toGroups(book: Book?): List<ListeningLogGroup> {
    val sorted = sortedByDescending { it.startedAt }
    return sorted
      .groupBy { it.startedAt.atZone(ZoneId.systemDefault()).toLocalDate() }
      .entries
      .sortedByDescending { it.key }
      .map { (date, daySessions) ->
        val entries = daySessions.toEntries(book)
        ListeningLogGroup(
          dateLabel = date.format(dateFormatter),
          entries = entries,
        )
      }
  }

  private fun List<ListeningSession>.toEntries(book: Book?): List<ListeningLogEntry> {
    val result = mutableListOf<ListeningLogEntry>()
    // Sessions already sorted descending within this day group
    forEachIndexed { index, session ->
      // Detect a skip: if the next (chronologically earlier) session's end
      // differs from this session's start by more than the threshold.
      val next = getOrNull(index + 1)
      val wasSkip = next != null &&
        Math.abs(session.startPositionMs - next.endPositionMs) > SKIP_THRESHOLD_MS

      val pauseTime = session.endedAt.atZone(ZoneId.systemDefault()).format(timeFormatter)
      val playTime = session.startedAt.atZone(ZoneId.systemDefault()).format(timeFormatter)

      val startChapter = book.chapterName(session.chapterId, session.startPositionMs)
      val endChapterId = session.endChapterId ?: session.chapterId
      val endChapter = book.chapterName(endChapterId, session.endPositionMs)

      val totalDuration = book?.duration ?: 0L

      result += ListeningLogEntry.Pause(
        id = session.id * 3,
        timeLabel = pauseTime,
        chapterName = endChapter,
        positionLabel = formatTime(session.endPositionMs),
        remainingLabel = remainingLabel(totalDuration, session.endPositionMs),
        chapterId = endChapterId,
        positionMs = session.endPositionMs,
      )
      result += ListeningLogEntry.Play(
        id = session.id * 3 + 1,
        timeLabel = playTime,
        chapterName = startChapter,
        positionLabel = formatTime(session.startPositionMs),
        remainingLabel = remainingLabel(totalDuration, session.startPositionMs),
        chapterId = session.chapterId,
        positionMs = session.startPositionMs,
      )
      if (wasSkip) {
        result += ListeningLogEntry.Skip(
          id = session.id * 3 + 2,
          chapterName = startChapter,
          positionLabel = formatTime(session.startPositionMs),
          remainingLabel = remainingLabel(totalDuration, session.startPositionMs),
          chapterId = session.chapterId,
          positionMs = session.startPositionMs,
        )
      }
    }
    return result
  }

  private fun Book?.chapterName(id: ChapterId, positionMs: Long): String {
    if (this == null) return "Unknown Chapter"
    val index = chapters.indexOfFirst { it.id == id }
    if (index == -1) return "Unknown Chapter"
    val chapter = chapters[index]
    val mark = chapter.chapterMarks.firstOrNull { positionMs in it.startMs..it.endMs }
      ?: chapter.chapterMarks.lastOrNull { positionMs >= it.startMs }
    val markName = mark?.name
    if (!markName.isNullOrBlank()) return markName
    val fallback = chapter.name
    return if (fallback.isNullOrBlank()) "Chapter ${index + 1}" else fallback
  }

  private fun remainingLabel(totalDurationMs: Long, positionMs: Long): String {
    val remainingMs = (totalDurationMs - positionMs).coerceAtLeast(0L)
    val hours = TimeUnit.MILLISECONDS.toHours(remainingMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60
    return "${hours}h ${minutes}m left"
  }

  fun onEntryClick(entry: ListeningLogEntry) {
    playerController.setPosition(entry.positionMs, entry.chapterId)
    navigator.goBack()
  }

  fun clearHistory() {
    scope.launch {
      sessionRepo.deleteAllForBook(bookId)
    }
  }

  fun onClose() {
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(bookId: BookId): ListeningLogViewModel
  }
}

data class ListeningLogViewState(
  val groups: List<ListeningLogGroup>,
  val bookTitle: String,
)

data class ListeningLogGroup(
  val dateLabel: String,
  val entries: List<ListeningLogEntry>,
)

sealed interface ListeningLogEntry {
  val id: Long
  val chapterId: ChapterId
  val positionMs: Long
  val chapterName: String
  val positionLabel: String
  val remainingLabel: String

  data class Play(
    override val id: Long,
    val timeLabel: String,
    override val chapterName: String,
    override val positionLabel: String,
    override val remainingLabel: String,
    override val chapterId: ChapterId,
    override val positionMs: Long,
  ) : ListeningLogEntry

  data class Pause(
    override val id: Long,
    val timeLabel: String,
    override val chapterName: String,
    override val positionLabel: String,
    override val remainingLabel: String,
    override val chapterId: ChapterId,
    override val positionMs: Long,
  ) : ListeningLogEntry

  data class Skip(
    override val id: Long,
    override val chapterName: String,
    override val positionLabel: String,
    override val remainingLabel: String,
    override val chapterId: ChapterId,
    override val positionMs: Long,
  ) : ListeningLogEntry
}
