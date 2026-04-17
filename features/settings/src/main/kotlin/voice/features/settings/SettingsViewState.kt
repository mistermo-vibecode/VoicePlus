package voice.features.settings

import voice.core.data.MediaButtonClickAction
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class SettingsViewState(
  val useDarkTheme: Boolean,
  val showDarkThemePref: Boolean,
  val seekTimeInSeconds: Int,
  val autoRewindInSeconds: Int,
  val appVersion: String,
  val dialog: Dialog?,
  val useGrid: Boolean,
  val autoSleepTimer: AutoSleepTimerViewState,
  val showFolderPickerEntry: Boolean,
  val mediaButtonDoubleClickAction: MediaButtonClickAction,
  val mediaButtonTripleClickAction: MediaButtonClickAction,
) {

  enum class Dialog {
    AutoRewindAmount,
    SeekTime,
    AutoSleepTimerDuration,
    MediaButtonDoubleClickAction,
    MediaButtonTripleAction,
  }

  companion object {
    fun preview(): SettingsViewState {
      return SettingsViewState(
        useDarkTheme = false,
        showDarkThemePref = true,
        seekTimeInSeconds = 42,
        autoRewindInSeconds = 12,
        dialog = null,
        appVersion = "1.2.3",
        useGrid = true,
        autoSleepTimer = AutoSleepTimerViewState.preview(),
        showFolderPickerEntry = false,
        mediaButtonDoubleClickAction = MediaButtonClickAction.SKIP_FORWARD,
        mediaButtonTripleClickAction = MediaButtonClickAction.SKIP_BACKWARD,
      )
    }
  }

  data class AutoSleepTimerViewState(
    val enabled: Boolean,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val duration: Duration,
  ) {
    companion object {
      fun preview(): AutoSleepTimerViewState {
        return AutoSleepTimerViewState(
          enabled = false,
          startTime = LocalTime.of(22, 0),
          endTime = LocalTime.of(6, 0),
          duration = 20.minutes,
        )
      }
    }
  }
}
