package voice.core.sleeptimer

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

interface SleepTimer {
  val state: StateFlow<SleepTimerState>
  fun enable(mode: SleepTimerMode)
  fun disable()
  fun reset()
  fun onChapterBoundaryReached()
}

sealed interface SleepTimerState {

  data object Disabled : SleepTimerState
  sealed interface Enabled : SleepTimerState {
    data class WithEndOfChapter(val chaptersRemaining: Int) : Enabled

    @JvmInline
    value class WithDuration(val leftDuration: Duration) : Enabled
  }

  val enabled: Boolean
    get() = when (this) {
      Disabled -> false
      is Enabled -> true
    }
}

sealed interface SleepTimerMode {
  data class TimedWithDuration(val duration: Duration) : SleepTimerMode
  data object TimedWithDefault : SleepTimerMode
  data class EndOfChapter(val chapters: Int = 1) : SleepTimerMode
}
