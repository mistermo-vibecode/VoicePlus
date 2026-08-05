package voice.core.playback.session

import android.view.InputDevice
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verifySequence
import org.junit.Test
import voice.core.playback.history.PlaybackIntentHolder
import voice.core.playback.player.VoicePlayer

class LibrarySessionCallbackTest {

  @Test
  fun `only physical alphabetic devices bypass configured headset actions`() {
    fun device(
      isVirtual: Boolean,
      keyboardType: Int,
    ) = mockk<InputDevice> {
      every { this@mockk.isVirtual } returns isVirtual
      every { this@mockk.keyboardType } returns keyboardType
    }

    device(isVirtual = true, InputDevice.KEYBOARD_TYPE_ALPHABETIC)
      .isPhysicalAlphabeticKeyboard() shouldBe false
    device(isVirtual = false, InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)
      .isPhysicalAlphabeticKeyboard() shouldBe false
    device(isVirtual = false, InputDevice.KEYBOARD_TYPE_ALPHABETIC)
      .isPhysicalAlphabeticKeyboard() shouldBe true
  }

  @Test
  fun `pause with rewind marks sleep pauses then seeks the underlying position`() {
    val player = mockk<VoicePlayer>(relaxed = true)
    every { player.pause() } just Runs
    every { player.currentPosition } returns 50_000L
    every { player.seekTo(any<Long>()) } just Runs
    val intentHolder = PlaybackIntentHolder()

    pauseWithRewind(player, intentHolder, rewindMs = 7_500L)

    intentHolder.stoppedBySleepTimer shouldBe true
    verifySequence {
      player.pause()
      player.currentPosition
      player.seekTo(42_500L)
    }
  }
}
