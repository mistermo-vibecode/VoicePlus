package voice.core.sleeptimer

import androidx.datastore.core.DataStore
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.data.store.FadeOutStore
import voice.core.data.store.SleepTimerPreferenceStore
import voice.core.logging.api.Logger
import voice.core.playback.PlayerController
import voice.core.playback.playstate.PlayStateManager
import voice.core.playback.playstate.PlayStateManager.PlayState.Playing
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SleepTimerImpl internal constructor(
  private val playStateManager: PlayStateManager,
  private val shakeDetector: ShakeDetector,
  @SleepTimerPreferenceStore
  private val sleepTimerPreferenceStore: DataStore<SleepTimerPreference>,
  private val playerController: PlayerController,
  @FadeOutStore
  private val fadeOutStore: DataStore<Duration>,
  dispatcherProvider: DispatcherProvider,
) : SleepTimer {

  private val scope = MainScope(dispatcherProvider)
  private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Disabled)
  override val state: StateFlow<SleepTimerState> get() = _state

  private var job: Job? = null
  private var lastMode: SleepTimerMode? = null

  /** Boundaries already counted by the CURRENT timer; cleared whenever a timer is armed or dropped. */
  private val countedBoundaries = mutableSetOf<String>()

  override fun enable(mode: SleepTimerMode) {
    disable() // cancel any active job first
    lastMode = mode

    job = scope.launch {
      when (mode) {
        is SleepTimerMode.TimedWithDuration -> startCountdown(mode.duration)
        SleepTimerMode.TimedWithDefault -> {
          val pref = sleepTimerPreferenceStore.data.first()
          startCountdown(pref.duration)
        }
        is SleepTimerMode.EndOfChapter -> {
          _state.value = SleepTimerState.Enabled.WithEndOfChapter(mode.chapters)
          // A chapter timer has no countdown to expire, so without this it stays armed forever when
          // the boundary never arrives — the book ends first, or the user just stops listening. It
          // would then fire in some later session, and meanwhile block the automatic bedtime timer,
          // which refuses to arm while a timer is active.
          disableWhenPlaybackStopsForGood()
        }
      }
    }
  }

  override fun disable() {
    job?.cancel()
    job = null
    lastMode = null
    countedBoundaries.clear()
    _state.value = SleepTimerState.Disabled
    playerController.setVolume(1F)
  }

  override fun reset() {
    val mode = lastMode ?: return
    // Only a duration can be "reset to full". Re-enabling an EndOfChapter timer would restore the
    // ORIGINAL chapter count, so skipping back 30s with 1 of 3 chapters left would silently buy
    // two more chapters. The volume observer already guards this way; doing it here covers the
    // skip-seek callers too.
    if (_state.value !is SleepTimerState.Enabled.WithDuration) return
    scope.launch {
      if (!sleepTimerPreferenceStore.data.first().autoResetEnabled) return@launch
      enable(mode)
    }
  }

  private suspend fun startCountdown(duration: Duration) {
    Logger.d("startCountdown(duration=$duration)")
    var left = duration
    _state.value = SleepTimerState.Enabled.WithDuration(left)
    playerController.setVolume(1F)

    val fadeOutDuration = fadeOutStore.data.first()
    val autoResetEnabled = sleepTimerPreferenceStore.data.first().autoResetEnabled
    var interval = 500.milliseconds

    while (left > Duration.ZERO) {
      val wasPaused = playStateManager.playState != Playing
      if (!suspendUntilPlaying()) {
        // Nothing resumed for a long time: the user paused and stopped listening. Keeping the timer
        // armed means it fires against the NEXT listening session — pause at 11pm, press play on
        // the bus next morning, and playback stops 20 minutes into the commute.
        Logger.i("Playback stayed paused for $STALE_PAUSE_TIMEOUT — disabling the sleep timer")
        disable()
        return
      }
      if (wasPaused && autoResetEnabled) {
        Logger.i("Playback resumed from pause — resetting sleep timer to $duration")
        left = duration
        _state.value = SleepTimerState.Enabled.WithDuration(left)
        playerController.setVolume(1F)
        interval = 500.milliseconds
        continue
      }
      if (left < fadeOutDuration) {
        interval = 200.milliseconds
        updateVolume(left, fadeOutDuration)
      }
      delay(interval)
      left = max((left - interval).inWholeMilliseconds, 0).milliseconds
      _state.value = SleepTimerState.Enabled.WithDuration(left)
    }
    playerController.setVolume(1f)
    _state.value = SleepTimerState.Disabled

    playerController.pauseWithRewind(fadeOutDuration)

    val shakeDetected = detectShakeWithTimeout()
    playerController.setVolume(1F)
    if (shakeDetected) {
      Logger.i("Shake detected, resetting timer")
      playerController.play()
      startCountdown(duration)
    }
  }

  private suspend fun detectShakeWithTimeout(): Boolean {
    Logger.d("Waiting $SHAKE_TO_RESET_TIME for shake...")
    return withTimeoutOrNull(SHAKE_TO_RESET_TIME) {
      shakeDetector.detect()
      true
    } ?: false
  }

  private fun updateVolume(
    left: Duration,
    fadeOutDuration: Duration,
  ) {
    val percentage = (left / fadeOutDuration).toFloat().coerceIn(0f, 1f)
    val volume = 1 - FastOutSlowInInterpolator().getInterpolation(1 - percentage)
    playerController.setVolume(volume)
  }

  private suspend fun disableWhenPlaybackStopsForGood() {
    while (true) {
      playStateManager.flow.first { it != Playing }
      if (!suspendUntilPlaying()) {
        Logger.i("Playback stayed paused for $STALE_PAUSE_TIMEOUT — disabling the chapter sleep timer")
        disable()
        return
      }
    }
  }

  /** @return false if playback did not resume within [STALE_PAUSE_TIMEOUT]. */
  private suspend fun suspendUntilPlaying(): Boolean {
    if (playStateManager.playState == Playing) return true
    Logger.i("Not playing. Waiting for playback to continue.")
    val resumed = withTimeoutOrNull(STALE_PAUSE_TIMEOUT) {
      playStateManager.flow.first { it == Playing }
    } != null
    if (resumed) Logger.i("Playback resumed.")
    return resumed
  }

  override fun onChapterBoundaryReached(boundaryId: String) {
    val current = _state.value
    if (current !is SleepTimerState.Enabled.WithEndOfChapter) return
    // Media3 boundary messages are re-delivered whenever playback reaches the position again, so
    // rewinding back over a boundary already counted would decrement a second time and end the
    // timer a whole chapter early.
    if (!countedBoundaries.add(boundaryId)) return
    val next = current.chaptersRemaining - 1
    if (next > 0) {
      _state.value = SleepTimerState.Enabled.WithEndOfChapter(next)
    } else {
      disable()
    }
  }

  internal companion object {
    val SHAKE_TO_RESET_TIME = 30.seconds

    /** Long enough that a normal interruption (door, kettle, phone call) keeps the timer armed. */
    val STALE_PAUSE_TIMEOUT = 30.minutes
  }
}
