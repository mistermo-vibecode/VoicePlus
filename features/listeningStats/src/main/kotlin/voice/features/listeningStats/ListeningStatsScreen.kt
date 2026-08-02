package voice.features.listeningStats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.data.BookId
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
  val viewModel = retain { rootGraphAs<ListeningStatsGraph>().listeningStatsViewModel }
  // Null until the first aggregation lands: rendering Empty here would flash "No listening data yet"
  // at a user who has years of it.
  val viewState = viewModel.viewState() ?: return
  ListeningStatsScreen(
    viewState = viewState,
    onClose = viewModel::onClose,
    onBookClick = viewModel::onBookClick,
  )
}

private enum class StatsRange {
  Daily,
  Weekly,
  Monthly,
  AllTime,
}

@Composable
internal fun ListeningStatsScreen(
  viewState: ListeningStatsViewState,
  onClose: () -> Unit,
  onBookClick: (BookId) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedRange by remember { mutableStateOf(StatsRange.Weekly) }

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

    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        RangeSelector(
          selectedRange = selectedRange,
          onRangeSelect = { selectedRange = it },
        )

        if (viewState.totalLifetimeMs > 0L) {
          RangeSummary(viewState, selectedRange)
          ListeningActivity(
            viewState = viewState,
            selectedRange = selectedRange,
          )
          HighlightsSection(viewState)
          viewState.topBook?.let { topBook ->
            MostListenedBook(
              topBook = topBook,
              onClick = { onBookClick(topBook.bookId) },
            )
          }
          AdditionalStats(viewState)
        }

        if (viewState.booksInLibrary > 0) {
          LibraryProgress(viewState)
        }

        if (viewState.totalLifetimeMs > 0L) {
          viewState.firstListeningDateLabel?.let { date ->
            Text(
              text = stringResource(StringsR.string.listening_stats_since, date),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth(),
              textAlign = TextAlign.Center,
            )
          }
        }

        Spacer(modifier = Modifier.size(12.dp))
      }
    }
  }
}

@Composable
private fun RangeSelector(
  selectedRange: StatsRange,
  onRangeSelect: (StatsRange) -> Unit,
) {
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    StatsRange.entries.forEachIndexed { index, range ->
      SegmentedButton(
        selected = selectedRange == range,
        onClick = { onRangeSelect(range) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = StatsRange.entries.size),
        label = {
          Text(
            text = range.label(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
      )
    }
  }
}

@Composable
private fun RangeSummary(
  viewState: ListeningStatsViewState,
  selectedRange: StatsRange,
) {
  val label = when (selectedRange) {
    StatsRange.Daily -> stringResource(StringsR.string.listening_stats_today)
    StatsRange.Weekly -> stringResource(StringsR.string.listening_stats_this_week)
    StatsRange.Monthly -> stringResource(StringsR.string.listening_stats_this_month)
    StatsRange.AllTime -> stringResource(StringsR.string.listening_stats_total_lifetime)
  }
  val total = when (selectedRange) {
    StatsRange.Daily -> viewState.todayMs
    StatsRange.Weekly -> viewState.thisWeekMs
    StatsRange.Monthly -> viewState.thisMonthMs
    StatsRange.AllTime -> viewState.totalLifetimeMs
  }

  Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = formatDuration(total),
      style = MaterialTheme.typography.displaySmall,
      modifier = Modifier.padding(top = 2.dp),
    )
    if (selectedRange == StatsRange.Weekly) {
      viewState.weekChangePercent?.let { change ->
        Text(
          text = weekChangeLabel(change),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }
  }
}

@Composable
private fun ListeningActivity(
  viewState: ListeningStatsViewState,
  selectedRange: StatsRange,
) {
  val data = when (selectedRange) {
    StatsRange.Daily -> viewState.dailyData.takeLast(7)
    StatsRange.Weekly -> viewState.weeklyData.takeLast(6)
    StatsRange.Monthly -> viewState.monthlyData.takeLast(6)
    StatsRange.AllTime -> viewState.monthlyData
  }
  var selectedIndex by remember(selectedRange, data) { mutableIntStateOf(data.lastIndex.coerceAtLeast(0)) }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
    shape = MaterialTheme.shapes.extraLarge,
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      if (data.isNotEmpty()) {
        val selectedPoint = data[selectedIndex]
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (selectedRange == StatsRange.AllTime) {
              stringResource(StringsR.string.listening_stats_last_12_months)
            } else {
              stringResource(StringsR.string.listening_stats_activity)
            },
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = stringResource(
              StringsR.string.listening_stats_selected_period,
              selectedPoint.label,
              formatDuration(selectedPoint.valueMs),
            ),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp),
          )
        }
        InteractiveBarChart(
          data = data,
          selectedIndex = selectedIndex,
          onSelect = { selectedIndex = it },
          modifier = Modifier.padding(top = 16.dp),
        )
      } else {
        Text(
          text = stringResource(StringsR.string.listening_stats_no_data),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}

@Composable
private fun StatsRange.label(): String = when (this) {
  StatsRange.Daily -> stringResource(StringsR.string.listening_stats_daily_chart)
  StatsRange.Weekly -> stringResource(StringsR.string.listening_stats_weekly_chart)
  StatsRange.Monthly -> stringResource(StringsR.string.listening_stats_monthly_chart)
  StatsRange.AllTime -> stringResource(StringsR.string.listening_stats_all_time)
}

@Composable
private fun weekChangeLabel(change: Int): String = when {
  change > 0 -> stringResource(StringsR.string.listening_stats_week_more, change)
  change < 0 -> stringResource(StringsR.string.listening_stats_week_less, -change)
  else -> stringResource(StringsR.string.listening_stats_week_same)
}

@Composable
private fun InteractiveBarChart(
  data: List<ChartDataPoint>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val maxValue = data.maxOfOrNull { it.valueMs }?.coerceAtLeast(1L) ?: 1L

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(184.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    data.forEachIndexed { index, point ->
      val selected = index == selectedIndex
      val heightFraction by animateFloatAsState(
        targetValue = (point.valueMs.toFloat() / maxValue).coerceAtLeast(0.04f),
        animationSpec = spring(),
        label = "Listening bar height",
      )
      val description = stringResource(
        StringsR.string.listening_stats_selected_period,
        point.label,
        formatDuration(point.valueMs),
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .clickable(
            role = Role.Button,
            onClickLabel = stringResource(StringsR.string.listening_stats_select_period),
            onClick = { onSelect(index) },
          )
          .semantics(mergeDescendants = true) {
            contentDescription = description
            this.selected = selected
          },
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentAlignment = Alignment.BottomCenter,
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth(0.58f)
              .fillMaxHeight(heightFraction)
              .clip(MaterialTheme.shapes.extraLarge)
              .background(
                if (selected) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                },
              ),
          )
        }
        Text(
          text = point.label,
          style = MaterialTheme.typography.labelSmall,
          color = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }
  }
}

