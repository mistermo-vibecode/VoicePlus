package voice.features.listeningStats

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.ListeningSession
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ListeningSessionRepo
import voice.navigation.Destination
import voice.navigation.Navigator
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

@Inject
class ListeningStatsViewModel(
  private val sessionRepo: ListeningSessionRepo,
  private val bookRepo: BookRepository,
  private val navigator: Navigator,
) {

  /** Null while the first aggregation is still in flight, so the screen can tell "loading" from "no data". */
  @Composable
  fun viewState(): ListeningStatsViewState? {
    val combined by remember {
      // Only stable library metadata is consumed from bookRepo, but its flow re-emits on every
      // position save (~1/second while playing). Deriving and de-duplicating it here keeps the
      // full-table aggregation below from re-running once a second with the screen open.
      val librarySummary = bookRepo.flow()
        .map { books ->
          LibrarySummary(
            size = books.size,
            completed = books.count { it.isCompleted() },
            names = books.associate { it.id to it.content.name },
          )
        }
        .distinctUntilChanged()
      combine(sessionRepo.allSessions(), librarySummary) { sessions, library ->
        val locale = Locale.getDefault()
        computeStats(
          sessions = sessions,
          librarySize = library.size,
          booksCompleted = library.completed,
          bookNames = library.names,
          zone = ZoneId.systemDefault(),
          today = LocalDate.now(),
          locale = locale,
          dayLabel = dayLabelFormatter(locale)::format,
        )
      }
    }.collectAsState(initial = null)
    return combined
  }

  fun onClose() {
    navigator.goBack()
  }

  fun onBookClick(bookId: BookId) {
    navigator.goTo(Destination.Playback(bookId))
  }
}

internal data class LibrarySummary(
  val size: Int,
  val completed: Int,
  val names: Map<BookId, String>,
)

/** Day-and-month in the locale's own field order — "8/2" in the US, "2/8" in the UK. */
private fun dayLabelFormatter(locale: Locale): DateTimeFormatter =
  DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "Md"), locale)

