package voice.core.playback.session

import android.view.InputDevice
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

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
}
