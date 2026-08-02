package voice.core.sleeptimer.impl

import io.kotest.matchers.collections.shouldBeStrictlyDecreasing
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldEndWith
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.BeforeClass
import org.junit.Test
import voice.core.common.DispatcherProvider
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.logging.api.LogWriter
import voice.core.logging.api.Logger
import voice.core.playback.PlayerController
import voice.core.playback.playstate.PlayStateManager
import voice.core.sleeptimer.ShakeDetector
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerImpl
import voice.core.sleeptimer.SleepTimerMode
import voice.core.sleeptimer.SleepTimerState
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private class TestShakeDetector : ShakeDetector {
  private val shakes = Channel<Unit>(capacity = Channel.UNLIMITED)
  override suspend fun detect() {
    shakes.receive()
  }

  fun shake() {
    shakes.trySend(Unit)
  }
}

class SleepTimerImplTest {

  private val playStateManager = PlayStateManager().apply {
    playState = PlayStateManager.PlayState.Playing
  }
  private val shakeDetector = TestShakeDetector()
  private val sleepTimerPreferenceStore = MemoryDataStore(SleepTimerPreference.Default)
  private val setVolumeSlots = mutableListOf<Float>()
  private val playerController = mockk<PlayerController> {
    every { setVolume(capture(setVolumeSlots)) } just Runs
    every { pauseWithRewind(any()) } answers {
      playStateManager.playState = PlayStateManager.PlayState.Paused
    }
    every {
      play()
    } answers {
      playStateManager.playState = PlayStateManager.PlayState.Playing
    }
  }

