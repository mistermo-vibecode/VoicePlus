package voice.features.settings

import voice.core.data.MediaButtonClickAction
import java.time.LocalTime

interface SettingsListener {
  fun close()
  fun toggleDarkTheme()
  fun toggleGrid()
  fun seekAmountChanged(seconds: Int)
  fun onSeekAmountRowClick()
  fun autoRewindAmountChang(seconds: Int)
  fun onAutoRewindRowClick()
  fun onAutoSleepTimerDurationClick()
  fun dismissDialog()
  fun setAutoSleepTimer(checked: Boolean)
  fun setAutoSleepTimerStart(time: LocalTime)
  fun setAutoSleepTimerEnd(time: LocalTime)
  fun setAutoSleepTimerDuration(minutes: Int)
  fun openFolderPicker()
  fun onAppVersionClick()

  fun openListeningStats()
  fun openHiddenBooks()
  fun openLicenses()

  fun onMediaButtonDoubleClickRowClick()
  fun onMediaButtonTripleClickRowClick()
  fun setMediaButtonDoubleClickAction(action: MediaButtonClickAction)
  fun setMediaButtonTripleClickAction(action: MediaButtonClickAction)

  companion object {
    fun noop() = object : SettingsListener {
      override fun close() {}
      override fun toggleDarkTheme() {}
      override fun toggleGrid() {}
      override fun seekAmountChanged(seconds: Int) {}
      override fun onSeekAmountRowClick() {}
      override fun autoRewindAmountChang(seconds: Int) {}
      override fun onAutoRewindRowClick() {}
      override fun onAutoSleepTimerDurationClick() {}
      override fun dismissDialog() {}
      override fun setAutoSleepTimer(checked: Boolean) {}
      override fun setAutoSleepTimerStart(time: LocalTime) {}
      override fun setAutoSleepTimerEnd(time: LocalTime) {}
      override fun setAutoSleepTimerDuration(minutes: Int) {}
      override fun openFolderPicker() {}
      override fun onAppVersionClick() {}
      override fun openListeningStats() {}
      override fun openHiddenBooks() {}
      override fun openLicenses() {}

      override fun onMediaButtonDoubleClickRowClick() {}
      override fun onMediaButtonTripleClickRowClick() {}
      override fun setMediaButtonDoubleClickAction(action: MediaButtonClickAction) {}
      override fun setMediaButtonTripleClickAction(action: MediaButtonClickAction) {}
    }
  }
}
