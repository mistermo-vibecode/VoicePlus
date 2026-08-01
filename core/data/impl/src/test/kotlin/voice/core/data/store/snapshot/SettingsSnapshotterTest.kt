package voice.core.data.store.snapshot

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import voice.core.data.GridMode
import voice.core.data.MediaButtonClickAction
import voice.core.data.repo.internals.MemoryDataStore
import voice.core.data.sleeptimer.SleepTimerPreference
import kotlin.time.Duration.Companion.seconds

/**
 * The one leg of the backup path whose silent failure mode is "every setting stops restoring":
 * Entry.apply swallows undecodable values by design, so nothing else would catch a serializer
 * change, key rename, or Json drift.
 */
class SettingsSnapshotterTest {

  private class Stores {
    val darkTheme = MemoryDataStore(false)
    val autoRewind = MemoryDataStore(2)
    val seekTime = MemoryDataStore(20)
    val fadeOut = MemoryDataStore(10.seconds)
    val sleepTimer = MemoryDataStore(SleepTimerPreference.Default)
    val gridMode = MemoryDataStore(GridMode.FOLLOW_DEVICE)
    val mediaDoubleClick = MemoryDataStore(MediaButtonClickAction.NONE)
    val mediaTripleClick = MemoryDataStore(MediaButtonClickAction.NONE)
    val experimentalPersistence = MemoryDataStore(false)
    val ignoreFileTags = MemoryDataStore(false)

    fun snapshotter() = SettingsSnapshotter(
      darkTheme = darkTheme,
      autoRewind = autoRewind,
      seekTime = seekTime,
      fadeOut = fadeOut,
      sleepTimer = sleepTimer,
      gridMode = gridMode,
      mediaDoubleClick = mediaDoubleClick,
      mediaTripleClick = mediaTripleClick,
      experimentalPersistence = experimentalPersistence,
      ignoreFileTags = ignoreFileTags,
      json = snapshotTestJson,
    )
  }

  @Test
  fun `captured settings survive apply onto fresh default stores`() = runTest {
    val source = Stores()
    source.darkTheme.updateData { true }
    source.seekTime.updateData { 45 }
    source.gridMode.updateData { GridMode.GRID }
    source.mediaDoubleClick.updateData { MediaButtonClickAction.QUICK_BOOKMARK }
    source.ignoreFileTags.updateData { true }

    val captured = source.snapshotter().capture()
    val target = Stores()
    target.snapshotter().apply(captured)

    target.darkTheme.data.first() shouldBe true
    target.seekTime.data.first() shouldBe 45
    target.gridMode.data.first() shouldBe GridMode.GRID
    target.mediaDoubleClick.data.first() shouldBe MediaButtonClickAction.QUICK_BOOKMARK
    target.ignoreFileTags.data.first() shouldBe true
    // Untouched settings keep their defaults.
    target.autoRewind.data.first() shouldBe 2
  }

  @Test
  fun `an undecodable entry is skipped and the default kept`() = runTest {
    val target = Stores()
    target.snapshotter().apply(mapOf("seekTime" to "not-json", "darkTheme" to "true"))

    target.seekTime.data.first() shouldBe 20
    target.darkTheme.data.first() shouldBe true
  }

  @Test
  fun `applyScanAffecting applies ignoreFileTags and nothing else`() = runTest {
    val source = Stores()
    source.ignoreFileTags.updateData { true }
    source.darkTheme.updateData { true }
    val captured = source.snapshotter().capture()

    val target = Stores()
    target.snapshotter().applyScanAffecting(captured)

    target.ignoreFileTags.data.first() shouldBe true
    target.darkTheme.data.first() shouldBe false
  }
}