  private val fadeOutStore = MemoryDataStore(2.seconds)
  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)

  private val sleepTimer: SleepTimer

  init {
    val dispatcherProvider = DispatcherProvider(testDispatcher, testDispatcher, testDispatcher)
    sleepTimer = SleepTimerImpl(
      playStateManager,
      shakeDetector,
      sleepTimerPreferenceStore,
      playerController,
      fadeOutStore,
      dispatcherProvider,
    )
  }

  @Test
  fun `initial state is disabled`() {
    sleepTimer.state.value shouldBe SleepTimerState.Disabled
  }

  @Test
  fun `enable with fixed duration eventually disables and pauses playback`() = testScope.runTest {
    sleepTimer.enable(SleepTimerMode.TimedWithDuration(1.seconds))

    advanceTimeBy(2.seconds)
    sleepTimer.state.value shouldBe SleepTimerState.Disabled
    coVerify(exactly = 1) { playerController.pauseWithRewind(any()) }
  }

  @Test
  fun `enable with EndOfChapter sets state`() = testScope.runTest {
    sleepTimer.enable(SleepTimerMode.EndOfChapter())

    advanceTimeBy(1)
    sleepTimer.state.value shouldBe SleepTimerState.Enabled.WithEndOfChapter(1)
  }

  @Test
  fun `disable cancels timer and resets state`() = testScope.runTest {
    sleepTimer.enable(SleepTimerMode.TimedWithDuration(5.seconds))
    advanceTimeBy(1.seconds)

    sleepTimer.disable()

    sleepTimer.state.value shouldBe SleepTimerState.Disabled
  }

  @Test
  fun `reset is no-op when autoResetEnabled is false`() = testScope.runTest {
    sleepTimerPreferenceStore.updateData { it.copy(autoResetEnabled = false) }
    sleepTimer.enable(SleepTimerMode.TimedWithDuration(10.seconds))
    advanceTimeBy(3.seconds)

    val stateBefore = sleepTimer.state.value
    stateBefore.shouldBeInstanceOf<SleepTimerState.Enabled.WithDuration>()

    sleepTimer.reset()
    advanceTimeBy(1.seconds)

    val stateAfter = sleepTimer.state.value
    stateAfter.shouldBeInstanceOf<SleepTimerState.Enabled.WithDuration>()
    // countdown continued — remaining time decreased, it was NOT reset to full
    (stateAfter.leftDuration < stateBefore.leftDuration) shouldBe true
  }

  @Test
  fun `reset restarts countdown when autoResetEnabled is true`() = testScope.runTest {
    sleepTimerPreferenceStore.updateData { it.copy(autoResetEnabled = true) }
    sleepTimer.enable(SleepTimerMode.TimedWithDuration(10.seconds))
    advanceTimeBy(3.seconds)

    val stateBefore = sleepTimer.state.value
    stateBefore.shouldBeInstanceOf<SleepTimerState.Enabled.WithDuration>()

    sleepTimer.reset()
    runCurrent() // drain the reset coroutine so enable() fires before advancing time
    advanceTimeBy(1.seconds)

    val stateAfter = sleepTimer.state.value
    stateAfter.shouldBeInstanceOf<SleepTimerState.Enabled.WithDuration>()
    // countdown restarted — remaining time is greater than it was before reset
    (stateAfter.leftDuration > stateBefore.leftDuration) shouldBe true
  }

  @Test
  fun withDurationResetsVolume() = testScope.runTest {
    sleepTimer.enable(SleepTimerMode.TimedWithDuration(5.seconds))
    advanceTimeBy(3.seconds)
    yield()

    // after the first 3 seconds, the volume should not have been decreased
    setVolumeSlots.shouldContainOnly(1F)

    setVolumeSlots.clear()
    advanceTimeBy(1.seconds)
    yield()
    // now we're in fade-out phase, volume should decrease
    setVolumeSlots.shouldNotBeEmpty()
      .shouldBeStrictlyDecreasing()

    // after the timer finished, volume should be reset
    setVolumeSlots.clear()
    advanceTimeBy(2.seconds)
    yield()
    setVolumeSlots.shouldEndWith(1f)
  }

  @Test
  fun shake_does_not_cancel_second_countdown_after_window() = testScope.runTest {
    // Use a LONG duration so we can observe behavior across the 30s window
    val longDuration = SleepTimerImpl.SHAKE_TO_RESET_TIME * 2

    sleepTimer.enable(SleepTimerMode.TimedWithDuration(longDuration))

    // 1) Let the first countdown finish and enter the shake window
    advanceTimeBy(longDuration + 1.seconds)
    runCurrent()
    coVerify(exactly = 1) { playerController.pauseWithRewind(any()) }
    sleepTimer.state.value shouldBe SleepTimerState.Disabled

    // 2) Trigger the shake → a new countdown should start independently of the old timeout
    shakeDetector.shake()
    runCurrent()
    verify(exactly = 1) { playerController.play() }
    sleepTimer.state.value shouldBe SleepTimerState.Enabled.WithDuration(longDuration)

    // 3) Advance past the original 30s shake window and allow the second countdown to finish
    advanceTimeBy(SleepTimerImpl.SHAKE_TO_RESET_TIME + longDuration + 2.seconds)
    runCurrent()

    // The second countdown should complete normally
    coVerify(exactly = 2) { playerController.pauseWithRewind(any()) }
    sleepTimer.state.value shouldBe SleepTimerState.Disabled
  }

  @Test
  fun `each chapter boundary decrements the count, and the last one disables the timer`() = testScope.runTest {
    sleepTimer.enable(SleepTimerMode.EndOfChapter(chapters = 3))
    advanceTimeBy(1)

    sleepTimer.onChapterBoundaryReached("0:1000")
    sleepTimer.state.value shouldBe SleepTimerState.Enabled.WithEndOfChapter(2)

    sleepTimer.onChapterBoundaryReached("1:2000")
    sleepTimer.state.value shouldBe SleepTimerState.Enabled.WithEndOfChapter(1)

    sleepTimer.onChapterBoundaryReached("2:3000")
    sleepTimer.state.value shouldBe SleepTimerState.Disabled
  }

  @Test
  fun `re-crossing a boundary already counted does not decrement again`() = testScope.runTest {
    // Media3 re-delivers a boundary message whenever playback reaches it again, so skipping back
    // over a chapter mark you already passed used to burn a second chapter and stop playback early.
    sleepTimer.enable(SleepTimerMode.EndOfChapter(chapters = 2))
    advanceTimeBy(1)

    sleepTimer.onChapterBoundaryReached("0:1000")
    sleepTimer.state.value shouldBe SleepTimerState.Enabled.WithEndOfChapter(1)

    sleepTimer.onChapterBoundaryReached("0:1000")
    sleepTimer.state.value shouldBe SleepTimerState.Enabled.WithEndOfChapter(1)

    // A different boundary still counts.
    sleepTimer.onChapterBoundaryReached("1:2000")
    sleepTimer.state.value shouldBe SleepTimerState.Disabled
  }

  @Test
  fun `arming a new chapter timer counts the same boundary again`() = testScope.runTest {
    sleepTimer.enable(SleepTimerMode.EndOfChapter(chapters = 1))
    advanceTimeBy(1)
    sleepTimer.onChapterBoundaryReached("0:1000")
    sleepTimer.state.value shouldBe SleepTimerState.Disabled

    sleepTimer.enable(SleepTimerMode.EndOfChapter(chapters = 1))
    advanceTimeBy(1)
    sleepTimer.onChapterBoundaryReached("0:1000")
    sleepTimer.state.value shouldBe SleepTimerState.Disabled
  }

  @Test
  fun `a boundary reached while no timer is running does nothing`() = testScope.runTest {
    sleepTimer.onChapterBoundaryReached("0:1000")
    sleepTimer.state.value shouldBe SleepTimerState.Disabled
  }

  @Test
  fun `a countdown left paused for too long disarms instead of firing on the next session`() = testScope.runTest {
    sleepTimer.enable(SleepTimerMode.TimedWithDuration(20.minutes))
    advanceTimeBy(1.minutes)

    // User pauses and stops listening for the night.
    playStateManager.playState = PlayStateManager.PlayState.Paused
    advanceTimeBy(SleepTimerImpl.STALE_PAUSE_TIMEOUT + 1.minutes)

    sleepTimer.state.value shouldBe SleepTimerState.Disabled

    // Pressing play the next morning must not resurrect it.
    playStateManager.playState = PlayStateManager.PlayState.Playing
    advanceTimeBy(25.minutes)
    sleepTimer.state.value shouldBe SleepTimerState.Disabled
    coVerify(exactly = 0) { playerController.pauseWithRewind(any()) }
  }

  @Test
  fun `a chapter timer disarms when playback stops for good`() = testScope.runTest {
    // The book ends before the next chapter boundary arrives, so the timer would otherwise stay
    // armed forever — firing in a later book and blocking the automatic bedtime timer meanwhile.
    sleepTimer.enable(SleepTimerMode.EndOfChapter(chapters = 2))
    advanceTimeBy(1)
    sleepTimer.state.value shouldBe SleepTimerState.Enabled.WithEndOfChapter(2)

    playStateManager.playState = PlayStateManager.PlayState.Paused
    advanceTimeBy(SleepTimerImpl.STALE_PAUSE_TIMEOUT + 1.minutes)

    sleepTimer.state.value shouldBe SleepTimerState.Disabled
  }

  @Test
  fun `a short pause keeps a chapter timer armed`() = testScope.runTest {
    sleepTimer.enable(SleepTimerMode.EndOfChapter(chapters = 2))
    advanceTimeBy(1)

    playStateManager.playState = PlayStateManager.PlayState.Paused
    advanceTimeBy(5.minutes)
    playStateManager.playState = PlayStateManager.PlayState.Playing
    advanceTimeBy(1)

    sleepTimer.state.value shouldBe SleepTimerState.Enabled.WithEndOfChapter(2)
  }

  @Test
  fun `reset does not re-arm a chapter timer to its original count`() = testScope.runTest {
    // Skipping back 30s calls reset(). For a duration that means "start the countdown again", but
    // for chapters it used to restore the ORIGINAL count — silently buying back chapters that had
    // already elapsed.
    sleepTimerPreferenceStore.updateData { it.copy(autoResetEnabled = true) }
    sleepTimer.enable(SleepTimerMode.EndOfChapter(chapters = 3))
    advanceTimeBy(1)
    sleepTimer.onChapterBoundaryReached("0:1000")
    sleepTimer.onChapterBoundaryReached("1:2000")
    sleepTimer.state.value shouldBe SleepTimerState.Enabled.WithEndOfChapter(1)

    sleepTimer.reset()
    advanceTimeBy(1.seconds)

    sleepTimer.state.value shouldBe SleepTimerState.Enabled.WithEndOfChapter(1)
  }

  @Test
  fun `resuming after a pause restarts the countdown when auto-reset is on`() = testScope.runTest {
    sleepTimerPreferenceStore.updateData { it.copy(autoResetEnabled = true) }
    sleepTimer.enable(SleepTimerMode.TimedWithDuration(10.seconds))
    advanceTimeBy(4.seconds)
    val beforePause = sleepTimer.state.value
    beforePause.shouldBeInstanceOf<SleepTimerState.Enabled.WithDuration>()

    playStateManager.playState = PlayStateManager.PlayState.Paused
    advanceTimeBy(2.seconds)
    playStateManager.playState = PlayStateManager.PlayState.Playing
    advanceTimeBy(1)

    val afterResume = sleepTimer.state.value
    afterResume.shouldBeInstanceOf<SleepTimerState.Enabled.WithDuration>()
    (afterResume.leftDuration > beforePause.leftDuration) shouldBe true
  }

  companion object {

    @BeforeClass
    @JvmStatic
    fun setup() {
      Logger.install(
        object : LogWriter {
          override fun log(
            severity: Logger.Severity,
            message: String,
            throwable: Throwable?,
          ) {
            println(
              buildString {
                append("${severity.name}: ")
                append(message)
                if (throwable != null) {
                  append(", $throwable")
                }
              },
            )
          }
        },
      )
    }
  }
}
