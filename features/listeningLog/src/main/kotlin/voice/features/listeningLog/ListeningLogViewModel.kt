package voice.features.listeningLog

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import voice.core.common.resolveChapterName
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ListeningEvent
import voice.core.data.ListeningEventType
import voice.core.data.ListeningSession
import voice.core.data.ListeningSessionEndReason
import voice.core.data.byMarkKey
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.core.data.repo.ListeningEventRepo
import voice.core.data.repo.ListeningSessionRepo
import voice.core.playback.PlayerController
import voice.core.strings.R
import voice.core.ui.formatTime
import voice.navigation.Navigator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val COALESCE_WINDOW_MS = 2_000L

@AssistedInject
class ListeningLogViewModel(
  private val sessionRepo: ListeningSessionRepo,
  private val eventRepo: ListeningEventRepo,
  private val bookRepo: BookRepository,
  private val chapterNameOverrideRepo: ChapterNameOverrideRepo,
  private val playerController: PlayerController,
  private val navigator: Navigator,
  private val context: Context,
  @Assisted private val bookId: BookId,
) {

  private val scope = MainScope()
  private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
  private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

  @Composable
  fun viewState(): ListeningLogViewState {
    val sessions by remember { sessionRepo.sessions(bookId) }.collectAsState(initial = emptyList())
    val events by remember { eventRepo.events(bookId) }.collectAsState(initial = emptyList())
    val book by remember { bookRepo.flow(bookId) }.collectAsState(initial = null)
    val overrides by remember { chapterNameOverrideRepo.overridesForBook(bookId) }.collectAsState(initial = emptyList())
    val groups = remember(sessions, events, book, overrides) {
      val offset = book?.content?.chapterNameOffset ?: 0
      val overrideMap = overrides.byMarkKey()
      buildGroups(sessions, events, book, offset, overrideMap)
    }
    return ListeningLogViewState(
      groups = groups,
      bookTitle = book?.content?.name ?: "",
    )
  }

  private fun buildGroups(
    sessions: List<ListeningSession>,
    events: List<ListeningEvent>,
    book: Book?,
    offset: Int,
    overrideMap: Map<Pair<String, Long>, String>,
  ): List<ListeningLogGroup> {
    val timed = buildList {
      sessions.forEach { session ->
        add(
          TimedEntry(
            timestamp = session.startedAt,
            entry = Raw.Play(session),
          ),
        )
        add(
          TimedEntry(
            timestamp = session.endedAt,
            entry = Raw.Pause(session),
          ),
        )
      }
      events
        .filter { ListeningEventType.fromId(it.type) != null }
        .forEach { event ->
          add(
            TimedEntry(
              timestamp = event.at,
              entry = Raw.Transport(event),
            ),
          )
        }
    }
      .sortedByDescending { it.timestamp }

    val coalesced = coalesce(timed)

    return coalesced
      .groupBy { it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() }
      .entries
      .sortedByDescending { it.key }
      .map { (date, dayEntries) ->
        ListeningLogGroup(
          dateLabel = date.format(dateFormatter),
          entries = dayEntries.map { it.toEntry(book, offset, overrideMap) },
        )
      }
  }

  // Merge runs of adjacent same-type Transport entries within the coalesce window into one,
  // keeping the newest entry's destination and the oldest-in-run's origin.
  private fun coalesce(sorted: List<TimedEntry>): List<TimedEntry> {
    val result = mutableListOf<TimedEntry>()
    var index = 0
    while (index < sorted.size) {
      val head = sorted[index]
      val headTransport = head.entry as? Raw.Transport
      if (headTransport == null) {
        result += head
        index++
        continue
      }
      var last = head
      var runEnd = index
      // Entries are sorted DESC, so head is the newest; walk towards older entries.
      while (runEnd + 1 < sorted.size) {
        val candidate = sorted[runEnd + 1]
        val candidateTransport = candidate.entry as? Raw.Transport ?: break
        val sameType = candidateTransport.event.type == headTransport.event.type
        // Per-step: each consecutive same-type event within the window joins the run, so a
        // continuous burst of taps collapses into one entry even if the whole run exceeds 2s.
        val withinWindow = last.timestamp.toEpochMilli() - candidate.timestamp.toEpochMilli() <= COALESCE_WINDOW_MS
        if (!sameType || !withinWindow) break
        last = candidate
        runEnd++
      }
      if (runEnd == index) {
        result += head
      } else {
        val oldest = (sorted[runEnd].entry as Raw.Transport).event
        result += head.copy(
          entry = Raw.Transport(
            event = headTransport.event.copy(fromPositionMs = oldest.fromPositionMs),
          ),
        )
      }
      index = runEnd + 1
    }
    return result
  }

  private fun TimedEntry.toEntry(
    book: Book?,
    offset: Int,
    overrideMap: Map<Pair<String, Long>, String>,
  ): ListeningLogEntry {
    val totalDuration = book?.duration ?: 0L
    return when (val raw = entry) {
      is Raw.Play -> {
        val session = raw.session
        ListeningLogEntry.Play(
          id = "s${session.id}-play",
          timeLabel = timestamp.atZone(ZoneId.systemDefault()).format(timeFormatter),
          chapterName = book.chapterName(session.chapterId, session.startPositionMs, offset, overrideMap),
          positionLabel = formatTime(session.startPositionMs),
          remainingLabel = remainingLabel(totalDuration, session.startPositionMs),
          chapterId = session.chapterId,
          positionMs = session.startPositionMs,
        )
      }
      is Raw.Pause -> {
        val session = raw.session
        val endChapterId = session.endChapterId ?: session.chapterId
        ListeningLogEntry.Pause(
          id = "s${session.id}-pause",
          timeLabel = timestamp.atZone(ZoneId.systemDefault()).format(timeFormatter),
          endReason = ListeningSessionEndReason.fromId(session.endReason),
          chapterName = book.chapterName(endChapterId, session.endPositionMs, offset, overrideMap),
          positionLabel = formatTime(session.endPositionMs),
          remainingLabel = remainingLabel(totalDuration, session.endPositionMs),
          chapterId = endChapterId,
          positionMs = session.endPositionMs,
        )
      }
      is Raw.Transport -> {
        val event = raw.event
        ListeningLogEntry.Transport(
          id = "e${event.id}",
          type = checkNotNull(ListeningEventType.fromId(event.type)),
          fromPositionMs = event.fromPositionMs,
          chapterName = book.chapterName(event.chapterId, event.positionMs, offset, overrideMap),
          positionLabel = formatTime(event.positionMs),
          remainingLabel = remainingLabel(totalDuration, event.positionMs),
          chapterId = event.chapterId,
          positionMs = event.positionMs,
        )
      }
    }
  }

  private fun Book?.chapterName(
    id: ChapterId,
    positionMs: Long,
    offset: Int,
    overrideMap: Map<Pair<String, Long>, String>,
  ): String {
    if (this == null) return context.getString(R.string.unknown_chapter)
    val index = chapters.indexOfFirst { it.id == id }
    if (index == -1) return context.getString(R.string.unknown_chapter)
    val chapter = chapters[index]
    val mark = chapter.chapterMarks.firstOrNull { positionMs in it.startMs..it.endMs }
      ?: chapter.chapterMarks.lastOrNull { positionMs >= it.startMs }
    val override = mark?.let { overrideMap[Pair(chapter.id.value, it.startMs)] }
    // Mirror the other surfaces' fallback: prefer the chapter's own name, then a localized
    // "Unknown chapter" — rather than a synthetic, mis-numbered "Chapter N".
    return resolveChapterName(mark?.name ?: "", offset, override)
      .ifBlank { chapter.name.orEmpty() }
      .ifBlank { context.getString(R.string.unknown_chapter) }
  }

  private fun remainingLabel(
    totalDurationMs: Long,
    positionMs: Long,
  ): String {
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
      eventRepo.deleteAllForBook(bookId)
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

private data class TimedEntry(
  val timestamp: Instant,
  val entry: Raw,
)

private sealed interface Raw {
  data class Play(val session: ListeningSession) : Raw
  data class Pause(val session: ListeningSession) : Raw
  data class Transport(val event: ListeningEvent) : Raw
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
  val id: String
  val chapterId: ChapterId
  val positionMs: Long
  val chapterName: String
  val positionLabel: String
  val remainingLabel: String

  data class Play(
    override val id: String,
    val timeLabel: String,
    override val chapterName: String,
    override val positionLabel: String,
    override val remainingLabel: String,
    override val chapterId: ChapterId,
    override val positionMs: Long,
  ) : ListeningLogEntry

  data class Pause(
    override val id: String,
    val timeLabel: String,
    val endReason: ListeningSessionEndReason?,
    override val chapterName: String,
    override val positionLabel: String,
    override val remainingLabel: String,
    override val chapterId: ChapterId,
    override val positionMs: Long,
  ) : ListeningLogEntry

  data class Transport(
    override val id: String,
    val type: ListeningEventType,
    val fromPositionMs: Long?,
    override val chapterName: String,
    override val positionLabel: String,
    override val remainingLabel: String,
    override val chapterId: ChapterId,
    override val positionMs: Long,
  ) : ListeningLogEntry
}
