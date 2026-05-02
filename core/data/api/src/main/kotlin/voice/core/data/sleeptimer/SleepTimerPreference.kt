package voice.core.data.sleeptimer

import kotlinx.serialization.Serializable
import voice.core.common.serialization.LocalTimeSerializer
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Serializable
public data class SleepTimerPreference(
  /**
   * The custom sleep time duration
   */
  val duration: Duration,
  /**
   * If the sleep timer should be automatically enabled between [autoSleepStartTime] and [autoSleepEndTime]
   */
  val autoSleepTimerEnabled: Boolean,
  @Serializable(with = LocalTimeSerializer::class)
  val autoSleepStartTime: LocalTime,
  @Serializable(with = LocalTimeSerializer::class)
  val autoSleepEndTime: LocalTime,
  /**
   * Number of chapters for "End of Chapter" mode
   */
  val chaptersCount: Int = 1,
  /**
   * If true, the running sleep timer resets when the user changes volume or resumes after pausing.
   */
  val autoResetEnabled: Boolean = true,
) {

  public companion object {
    public val Default: SleepTimerPreference = SleepTimerPreference(
      autoSleepTimerEnabled = false,
      autoSleepStartTime = LocalTime.of(22, 0),
      autoSleepEndTime = LocalTime.of(6, 0),
      duration = 20.minutes,
      chaptersCount = 1,
      autoResetEnabled = true,
    )
  }
}
