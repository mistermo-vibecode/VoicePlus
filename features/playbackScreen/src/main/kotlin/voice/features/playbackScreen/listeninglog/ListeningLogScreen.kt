package voice.features.playbackScreen.listeninglog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
interface ListeningLogGraph {
  val listeningLogViewModelFactory: ListeningLogViewModel.Factory
}

@ContributesTo(AppScope::class)
interface ListeningLogProvider {

  @Provides
  @IntoSet
  fun listeningLogNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.ListeningLog> { key ->
    NavEntry(key) {
      ListeningLogScreen(bookId = key.bookId)
    }
  }
}

@Composable
fun ListeningLogScreen(bookId: BookId) {
  val viewModel = retain(bookId.value) {
    rootGraphAs<ListeningLogGraph>().listeningLogViewModelFactory.create(bookId)
  }
  val viewState = viewModel.viewState()
  ListeningLogScreen(
    viewState = viewState,
    onClose = viewModel::onClose,
    onEntryClick = viewModel::onEntryClick,
    onClearHistory = viewModel::clearHistory,
  )
}

@Composable
internal fun ListeningLogScreen(
  viewState: ListeningLogViewState,
  onClose: () -> Unit,
  onEntryClick: (ListeningLogEntry) -> Unit,
  onClearHistory: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showClearDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = viewState.bookTitle.ifBlank { stringResource(StringsR.string.listening_log) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(StringsR.string.close))
          }
        },
        actions = {
          if (viewState.groups.isNotEmpty()) {
            TextButton(onClick = { showClearDialog = true }) {
              Text(stringResource(StringsR.string.listening_log_clear_all))
            }
          }
        },
      )
    },
  ) { paddingValues ->
    if (viewState.groups.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(StringsR.string.listening_log_empty),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyColumn(contentPadding = paddingValues) {
        viewState.groups.forEach { group ->
          item(key = group.dateLabel) {
            Text(
              text = group.dateLabel,
              modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          items(items = group.entries, key = { it.id }) { entry ->
            EventCard(
              entry = entry,
              onClick = { onEntryClick(entry) },
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
          }
          item(key = "${group.dateLabel}_spacer") {
            Spacer(modifier = Modifier.size(8.dp))
          }
        }
        item {
          Spacer(modifier = Modifier.size(16.dp))
        }
      }
    }
  }

  if (showClearDialog) {
    AlertDialog(
      onDismissRequest = { showClearDialog = false },
      title = { Text(stringResource(StringsR.string.listening_log_clear_history)) },
      text = { Text(stringResource(StringsR.string.dialog_confirm)) },
      confirmButton = {
        TextButton(onClick = {
          showClearDialog = false
          onClearHistory()
        }) {
          Text(stringResource(StringsR.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearDialog = false }) {
          Text(stringResource(StringsR.string.dialog_cancel))
        }
      },
    )
  }
}

@Composable
private fun EventCard(
  entry: ListeningLogEntry,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    tonalElevation = 2.dp,
  ) {
    Column(
      modifier = Modifier
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      // Headline row: icon + event label
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        when (entry) {
          is ListeningLogEntry.Play -> {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = stringResource(StringsR.string.play),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
          }
          is ListeningLogEntry.Pause -> {
            Icon(
              imageVector = Icons.Default.Pause,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = stringResource(StringsR.string.pause),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
          }
          is ListeningLogEntry.Skip -> {
            Icon(
              imageVector = Icons.Default.FastForward,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.tertiary,
            )
            Text(
              text = stringResource(StringsR.string.listening_log_event_skip),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      // Chapter name
      Text(
        text = entry.chapterName,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      // Bottom row: wall-clock time (left) + position / remaining (right)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val timeLabel = when (entry) {
          is ListeningLogEntry.Play -> entry.timeLabel
          is ListeningLogEntry.Pause -> entry.timeLabel
          is ListeningLogEntry.Skip -> ""
        }
        Text(
          text = timeLabel,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = entry.positionLabel,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
          )
          Text(
            text = entry.remainingLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
