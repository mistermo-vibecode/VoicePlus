package voice.core.scanner

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

class ScanCooldownTest {

  private val now = Instant.ofEpochMilli(10_000_000)

  @Test
  fun `never fresh before the first completed scan`() {
    isFresh(lastCompletedAt = null, now = now, window = 5.minutes) shouldBe false
  }

  @Test
  fun `fresh strictly inside the window, stale at and beyond it`() {
    isFresh(now.minusMillis(5.minutes.inWholeMilliseconds - 1), now, 5.minutes) shouldBe true
    isFresh(now.minusMillis(5.minutes.inWholeMilliseconds), now, 5.minutes) shouldBe false
    isFresh(now.minusMillis(5.minutes.inWholeMilliseconds + 1), now, 5.minutes) shouldBe false
  }
}
