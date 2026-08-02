package voice.core.scanner

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import voice.core.common.DispatcherProvider
import voice.core.data.folders.FolderType
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
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

  /**
   * The onboarding regression: a trivial scan with NO folders completes and arms the cooldown, the
   * user then adds their audiobook folder, and the library-entry scan must NOT be skipped — the
   * cooldown only applies while the folder configuration is unchanged.
   */
  @Test
  fun `entry scan is skipped only while the folder set is unchanged`() = runTest {
    val folders = FakeManagedFolders()
    val scanner = mockk<MediaScanner> { coEvery { scan(any(), any()) } just Runs }
    val trigger = MediaScanTrigger(
      audiobookFolders = folders,
      scanner = scanner,
      coverScanner = mockk(relaxed = true),
      bookRepo = mockk { coEvery { all() } returns emptyList() },
      documentFileFactory = mockk(relaxed = true),
      dispatcherProvider = DispatcherProvider(coroutineContext, coroutineContext, coroutineContext),
    )
    trigger.clock = { now }

    // App start with no folders configured: the scan completes trivially and arms the cooldown.
    trigger.scan()
    advanceUntilIdle()
    coVerify(exactly = 1) { scanner.scan(any(), any()) }

    // Within the window and folders unchanged -> skipped.
    trigger.scan(skipIfCompletedWithin = 5.minutes)
    advanceUntilIdle()
    coVerify(exactly = 1) { scanner.scan(any(), any()) }

    // Onboarding adds a folder. The next entry scan must run despite the window.
    folders.add("file:///audiobooks".toUri(), FolderType.Root)
    trigger.scan(skipIfCompletedWithin = 5.minutes)
    advanceUntilIdle()
    coVerify(exactly = 2) { scanner.scan(any(), any()) }

    // Same (non-empty) set again -> skipped again.
    trigger.scan(skipIfCompletedWithin = 5.minutes)
    advanceUntilIdle()
    coVerify(exactly = 2) { scanner.scan(any(), any()) }

    // Removing the folder is a configuration change too -> scans.
    folders.remove("file:///audiobooks".toUri(), FolderType.Root)
    trigger.scan(skipIfCompletedWithin = 5.minutes)
    advanceUntilIdle()
    coVerify(exactly = 3) { scanner.scan(any(), any()) }
  }
}