internal fun computeStats(
  sessions: List<ListeningSession>,
  librarySize: Int,
  booksCompleted: Int,
  bookNames: Map<BookId, String> = emptyMap(),
  zone: ZoneId,
  today: LocalDate,
  locale: Locale = Locale.getDefault(),
  dayLabel: (LocalDate) -> String = { "${it.dayOfMonth}/${it.monthValue}" },
): ListeningStatsViewState {
  if (sessions.isEmpty()) {
    return ListeningStatsViewState.Empty.copy(
      booksCompleted = booksCompleted,
      booksInLibrary = librarySize,
    )
  }

  val weekFields = WeekFields.of(locale)
  val dailyTotals = dailyTotals(sessions, zone)

  // Summary metrics
  val totalLifetimeMs = sessions.sumOf { it.durationMs }
  val todayMs = dailyTotals[today] ?: 0L
  val weekStart = today.with(weekFields.dayOfWeek(), 1)
  val thisWeekMs = dailyTotals.entries.filter { it.key >= weekStart }.sumOf { it.value }
  val thisMonthMs = dailyTotals.entries
    .filter { it.key.month == today.month && it.key.year == today.year }
    .sumOf { it.value }

  val previousWeekStart = weekStart.minusWeeks(1)
  val previousWeekEnd = previousWeekStart.plusDays(today.toEpochDay() - weekStart.toEpochDay())
  val previousWeekMs = dailyTotals.entries
    .filter { it.key in previousWeekStart..previousWeekEnd }
    .sumOf { it.value }
  val weekChangePercent = previousWeekMs.takeIf { it > 0 }?.let {
    (((thisWeekMs - it) * 100.0) / it).roundToInt()
  }

  // Average per day (over days since first session)
  val firstDay = dailyTotals.keys.min()
  val daysSinceFirst = (today.toEpochDay() - firstDay.toEpochDay() + 1).coerceAtLeast(1)
  val avgDailyMs = totalLifetimeMs / daysSinceFirst
  val activeDaysLast30 = dailyTotals.keys.count { it in today.minusDays(29)..today }
  val avgSessionMs = totalLifetimeMs / sessions.size

  val topBook = sessions
    .filter { it.bookId in bookNames }
    .groupBy { it.bookId }
    .mapValues { (_, bookSessions) -> bookSessions.sumOf { it.durationMs } }
    .maxByOrNull { it.value }
    ?.let { (bookId, durationMs) ->
      TopBookStats(
        bookId = bookId,
        name = bookNames.getValue(bookId),
        durationMs = durationMs,
      )
    }

  // Longest day
  val longestEntry = dailyTotals.maxByOrNull { it.value }
  val longestDayMs = longestEntry?.value ?: 0L
  val longestDayLabel = longestEntry?.key
    ?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

  val (currentStreak, longestStreak) = computeStreaks(dailyTotals.keys, today)

  val bestDayOfWeek = dailyTotals.entries
    .groupBy({ it.key.dayOfWeek }, { it.value })
    .mapValues { (_, totals) -> totals.sum() }
    .maxByOrNull { it.value }
    ?.key
    ?.getDisplayName(TextStyle.FULL, locale)

  // Daily chart — last 30 days
  val dailyData = (29 downTo 0).map { daysBack ->
    val date = today.minusDays(daysBack.toLong())
    ChartDataPoint(label = dayLabel(date), valueMs = dailyTotals[date] ?: 0L)
  }

  // Weekly chart — last 12 weeks
  val weeklyData = (11 downTo 0).map { weeksBack ->
    val weekStartOfBucket = today.minusWeeks(weeksBack.toLong()).with(weekFields.dayOfWeek(), 1)
    val weekEndOfBucket = weekStartOfBucket.plusDays(6)
    ChartDataPoint(
      label = "W${weekStartOfBucket.get(weekFields.weekOfWeekBasedYear())}",
      valueMs = dailyTotals.entries
        .filter { it.key >= weekStartOfBucket && it.key <= weekEndOfBucket }
        .sumOf { it.value },
    )
  }

  // Monthly chart — last 12 months
  val monthlyData = (11 downTo 0).map { monthsBack ->
    val targetDate = today.minusMonths(monthsBack.toLong())
    ChartDataPoint(
      label = targetDate.month.getDisplayName(TextStyle.SHORT, locale),
      valueMs = dailyTotals.entries
        .filter { it.key.month == targetDate.month && it.key.year == targetDate.year }
        .sumOf { it.value },
    )
  }

  return ListeningStatsViewState(
    totalLifetimeMs = totalLifetimeMs,
    todayMs = todayMs,
    thisWeekMs = thisWeekMs,
    thisMonthMs = thisMonthMs,
    weekChangePercent = weekChangePercent,
    firstListeningDateLabel = firstDay.format(
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
    ),
    booksCompleted = booksCompleted,
    booksInLibrary = librarySize,
    dailyData = dailyData,
    weeklyData = weeklyData,
    monthlyData = monthlyData,
    avgDailyMs = avgDailyMs,
    activeDaysLast30 = activeDaysLast30,
    avgSessionMs = avgSessionMs,
    topBook = topBook,
    longestDayMs = longestDayMs,
    longestDayLabel = longestDayLabel,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    bestDayOfWeek = bestDayOfWeek,
  )
}

/**
 * Listening time per local calendar day. A session is split at midnight rather than billed whole to
 * the day it started on — bedtime listening routinely crosses midnight, and crediting a 23:30→00:45
 * session entirely to the earlier day leaves the new day showing zero (and unable to extend a streak).
 */
internal fun dailyTotals(
  sessions: List<ListeningSession>,
  zone: ZoneId,
): Map<LocalDate, Long> = buildMap {
  sessions.forEach { session ->
    session.dailyShares(zone).forEach { (date, ms) ->
      merge(date, ms, Long::plus)
    }
  }
}

