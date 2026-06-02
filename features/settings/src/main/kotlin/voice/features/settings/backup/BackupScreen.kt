package voice.features.settings.backup

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.logging.api.Logger
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
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
  val viewState = viewModel.viewState()
  BackupScreen(
    viewState = viewState,
    onClose = viewModel::onClose,
    onChooseFolder = viewModel::onFolderChosen,
    onRestoreNow = viewModel::restoreNow,
  )
}

@Composable
internal fun BackupScreen(
  viewState: BackupViewState,
  onClose: () -> Unit,
  onChooseFolder: (Uri) -> Unit,
  onRestoreNow: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) onChooseFolder(uri)
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
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .padding(16.dp)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      ListItem(
        headlineContent = { Text(stringResource(StringsR.string.backup_folder)) },
        supportingContent = {
          Text(viewState.folder?.toString() ?: stringResource(StringsR.string.backup_folder_none))
        },
      )
      ListItem(
        headlineContent = { Text(stringResource(StringsR.string.backup_last)) },
        supportingContent = {
          Text(viewState.lastBackup?.toString() ?: stringResource(StringsR.string.backup_last_never))
        },
      )
      OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
          try {
            pickFolder.launch(null)
          } catch (e: ActivityNotFoundException) {
            Logger.w(e, "No SAF folder picker available")
          }
        },
      ) {
        Text(stringResource(StringsR.string.backup_choose_folder))
      }
      Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = viewState.folder != null,
        onClick = onRestoreNow,
      ) {
        Text(stringResource(StringsR.string.backup_restore_now))
      }
    }
  }
}
