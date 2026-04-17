package voice.features.settings.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.ui.rememberScoped
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface ListeningStatsGraph {
  val listeningStatsViewModel: ListeningStatsViewModel
}

@ContributesTo(AppScope::class)
interface ListeningStatsProvider {

  @Provides
  @IntoSet
  fun listeningStatsNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.ListeningStatistics> { key ->
    NavEntry(key) {
      ListeningStatsScreen()
    }
  }
}

@Composable
fun ListeningStatsScreen() {
  val viewModel = rememberScoped { rootGraphAs<ListeningStatsGraph>().listeningStatsViewModel }
  val viewState = viewModel.viewState()
  ListeningStatsScreen(viewState = viewState, onClose = viewModel::onClose)
}

@Composable
internal fun ListeningStatsScreen(
  viewState: ListeningStatsViewState,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(StringsR.string.listening_stats)) },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(StringsR.string.close))
          }
        },
      )
    },
  ) { paddingValues ->
    if (viewState.totalLifetimeMs == 0L && viewState.booksInLibrary == 0) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(StringsR.string.listening_stats_no_data),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      return@Scaffold
    }

    Column(
      modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(paddingValues)
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Summary Cards
      SummarySection(viewState)

      // Daily Chart
      if (viewState.dailyData.any { it.valueMs > 0 }) {
        SectionTitle(stringResource(StringsR.string.listening_stats_daily_chart))
        BarChart(data = viewState.dailyData, showEveryNthLabel = 7)
      }

      // Weekly Chart
      if (viewState.weeklyData.any { it.valueMs > 0 }) {
        SectionTitle(stringResource(StringsR.string.listening_stats_weekly_chart))
        BarChart(data = viewState.weeklyData, showEveryNthLabel = 1)
      }

      // Monthly Chart
      if (viewState.monthlyData.any { it.valueMs > 0 }) {
        SectionTitle(stringResource(StringsR.string.listening_stats_monthly_chart))
        BarChart(data = viewState.monthlyData, showEveryNthLabel = 1)
      }

      // Additional Stats
      AdditionalStatsSection(viewState)

      Spacer(modifier = Modifier.size(24.dp))
    }
  }
}

@Composable
private fun SummarySection(viewState: ListeningStatsViewState) {
  SectionTitle("Summary")
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    StatCard(
      label = stringResource(StringsR.string.listening_stats_total_lifetime),
      value = formatDuration(viewState.totalLifetimeMs),
      modifier = Modifier.weight(1f),
    )
    StatCard(
      label = stringResource(StringsR.string.listening_stats_today),
      value = formatDuration(viewState.todayMs),
      modifier = Modifier.weight(1f),
    )
  }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    StatCard(
      label = stringResource(StringsR.string.listening_stats_this_week),
      value = formatDuration(viewState.thisWeekMs),
      modifier = Modifier.weight(1f),
    )
    StatCard(
      label = stringResource(StringsR.string.listening_stats_this_month),
      value = formatDuration(viewState.thisMonthMs),
      modifier = Modifier.weight(1f),
    )
  }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    StatCard(
      label = stringResource(StringsR.string.listening_stats_books_completed),
      value = viewState.booksCompleted.toString(),
      modifier = Modifier.weight(1f),
    )
    StatCard(
      label = stringResource(StringsR.string.listening_stats_books_in_library),
      value = viewState.booksInLibrary.toString(),
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun AdditionalStatsSection(viewState: ListeningStatsViewState) {
  SectionTitle("Insights")
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      InsightRow(stringResource(StringsR.string.listening_stats_avg_daily), formatDuration(viewState.avgDailyMs))
      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
      InsightRow(
        stringResource(StringsR.string.listening_stats_longest_day),
        if (viewState.longestDayMs > 0) "${formatDuration(viewState.longestDayMs)} (${viewState.longestDayLabel})" else "—",
      )
      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
      InsightRow(
        stringResource(StringsR.string.listening_stats_current_streak),
        if (viewState.currentStreak > 0) stringResource(StringsR.string.listening_stats_streak_days, viewState.currentStreak) else "—",
      )
      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
      InsightRow(
        stringResource(StringsR.string.listening_stats_longest_streak),
        if (viewState.longestStreak > 0) stringResource(StringsR.string.listening_stats_streak_days, viewState.longestStreak) else "—",
      )
      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
      InsightRow(stringResource(StringsR.string.listening_stats_best_day_of_week), viewState.bestDayOfWeek ?: "—")
    }
  }
}

