package voice.features.listeningStats

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ListeningSession
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * The stats arithmetic is pure calendar maths with no UI — and until this file existed, none of it
 * was exercised. Every case below is a user-visible number on the statistics screen.
 */
class ListeningStatsTest {

  private val zone: ZoneId = ZoneId.of("Europe/Berlin")
  private val today: LocalDate = LocalDate.of(2026, 8, 2)

  private fun session(
    date: LocalDate,
    startHour: Int,
    durationMinutes: Long,
    startMinute: Int = 0,
  ): ListeningSession {
    val start = date.atTime(startHour, startMinute).atZone(zone).toInstant()
    val durationMs = durationMinutes * 60_000
    return ListeningSession(
      bookId = BookId("content://book"),
      chapterId = ChapterId("content://chapter"),
      startedAt = start,
      endedAt = start.plusMillis(durationMs),
      durationMs = durationMs,
      startPositionMs = 0,
      endPositionMs = durationMs,
    )
  }

  private fun stats(sessions: List<ListeningSession>) = computeStats(
    sessions = sessions,
    librarySize = 3,
    booksCompleted = 1,
    zone = zone,
    today = today,
    locale = Locale.UK,
  )

  @Test
  fun `a streak that ended yesterday is still the current streak`() {
    // The user listened for five days straight but has not pressed play yet today. Before the fix
    // the count-back started at today, found nothing, and reported no streak at all every morning.
    val sessions = (1..5).map { session(today.minusDays(it.toLong()), startHour = 20, durationMinutes = 30) }
    val result = stats(sessions)
    result.currentStreak shouldBe 5
    result.longestStreak shouldBe 5
  }

  @Test
  fun `listening today extends the streak`() {
    val sessions = (0..2).map { session(today.minusDays(it.toLong()), startHour = 20, durationMinutes = 30) }
    stats(sessions).currentStreak shouldBe 3
  }

  @Test
  fun `a gap of two days breaks the current streak but keeps the longest`() {
    val sessions = (2..5).map { session(today.minusDays(it.toLong()), startHour = 20, durationMinutes = 30) }
    val result = stats(sessions)
    result.currentStreak shouldBe 0
    result.longestStreak shouldBe 4
  }

  @Test
  fun `a session crossing midnight is split across both days`() {
    // 23:30 -> 00:30. Bedtime listening is the app's core use case; billing the whole hour to the
    // previous day left "Today" at zero and stopped the new day counting toward a streak.
    val sessions = listOf(session(today.minusDays(1), startHour = 23, startMinute = 30, durationMinutes = 60))
    val result = stats(sessions)

    result.todayMs shouldBe 30 * 60_000L
    result.totalLifetimeMs shouldBe 60 * 60_000L
    result.currentStreak shouldBe 2
    // The split parts still add up to exactly what was recorded.
    dailyTotals(sessions, zone).values.sum() shouldBe 60 * 60_000L
  }

  @Test
  fun `a session inside one day is billed whole to that day`() {
    val sessions = listOf(session(today, startHour = 9, durationMinutes = 45))
    val result = stats(sessions)
    result.todayMs shouldBe 45 * 60_000L
    dailyTotals(sessions, zone).keys shouldBe setOf(today)
  }

  @Test
  fun `a session spanning the spring-forward DST gap keeps its recorded duration`() {
    // Europe/Berlin skips 02:00->03:00 on 2026-03-29, so this day is 23 hours long.
    val dstDay = LocalDate.of(2026, 3, 28)
    val start = LocalDateTime.of(dstDay, java.time.LocalTime.of(23, 0)).atZone(zone).toInstant()
    val durationMs = 5L * 60 * 60 * 1000 // 5 hours of wall clock, ending 05:00 next day
    val session = ListeningSession(
      bookId = BookId("content://book"),
      chapterId = ChapterId("content://chapter"),
      startedAt = start,
      endedAt = start.plusMillis(durationMs),
      durationMs = durationMs,
      startPositionMs = 0,
      endPositionMs = durationMs,
    )
    val totals = dailyTotals(listOf(session), zone)
    totals.values.sum() shouldBe durationMs
    totals.keys shouldBe setOf(dstDay, dstDay.plusDays(1))
    // One hour before midnight, the rest after.
    totals.getValue(dstDay) shouldBe 60 * 60_000L
  }

  @Test
  fun `today, this week and this month cover the right ranges`() {
    // today is Sunday 2 Aug 2026; with a UK (Monday-start) week this week runs Mon 27 Jul - Sun 2 Aug,
    // so the week straddles the month boundary while August itself contains only today.
    val sessions = listOf(
      session(today, startHour = 10, durationMinutes = 20), // Sun 2 Aug — this week, this month
      session(today.minusDays(2), startHour = 10, durationMinutes = 30), // Fri 31 Jul — this week, last month
      session(today.minusDays(9), startHour = 10, durationMinutes = 40), // Fri 24 Jul — last week
      session(LocalDate.of(2026, 7, 15), startHour = 10, durationMinutes = 50), // earlier in July
    )
    val result = stats(sessions)

    result.todayMs shouldBe 20 * 60_000L
    result.thisWeekMs shouldBe 50 * 60_000L
    result.thisMonthMs shouldBe 20 * 60_000L
    result.totalLifetimeMs shouldBe 140 * 60_000L
  }

  @Test
  fun `average per day divides by days since the first session, not by days listened`() {
    val sessions = listOf(
      session(today.minusDays(3), startHour = 10, durationMinutes = 60),
      session(today, startHour = 10, durationMinutes = 60),
    )
    // 120 minutes over the 4 days since the first session.
    stats(sessions).avgDailyMs shouldBe 30 * 60_000L
  }

  @Test
  fun `no sessions yields an empty view state that still reports library counts`() {
    val result = stats(emptyList())
    result shouldBe ListeningStatsViewState.Empty.copy(booksInLibrary = 3, booksCompleted = 1)
    result.currentStreak shouldBe 0
    result.dailyData shouldBe emptyList()
  }

  @Test
  fun `charts cover the trailing 30 days, 12 weeks and 12 months ending today`() {
    val result = stats(listOf(session(today, startHour = 10, durationMinutes = 15)))
    result.dailyData.size shouldBe 30
    result.weeklyData.size shouldBe 12
    result.monthlyData.size shouldBe 12
    // The final daily bucket is today and carries today's listening.
    result.dailyData.last().valueMs shouldBe 15 * 60_000L
    result.dailyData.dropLast(1).sumOf { it.valueMs } shouldBe 0L
  }

  @Test
  fun `monthly buckets do not merge the same month from different years`() {
    val sessions = listOf(
      session(LocalDate.of(2025, 8, 10), startHour = 10, durationMinutes = 60),
      session(LocalDate.of(2026, 8, 1), startHour = 10, durationMinutes = 30),
    )
    val result = stats(sessions)
    // Twelve trailing months ends at Aug 2026; Aug 2025 is outside it, so only this year's 30m shows.
    result.monthlyData.last().valueMs shouldBe 30 * 60_000L
    result.thisMonthMs shouldBe 30 * 60_000L
  }

  @Test
  fun `the busiest weekday reflects the split day totals`() {
    val sessions = listOf(
      session(LocalDate.of(2026, 7, 27), startHour = 10, durationMinutes = 90), // Monday
      session(LocalDate.of(2026, 7, 28), startHour = 10, durationMinutes = 30), // Tuesday
    )
    stats(sessions).bestDayOfWeek shouldBe "Monday"
  }
}