@Composable
private fun HighlightsSection(viewState: ListeningStatsViewState) {
  Column {
    SectionTitle(stringResource(StringsR.string.listening_stats_highlights))
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Highlight(
        value = viewState.activeDaysLast30.toString(),
        label = stringResource(StringsR.string.listening_stats_active_days_label),
        modifier = Modifier.weight(1f),
      )
      HighlightDivider()
      Highlight(
        value = formatDuration(viewState.avgSessionMs),
        label = stringResource(StringsR.string.listening_stats_avg_session),
        modifier = Modifier.weight(1f),
      )
      HighlightDivider()
      Highlight(
        value = viewState.bestDayOfWeek ?: "—",
        label = stringResource(StringsR.string.listening_stats_best_day_of_week),
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun Highlight(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(horizontal = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 2.dp),
    )
  }
}

@Composable
private fun HighlightDivider() {
  VerticalDivider(
    modifier = Modifier.height(48.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}

@Composable
private fun MostListenedBook(
  topBook: TopBookStats,
  onClick: () -> Unit,
) {
  Column {
    SectionTitle(stringResource(StringsR.string.listening_stats_most_listened))
    Card(
      onClick = onClick,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
      shape = MaterialTheme.shapes.large,
    ) {
      ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
          Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
            }
          }
        },
        headlineContent = {
          Text(
            text = topBook.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        supportingContent = {
          Text(
            text = stringResource(
              StringsR.string.listening_stats_most_listened_duration,
              formatDuration(topBook.durationMs),
            ),
          )
        },
        trailingContent = {
          Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        },
      )
    }
  }
}

@Composable
private fun LibraryProgress(viewState: ListeningStatsViewState) {
  val progress = (viewState.booksCompleted.toFloat() / viewState.booksInLibrary).coerceIn(0f, 1f)

  Column {
    SectionTitle(stringResource(StringsR.string.listening_stats_library))
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      shape = MaterialTheme.shapes.large,
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(StringsR.string.listening_stats_library_completed),
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            text = pluralStringResource(
              StringsR.plurals.listening_stats_library_progress,
              viewState.booksInLibrary,
              viewState.booksCompleted,
              viewState.booksInLibrary,
            ),
            style = MaterialTheme.typography.labelLarge,
          )
        }
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .height(8.dp)
            .clip(CircleShape),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
      }
    }
  }
}

@Composable
private fun AdditionalStats(viewState: ListeningStatsViewState) {
  Column {
    SectionTitle(stringResource(StringsR.string.listening_stats_more_insights))
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      shape = MaterialTheme.shapes.large,
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        InsightRow(stringResource(StringsR.string.listening_stats_avg_daily), formatDuration(viewState.avgDailyMs))
        InsightDivider()
        InsightRow(
          stringResource(StringsR.string.listening_stats_longest_day),
          if (viewState.longestDayMs > 0) {
            "${formatDuration(viewState.longestDayMs)} · ${viewState.longestDayLabel}"
          } else {
            "—"
          },
        )
        InsightDivider()
        InsightRow(
          stringResource(StringsR.string.listening_stats_current_streak),
          pluralStringResource(
            StringsR.plurals.listening_stats_streak_days,
            viewState.currentStreak,
            viewState.currentStreak,
          ),
        )
        InsightDivider()
        InsightRow(
          stringResource(StringsR.string.listening_stats_longest_streak),
          pluralStringResource(
            StringsR.plurals.listening_stats_streak_days,
            viewState.longestStreak,
            viewState.longestStreak,
          ),
        )
      }
    }
  }
}

@Composable
private fun InsightDivider() {
  HorizontalDivider(
    modifier = Modifier.padding(vertical = 8.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}

@Composable
private fun InsightRow(
  label: String,
  value: String,
) {
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
      style = MaterialTheme.typography.labelLarge,
      textAlign = TextAlign.End,
      modifier = Modifier.padding(start = 12.dp),
    )
  }
}

@Composable
private fun SectionTitle(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
  )
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