@Composable
private fun InsightRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.End,
    )
  }
}

@Composable
private fun StatCard(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun SectionTitle(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 4.dp),
  )
}

@Composable
fun BarChart(
  data: List<ChartDataPoint>,
  showEveryNthLabel: Int = 1,
  modifier: Modifier = Modifier,
) {
  val barColor = MaterialTheme.colorScheme.primary
  val zeroBarColor = MaterialTheme.colorScheme.surfaceVariant
  val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  val textMeasurer = rememberTextMeasurer()
  val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)
  val yLabelStyle = TextStyle(fontSize = 9.sp, color = labelColor)

  val maxValue = data.maxOfOrNull { it.valueMs } ?: 1L
  val safeMax = maxValue.coerceAtLeast(1L)

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(160.dp),
  ) {
    val chartTop = 8.dp.toPx()
    val labelHeight = 20.dp.toPx()
    val yLabelWidth = 52.dp.toPx()
    val chartBottom = size.height - labelHeight
    val chartHeight = chartBottom - chartTop
    val chartLeft = yLabelWidth
    val chartWidth = size.width - chartLeft

    val barSpacing = 2.dp.toPx()
    val barWidth = (chartWidth / data.size) - barSpacing

    // Y-axis grid lines and labels (3 levels: 25%, 50%, 75%, 100%)
    val levels = listOf(0.25f, 0.5f, 0.75f, 1.0f)
    for (level in levels) {
      val y = chartBottom - (chartHeight * level)
      drawLine(
        color = gridColor,
        start = Offset(chartLeft, y),
        end = Offset(size.width, y),
        strokeWidth = 0.5.dp.toPx(),
      )
      val timeLabel = formatDurationShort((safeMax * level).toLong())
      drawText(
        textMeasurer = textMeasurer,
        text = timeLabel,
        topLeft = Offset(0f, y - 8.dp.toPx()),
        style = yLabelStyle,
      )
    }

    // Bars
    data.forEachIndexed { index, point ->
      val x = chartLeft + index * (barWidth + barSpacing)
      val barHeight = if (safeMax > 0) chartHeight * point.valueMs / safeMax else 0f
      val barTop = chartBottom - barHeight

      drawRoundRect(
        color = if (point.valueMs > 0) barColor else zeroBarColor,
        topLeft = Offset(x, if (point.valueMs > 0) barTop else chartBottom - 2.dp.toPx()),
        size = Size(barWidth, if (point.valueMs > 0) barHeight else 2.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx()),
      )

      // X-axis labels
      if (showEveryNthLabel == 1 || index % showEveryNthLabel == 0) {
        drawText(
          textMeasurer = textMeasurer,
          text = point.label,
          topLeft = Offset(x, chartBottom + 2.dp.toPx()),
          style = labelStyle,
        )
      }
    }
  }
}

private fun formatDurationShort(ms: Long): String {
  val hours = ms / 3_600_000
  val minutes = (ms % 3_600_000) / 60_000
  return when {
    hours > 0 -> "${hours}h"
    minutes > 0 -> "${minutes}m"
    else -> "0m"
  }
}

fun formatDuration(ms: Long): String {
  val hours = ms / 3_600_000
  val minutes = (ms % 3_600_000) / 60_000
  val seconds = (ms % 60_000) / 1_000
  return when {
    hours > 0 -> "${hours}h ${minutes}m"
    minutes > 0 -> "${minutes}m ${seconds}s"
    seconds > 0 -> "${seconds}s"
    else -> "0m"
  }
}
