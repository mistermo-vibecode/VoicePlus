package voice.features.settings.backup

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import voice.core.data.store.snapshot.BackupRepository
import voice.navigation.Navigator
import java.time.Instant

@Inject
class BackupViewModel(
  private val backupRepository: BackupRepository,
  private val navigator: Navigator,
) {

  private val scope = MainScope()

  @Composable
  fun viewState(): BackupViewState {
    val folder by remember { backupRepository.backupFolder }.collectAsState(initial = null)
    val lastBackup by remember { backupRepository.lastBackupAt }.collectAsState(initial = null)
    return BackupViewState(folder = folder, lastBackup = lastBackup)
  }

  fun onFolderChosen(uri: Uri) {
    scope.launch { backupRepository.setBackupFolder(uri) }
  }

  fun restoreNow() {
    scope.launch { backupRepository.importAndRestore() }
  }

  fun onClose() {
    navigator.goBack()
  }
}

data class BackupViewState(
  val folder: Uri?,
  val lastBackup: Instant?,
)
