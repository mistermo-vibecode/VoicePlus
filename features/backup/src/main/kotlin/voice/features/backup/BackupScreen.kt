package voice.features.backup

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.data.store.snapshot.BackupEntry
import voice.core.data.store.snapshot.BackupStatus
import voice.core.data.store.snapshot.BackupStatusKind
import voice.core.logging.api.Logger
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface BackupGraph {
  val backupViewModel: BackupViewModel
}

@ContributesTo(AppScope::class)
interface BackupProvider {

  @Provides
  @IntoSet
  fun backupNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.BackupRestore> { key ->
    NavEntry(key) {
      BackupScreen()
    }
  }
}

@Composable
fun BackupScreen() {
  val viewModel = retain { rootGraphAs<BackupGraph>().backupViewModel }
  LaunchedEffect(viewModel) { viewModel.refreshSaves() }
  val viewState = viewModel.viewState()
  BackupScreen(
    viewState = viewState,
    onClose = viewModel::onClose,
    onChooseFolder = viewModel::onFolderChosen,
    onClearFolder = viewModel::clearFolder,
    onBackupNow = viewModel::backupNow,
    onRestore = viewModel::restore,
    onDeleteSave = viewModel::deleteSave,
  )
}

@Composable
internal fun BackupScreen(
  viewState: BackupViewState,
  onClose: () -> Unit,
  onChooseFolder: (Uri) -> Unit,
  onClearFolder: () -> Unit,
  onBackupNow: () -> Unit,
  onRestore: (BackupEntry) -> Unit,
  onDeleteSave: (BackupEntry) -> Unit,
  modifier: Modifier = Modifier,
) {
  val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) onChooseFolder(uri)
  }
  val launchPicker = {
    try {
      pickFolder.launch(null)
    } catch (e: ActivityNotFoundException) {
      Logger.w(e, "No SAF folder picker available")
    }
  }
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(StringsR.string.backup_title)) },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(StringsR.string.close))
          }
        },
      )
    },
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .padding(horizontal = 16.dp)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        ListItem(
          headlineContent = {
            Text(
              if (viewState.folder == null) {
                stringResource(StringsR.string.backup_folder_none)
              } else {
                stringResource(StringsR.string.backup_enabled)
              },
            )
          },
          supportingContent = {
            viewState.folder?.let { Text(it.toString()) }
          },
        )
      }
      item {
        ListItem(
          headlineContent = { Text(stringResource(StringsR.string.backup_last)) },
          supportingContent = {
            Text(viewState.lastBackup?.formatBackupTime() ?: stringResource(StringsR.string.backup_last_never))
          },
        )
      }
      viewState.status?.let { status ->
        item { Text(status.message()) }
      }
      if (viewState.busy) {
        item { Text(stringResource(StringsR.string.backup_busy)) }
      }
      if (viewState.folder == null) {
        item {
          Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewState.busy,
            onClick = { launchPicker() },
          ) {
            Text(stringResource(StringsR.string.backup_choose_folder))
          }
        }
      } else {
        item {
          Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewState.busy,
            onClick = onBackupNow,
          ) {
            Text(stringResource(StringsR.string.backup_now))
          }
        }
        item {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              modifier = Modifier.weight(1f),
              enabled = !viewState.busy,
              onClick = { launchPicker() },
            ) {
              Text(stringResource(StringsR.string.backup_change_folder))
            }
            OutlinedButton(
              modifier = Modifier.weight(1f),
              enabled = !viewState.busy,
              onClick = onClearFolder,
            ) {
              Text(stringResource(StringsR.string.backup_clear_folder))
            }
          }
        }
        item {
          Text(
            text = stringResource(StringsR.string.backup_saves),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
        if (viewState.saves.isEmpty()) {
          item { Text(stringResource(StringsR.string.backup_saves_empty)) }
        }
        items(viewState.saves, key = { it.displayName }) { save ->
          SaveRow(
            save = save,
            busy = viewState.busy,
            onRestore = { onRestore(save) },
            onDelete = { onDeleteSave(save) },
          )
        }
      }
      viewState.lastRestore?.let { restore ->
        item {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (restore.refusedNewerBackup) {
              Text(stringResource(StringsR.string.backup_restore_refused_newer))
            } else if (restore.unmatched.isNotEmpty()) {
              Text(stringResource(StringsR.string.backup_restore_unmatched_title, restore.unmatched.size))
              Text(stringResource(StringsR.string.backup_restore_unmatched_hint))
              restore.unmatched.take(5).forEach { info ->
                Text("- ${info.folderName}: ${info.reason.restoreReasonLabel()}")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SaveRow(
  save: BackupEntry,
  busy: Boolean,
  onRestore: () -> Unit,
  onDelete: () -> Unit,
) {
  ListItem(
    headlineContent = {
      Text(save.savedAt?.formatBackupTime() ?: stringResource(StringsR.string.backup_save_legacy))
    },
    supportingContent = { Text(save.displayName, style = MaterialTheme.typography.bodySmall) },
    leadingContent = if (save.manual) {
      {
        AssistChip(
          onClick = {},
          enabled = false,
          label = { Text(stringResource(StringsR.string.backup_save_manual)) },
        )
      }
    } else {
      null
    },
    trailingContent = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = !busy, onClick = onRestore) {
          Text(stringResource(StringsR.string.backup_restore_action))
        }
        IconButton(enabled = !busy, onClick = onDelete) {
          Icon(
            Icons.Outlined.Delete,
            contentDescription = stringResource(StringsR.string.backup_delete_save),
          )
        }
      }
    },
  )
}

@Composable
private fun BackupStatus.message(): String {
  return when (kind) {
    BackupStatusKind.NoBackupFound -> stringResource(StringsR.string.backup_status_no_backup_found)
    BackupStatusKind.BackupFound -> stringResource(StringsR.string.backup_status_backup_found)
    BackupStatusKind.BackupSaved -> stringResource(StringsR.string.backup_status_backup_saved)
    BackupStatusKind.BackupUnreadable -> stringResource(StringsR.string.backup_status_backup_unreadable)
    BackupStatusKind.BackupFailed -> stringResource(StringsR.string.backup_status_backup_failed)
    BackupStatusKind.RestoreComplete -> stringResource(StringsR.string.backup_status_restore_complete, restoredCount)
    BackupStatusKind.RestorePartial -> {
      stringResource(StringsR.string.backup_status_restore_partial, restoredCount, unmatchedCount)
    }
    BackupStatusKind.RestoreNoMatch -> stringResource(StringsR.string.backup_status_restore_no_match, unmatchedCount)
    BackupStatusKind.RefusedNewerBackup -> stringResource(StringsR.string.backup_restore_refused_newer)
    BackupStatusKind.PermissionDenied -> stringResource(StringsR.string.backup_status_permission_denied)
  }
}

private fun Instant.formatBackupTime(): String {
  return DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())
    .format(this)
}

@Composable
private fun String.restoreReasonLabel(): String {
  return when (this) {
    "OPAQUE_PROVIDER" -> stringResource(StringsR.string.backup_reason_opaque_provider)
    "SINGLE_FILE" -> stringResource(StringsR.string.backup_reason_single_file)
    "NO_PATH_MATCH" -> stringResource(StringsR.string.backup_reason_no_path_match)
    "AMBIGUOUS" -> stringResource(StringsR.string.backup_reason_ambiguous)
    "CONTENT_CHANGED" -> stringResource(StringsR.string.backup_reason_content_changed)
    "INVALID" -> stringResource(StringsR.string.backup_reason_invalid)
    else -> this
  }
}