private fun ListeningSession.dailyShares(zone: ZoneId): Map<LocalDate, Long> {
  val startDate = startedAt.atZone(zone).toLocalDate()
  val endDate = endedAt.atZone(zone).toLocalDate()
  val spanMs = endedAt.toEpochMilli() - startedAt.toEpochMilli()
  // Same-day, or a duration that doesn't match the recorded span (finalized/interrupted sessions):
  // don't invent a distribution, bill it where it started.
  if (startDate == endDate || spanMs <= 0L) return mapOf(startDate to durationMs)

  val shares = LinkedHashMap<LocalDate, Long>()
  var assigned = 0L
  var date = startDate
  while (date <= endDate) {
    // Real instants, so DST-shortened and -lengthened days apportion correctly.
    val dayStart = maxOf(startedAt, date.atStartOfDay(zone).toInstant())
    val dayEnd = minOf(endedAt, date.plusDays(1).atStartOfDay(zone).toInstant())
    val overlapMs = dayEnd.toEpochMilli() - dayStart.toEpochMilli()
    if (overlapMs > 0L) {
      val share = durationMs * overlapMs / spanMs
      shares[date] = share
      assigned += share
    }
    date = date.plusDays(1)
  }
  // Integer division loses up to a millisecond per day; keep the parts summing to the recorded total.
  shares.keys.lastOrNull()?.let { last ->
    shares[last] = shares.getValue(last) + (durationMs - assigned)
  }
  return shares
}

/** @return current streak (counting back from [today]) to longest streak ever. */
internal fun computeStreaks(
  daysWithListening: Set<LocalDate>,
  today: LocalDate,
): Pair<Int, Int> {
  if (daysWithListening.isEmpty()) return 0 to 0

  val sortedDays = daysWithListening.sorted()
  var longest = 1
  var run = 1
  for (i in 1 until sortedDays.size) {
    run = if (sortedDays[i].minusDays(1) == sortedDays[i - 1]) run + 1 else 1
    if (run > longest) longest = run
  }

  // Count back from today, or from yesterday when today hasn't been listened to yet — otherwise the
  // streak would read 0 every morning until the user next pressed play.
  var check = if (today in daysWithListening) today else today.minusDays(1)
  var current = 0
  while (check in daysWithListening) {
    current++
    check = check.minusDays(1)
  }
  return current to longest
}

private fun Book.isCompleted(): Boolean {
  return duration > 0 && position >= duration - 5_000L
}

data class ChartDataPoint(
  val label: String,
  val valueMs: Long,
)

data class TopBookStats(
  val bookId: BookId,
  val name: String,
  val durationMs: Long,
)

data class ListeningStatsViewState(
  val totalLifetimeMs: Long,
  val todayMs: Long,
  val thisWeekMs: Long,
  val thisMonthMs: Long,
  val weekChangePercent: Int?,
  val firstListeningDateLabel: String?,
  val booksCompleted: Int,
  val booksInLibrary: Int,
  val dailyData: List<ChartDataPoint>,
  val weeklyData: List<ChartDataPoint>,
  val monthlyData: List<ChartDataPoint>,
  val avgDailyMs: Long,
  val activeDaysLast30: Int,
  val avgSessionMs: Long,
  val topBook: TopBookStats?,
  val longestDayMs: Long,
  val longestDayLabel: String?,
  val currentStreak: Int,
  val longestStreak: Int,
  val bestDayOfWeek: String?,
) {
  companion object {
    val Empty = ListeningStatsViewState(
      totalLifetimeMs = 0L,
      todayMs = 0L,
      thisWeekMs = 0L,
      thisMonthMs = 0L,
      weekChangePercent = null,
      firstListeningDateLabel = null,
      booksCompleted = 0,
      booksInLibrary = 0,
      dailyData = emptyList(),
      weeklyData = emptyList(),
      monthlyData = emptyList(),
      avgDailyMs = 0L,
      activeDaysLast30 = 0,
      avgSessionMs = 0L,
      topBook = null,
      longestDayMs = 0L,
      longestDayLabel = null,
      currentStreak = 0,
      longestStreak = 0,
      bestDayOfWeek = null,
    )
  }
}
