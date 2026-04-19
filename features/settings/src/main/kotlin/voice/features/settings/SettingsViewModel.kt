package voice.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import voice.core.common.AppInfoProvider
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.GridMode
import voice.core.data.MediaButtonClickAction
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.data.store.AutoRewindAmountStore
import voice.core.data.store.DarkThemeStore
import voice.core.data.store.GridModeStore
import voice.core.data.store.MediaButtonDoubleClickHandlerStore
import voice.core.data.store.MediaButtonTripleClickHandlerStore
import voice.core.data.store.SeekTimeStore
import voice.core.data.store.SleepTimerPreferenceStore
import voice.core.featureflag.FeatureFlag
import voice.core.featureflag.FolderPickerInSettingsFeatureFlagQualifier
import voice.core.ui.DARK_THEME_SETTABLE
import voice.core.ui.GridCount
import voice.navigation.Destination
import voice.navigation.Navigator
import java.time.LocalTime
import kotlin.time.Duration.Companion.minutes

@Inject
class SettingsViewModel(
  @DarkThemeStore
  private val useDarkThemeStore: DataStore<Boolean>,
  @AutoRewindAmountStore
  private val autoRewindAmountStore: DataStore<Int>,
  @SeekTimeStore
  private val seekTimeStore: DataStore<Int>,
  private val navigator: Navigator,
  private val appInfoProvider: AppInfoProvider,
  @GridModeStore
  private val gridModeStore: DataStore<GridMode>,
  @SleepTimerPreferenceStore
  private val sleepTimerPreferenceStore: DataStore<SleepTimerPreference>,
  private val gridCount: GridCount,
  @FolderPickerInSettingsFeatureFlagQualifier
  private val folderPickerInSettingsFeatureFlag: FeatureFlag<Boolean>,
  @MediaButtonDoubleClickHandlerStore
  private val mediaButtonDoubleClickHandlerStore: DataStore<MediaButtonClickAction>,
  @MediaButtonTripleClickHandlerStore
  private val mediaButtonTripleClickHandlerStore: DataStore<MediaButtonClickAction>,
  dispatcherProvider: DispatcherProvider,
) : SettingsListener {

  private val mainScope = MainScope(dispatcherProvider)
  private val dialog = mutableStateOf<SettingsViewState.Dialog?>(null)

  @Composable
  fun viewState(): SettingsViewState {
    val useDarkTheme by remember { useDarkThemeStore.data }.collectAsState(initial = false)
    val autoRewindAmount by remember { autoRewindAmountStore.data }.collectAsState(initial = 0)
    val seekTime by remember { seekTimeStore.data }.collectAsState(initial = 0)
    val gridMode by remember { gridModeStore.data }.collectAsState(initial = GridMode.GRID)
    val autoSleepTimer by remember { sleepTimerPreferenceStore.data }.collectAsState(
      initial = SleepTimerPreference.Default,
    )
    val showFolderPickerEntry = remember {
      folderPickerInSettingsFeatureFlag.get()
    }
    val mediaButtonDoubleClickAction by remember { mediaButtonDoubleClickHandlerStore.data }.collectAsState(
      initial = MediaButtonClickAction.SKIP_FORWARD,
    )
    val mediaButtonTripleClickAction by remember { mediaButtonTripleClickHandlerStore.data }.collectAsState(
      initial = MediaButtonClickAction.SKIP_BACKWARD,
    )
    return SettingsViewState(
      useDarkTheme = useDarkTheme,
      showDarkThemePref = DARK_THEME_SETTABLE,
      seekTimeInSeconds = seekTime,
      autoRewindInSeconds = autoRewindAmount,
      dialog = dialog.value,
      appVersion = appInfoProvider.versionName,
      useGrid = when (gridMode) {
        GridMode.LIST -> false
        GridMode.GRID -> true
        GridMode.FOLLOW_DEVICE -> gridCount.useGridAsDefault()
      },
      autoSleepTimer = SettingsViewState.AutoSleepTimerViewState(
        enabled = autoSleepTimer.autoSleepTimerEnabled,
        startTime = autoSleepTimer.autoSleepStartTime,
        endTime = autoSleepTimer.autoSleepEndTime,
        duration = autoSleepTimer.duration,
      ),
      showFolderPickerEntry = showFolderPickerEntry,
      mediaButtonDoubleClickAction = mediaButtonDoubleClickAction,
      mediaButtonTripleClickAction = mediaButtonTripleClickAction,
    )
  }

  override fun close() {
    navigator.goBack()
  }

  override fun toggleDarkTheme() {
    mainScope.launch {
      useDarkThemeStore.updateData { !it }
    }
  }

  override fun toggleGrid() {
    mainScope.launch {
      gridModeStore.updateData { currentMode ->
        when (currentMode) {
          GridMode.LIST -> GridMode.GRID
          GridMode.GRID -> GridMode.LIST
          GridMode.FOLLOW_DEVICE -> if (gridCount.useGridAsDefault()) {
            GridMode.LIST
          } else {
            GridMode.GRID
          }
        }
      }
    }
  }

  override fun seekAmountChanged(seconds: Int) {
    mainScope.launch {
      seekTimeStore.updateData { seconds }
    }
  }

  override fun onSeekAmountRowClick() {
    dialog.value = SettingsViewState.Dialog.SeekTime
  }

  override fun autoRewindAmountChang(seconds: Int) {
    mainScope.launch {
      autoRewindAmountStore.updateData { seconds }
    }
  }

  override fun onAutoRewindRowClick() {
    dialog.value = SettingsViewState.Dialog.AutoRewindAmount
  }

  override fun onAutoSleepTimerDurationClick() {
    dialog.value = SettingsViewState.Dialog.AutoSleepTimerDuration
  }

  override fun dismissDialog() {
    dialog.value = null
  }

  override fun openFolderPicker() {
    navigator.goTo(Destination.FolderPicker)
  }

  override fun setAutoSleepTimer(checked: Boolean) {
    mainScope.launch {
      sleepTimerPreferenceStore.updateData { currentPrefs ->
        currentPrefs.copy(autoSleepTimerEnabled = checked)
      }
    }
  }

  override fun setAutoSleepTimerStart(time: LocalTime) {
    mainScope.launch {
      sleepTimerPreferenceStore.updateData { currentPrefs ->
        currentPrefs.copy(autoSleepStartTime = time)
      }
    }
  }

  override fun setAutoSleepTimerEnd(time: LocalTime) {
    mainScope.launch {
      sleepTimerPreferenceStore.updateData { currentPrefs ->
        currentPrefs.copy(autoSleepEndTime = time)
      }
    }
  }

  override fun setAutoSleepTimerDuration(minutes: Int) {
    mainScope.launch {
      sleepTimerPreferenceStore.updateData { currentPrefs ->
        currentPrefs.copy(duration = minutes.minutes)
      }
    }
  }

  override fun onAppVersionClick() {}

  override fun openListeningStats() {
    navigator.goTo(Destination.ListeningStatistics)
  }

  override fun openHiddenBooks() {
    navigator.goTo(Destination.HiddenBooks)
  }

  override fun openLicenses() {
    navigator.goTo(Destination.OpenSourceLicenses)
  }

  override fun onMediaButtonDoubleClickRowClick() {
    dialog.value = SettingsViewState.Dialog.MediaButtonDoubleClickAction
  }

  override fun onMediaButtonTripleClickRowClick() {
    dialog.value = SettingsViewState.Dialog.MediaButtonTripleAction
  }

  override fun setMediaButtonDoubleClickAction(action: MediaButtonClickAction) {
    mainScope.launch {
      mediaButtonDoubleClickHandlerStore.updateData { action }
    }
  }

  override fun setMediaButtonTripleClickAction(action: MediaButtonClickAction) {
    mainScope.launch {
      mediaButtonTripleClickHandlerStore.updateData { action }
    }
  }
}
