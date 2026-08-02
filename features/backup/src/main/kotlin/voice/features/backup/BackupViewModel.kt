package voice.features.backup

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import voice.core.common.RetainedViewModel
import voice.core.data.store.snapshot.BackupEntry
import voice.core.data.store.snapshot.BackupRepository
import voice.core.data.store.snapshot.BackupStatus
import voice.core.data.store.snapshot.LibrarySnapshotService
import voice.core.data.store.snapshot.RestoreSummary
import voice.core.playback.CurrentBookResolver
import voice.navigation.Navigator
import java.time.Instant

@Inject
class BackupViewModel(
  private val backupRepository: BackupRepository,
  private val librarySnapshotService: LibrarySnapshotService,
  private val currentBookResolver: CurrentBookResolver,
  private val navigator: Navigator,
) : RetainedViewModel() {
  private val saves = MutableStateFlow<List<BackupEntry>>(emptyList())

  @Composable
  fun viewState(): BackupViewState {
    val folder by remember { backupRepository.backupFolder }.collectAsState(initial = null)
    val lastBackup by remember { backupRepository.lastBackupAt }.collectAsState(initial = null)
    val lastRestore by remember { backupRepository.lastRestore }.collectAsState(initial = null)
    val status by remember { backupRepository.status }.collectAsState(initial = null)
    val busy by remember { backupRepository.busy }.collectAsState(initial = false)
    val savesList by saves.collectAsState()
    return BackupViewState(
      folder = folder,
      lastBackup = lastBackup,
      lastRestore = lastRestore,
      status = status,
      busy = busy,
      saves = savesList,
    )
  }

  fun refreshSaves() {
    scope.launch { reloadSaves() }
  }

  private suspend fun reloadSaves() {
    saves.value = backupRepository.listBackups()
  }

  fun onFolderChosen(uri: Uri) {
    scope.launch {
      backupRepository.setBackupFolder(uri)
      reloadSaves()
    }
  }

  fun clearFolder() {
    scope.launch {
      backupRepository.clearBackupFolder()
      saves.value = emptyList()
    }
  }

  fun backupNow() {
    scope.launch {
      // Capture the CURRENT library state: a change made moments ago may still be inside the
      // snapshot writer's debounce, and exporting the stale ring would silently omit it.
      currentBookResolver.persistCurrentPosition()
      librarySnapshotService.flushNow()
      backupRepository.exportNow()
      reloadSaves()
    }
  }

  fun restore(entry: BackupEntry) {
    scope.launch { backupRepository.importAndRestore(entry) }
  }

  fun deleteSave(entry: BackupEntry) {
    scope.launch {
      backupRepository.deleteBackup(entry)
      reloadSaves()
    }
  }

  fun onClose() {
    navigator.goBack()
  }
}

data class BackupViewState(
  val folder: Uri?,
  val lastBackup: Instant?,
  val lastRestore: RestoreSummary?,
  val status: BackupStatus?,
  val busy: Boolean,
  val saves: List<BackupEntry>,
)
