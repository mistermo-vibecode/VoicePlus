package voice.features.settings.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest
import voice.core.data.Book
import voice.core.data.ListeningSession
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ListeningSessionRepo
import voice.navigation.Navigator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Inject
class ListeningStatsViewModel(
  private val sessionRepo: ListeningSessionRepo,
  private val bookRepo: BookRepository,
  private val navigator: Navigator,
) {

  @Composable
  fun viewState(): ListeningStatsViewState {
    val combined by remember {
      var firstEmitted = false
      combine(sessionRepo.allSessions(), bookRepo.flow()) { sessions, books ->
        computeStats(sessions, books.size, books.count { it.isCompleted() })
      }.transformLatest { value ->
        if (!firstEmitted) {
          firstEmitted = true
          emit(value)
        } else {
          delay(1500L)
          emit(value)
        }
      }
    }.collectAsState(initial = null)
    return combined ?: ListeningStatsViewState.Empty
  }

  private fun computeStats(
    sessions: List<ListeningSession>,
    librarySize: Int,
    booksCompleted: Int,
  ): ListeningStatsViewState {
    if (sessions.isEmpty()) {
      return ListeningStatsViewState(
        totalLifetimeMs = 0L,
        todayMs = 0L,
        thisWeekMs = 0L,
        thisMonthMs = 0L,
        booksCompleted = booksCompleted,
        booksInLibrary = librarySize,
        dailyData = emptyList(),
        weeklyData = emptyList(),
        monthlyData = emptyList(),
        avgDailyMs = 0L,
        longestDayMs = 0L,
        longestDayLabel = null,
        currentStreak = 0,
        longestStreak = 0,
        bestDayOfWeek = null,
      )
    }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val weekFields = WeekFields.of(Locale.getDefault())

    fun ListeningSession.localDate() = startedAt.atZone(zone).toLocalDate()

    val byDay = sessions.groupBy { it.localDate() }
    val dailyTotals: Map<LocalDate, Long> = byDay.mapValues { (_, s) -> s.sumOf { it.durationMs } }

    // Summary metrics
    val totalLifetimeMs = sessions.sumOf { it.durationMs }
    val todayMs = dailyTotals[today] ?: 0L
    val weekStart = today.with(weekFields.dayOfWeek(), 1)
    val thisWeekMs = dailyTotals.entries.filter { it.key >= weekStart }.sumOf { it.value }
    val thisMonthMs = dailyTotals.entries.filter { it.key.month == today.month && it.key.year == today.year }.sumOf { it.value }

    // Average per day (over days since first session)
    val firstDay = sessions.minOf { it.localDate() }
    val daysSinceFirst = (today.toEpochDay() - firstDay.toEpochDay() + 1).coerceAtLeast(1)
    val avgDailyMs = totalLifetimeMs / daysSinceFirst

    // Longest day
    val longestEntry = dailyTotals.maxByOrNull { it.value }
    val longestDayMs = longestEntry?.value ?: 0L
    val longestDayLabel = longestEntry?.key?.toString()

    // Streaks
    val daysWithListening = dailyTotals.keys.sorted()
    val (currentStreak, longestStreak) = computeStreaks(daysWithListening, today)

    // Best day of week
    val byDayOfWeek: Map<DayOfWeek, Long> = sessions.groupBy { it.startedAt.atZone(zone).dayOfWeek }
      .mapValues { (_, s) -> s.sumOf { it.durationMs } }
    val bestDayOfWeek = byDayOfWeek.maxByOrNull { it.value }?.key
      ?.getDisplayName(TextStyle.FULL, Locale.getDefault())

    // Daily chart — last 30 days
    val dailyData = (29 downTo 0).map { daysBack ->
      val date = today.minusDays(daysBack.toLong())
      ChartDataPoint(
        label = "${date.dayOfMonth}/${date.monthValue}",
        valueMs = dailyTotals[date] ?: 0L,
      )
    }

    // Weekly chart — last 12 weeks
    val weeklyData = (11 downTo 0).map { weeksBack ->
      val weekDate = today.minusWeeks(weeksBack.toLong())
      val weekStart2 = weekDate.with(weekFields.dayOfWeek(), 1)
      val weekEnd2 = weekStart2.plusDays(6)
      val weekTotal = dailyTotals.entries
        .filter { it.key >= weekStart2 && it.key <= weekEnd2 }
        .sumOf { it.value }
      ChartDataPoint(
        label = "W${weekStart2.get(weekFields.weekOfWeekBasedYear())}",
        valueMs = weekTotal,
      )
    }

    // Monthly chart — last 12 months
    val monthlyData = (11 downTo 0).map { monthsBack ->
      val targetDate = today.minusMonths(monthsBack.toLong())
      val monthTotal = dailyTotals.entries
        .filter { it.key.month == targetDate.month && it.key.year == targetDate.year }
        .sumOf { it.value }
      ChartDataPoint(
        label = targetDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
        valueMs = monthTotal,
      )
    }

    return ListeningStatsViewState(
      totalLifetimeMs = totalLifetimeMs,
      todayMs = todayMs,
      thisWeekMs = thisWeekMs,
      thisMonthMs = thisMonthMs,
      booksCompleted = booksCompleted,
      booksInLibrary = librarySize,
      dailyData = dailyData,
      weeklyData = weeklyData,
      monthlyData = monthlyData,
      avgDailyMs = avgDailyMs,
      longestDayMs = longestDayMs,
      longestDayLabel = longestDayLabel,
      currentStreak = currentStreak,
      longestStreak = longestStreak,
      bestDayOfWeek = bestDayOfWeek,
    )
  }

  private fun computeStreaks(
    sortedDays: List<LocalDate>,
    today: LocalDate,
  ): Pair<Int, Int> {
    if (sortedDays.isEmpty()) return 0 to 0
    var longest = 1
    var current = 1
    for (i in 1 until sortedDays.size) {
      current = if (sortedDays[i].minusDays(1) == sortedDays[i - 1]) current + 1 else 1
      if (current > longest) longest = current
    }
    // Current streak: count back from today
    val activeStreak = if (sortedDays.last() >= today.minusDays(1)) {
      var streak = 0
      var check = today
      while (check in sortedDays) {
        streak++
        check = check.minusDays(1)
      }
      streak
    } else {
      0
    }
    return activeStreak to longest
  }

  fun onClose() {
    navigator.goBack()
  }
}

private fun Book.isCompleted(): Boolean {
  return duration > 0 && position >= duration - 5_000L
}

data class ChartDataPoint(
  val label: String,
  val valueMs: Long,
)

data class ListeningStatsViewState(
  val totalLifetimeMs: Long,
  val todayMs: Long,
  val thisWeekMs: Long,
  val thisMonthMs: Long,
  val booksCompleted: Int,
  val booksInLibrary: Int,
  val dailyData: List<ChartDataPoint>,
  val weeklyData: List<ChartDataPoint>,
  val monthlyData: List<ChartDataPoint>,
  val avgDailyMs: Long,
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
      booksCompleted = 0,
      booksInLibrary = 0,
      dailyData = emptyList(),
      weeklyData = emptyList(),
      monthlyData = emptyList(),
      avgDailyMs = 0L,
      longestDayMs = 0L,
      longestDayLabel = null,
      currentStreak = 0,
      longestStreak = 0,
      bestDayOfWeek = null,
    )
  }
}
