package voice.core.data.store.snapshot

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.data.store.snapshot.ExistingExternalBackup.Corrupt
import voice.core.data.store.snapshot.ExistingExternalBackup.Missing
import voice.core.data.store.snapshot.ExistingExternalBackup.Valid
import voice.core.data.store.snapshot.ExternalBackupExportDecision.BlockedCorruptBackup
import voice.core.data.store.snapshot.ExternalBackupExportDecision.BlockedNewerBackup
import voice.core.data.store.snapshot.ExternalBackupExportDecision.SkippedNoFolder
import voice.core.data.store.snapshot.ExternalBackupExportDecision.SkippedNoSnapshot
import voice.core.data.store.snapshot.ExternalBackupExportDecision.SkippedUnchanged
import voice.core.data.store.snapshot.ExternalBackupExportDecision.Write

class ExternalBackupExportPlannerTest {

  @Test
  fun `automatic export with corrupt primary is blocked`() {
    plan(
      forceWrite = false,
      allowOverwriteCorrupt = false,
      existing = Corrupt,
    ) shouldBe BlockedCorruptBackup
  }

  @Test
  fun `automatic export with equivalent primary is skipped unchanged`() {
    plan(
      forceWrite = false,
      allowOverwriteCorrupt = false,
      existing = Valid(meaningfulFingerprint = 7, isNewerThanThisApp = false),
      nextFingerprint = 7,
    ) shouldBe SkippedUnchanged
  }

  @Test
  fun `manual export with equivalent primary still writes and rotates`() {
    plan(
      forceWrite = true,
      allowOverwriteCorrupt = true,
      existing = Valid(meaningfulFingerprint = 7, isNewerThanThisApp = false),
      nextFingerprint = 7,
    ) shouldBe Write(rotatePrevious = true)
  }

  @Test
  fun `changed snapshot with valid primary writes and rotates`() {
    plan(
      forceWrite = false,
      allowOverwriteCorrupt = false,
      existing = Valid(meaningfulFingerprint = 7, isNewerThanThisApp = false),
      nextFingerprint = 8,
    ) shouldBe Write(rotatePrevious = true)
  }

  @Test
  fun `missing folder is skipped`() {
    plan(hasFolder = false) shouldBe SkippedNoFolder
  }

  @Test
  fun `missing local snapshot is skipped`() {
    plan(hasSnapshot = false) shouldBe SkippedNoSnapshot
  }

  @Test
  fun `newer primary is blocked even for manual export`() {
    plan(
      forceWrite = true,
      allowOverwriteCorrupt = true,
      existing = Valid(meaningfulFingerprint = 7, isNewerThanThisApp = true),
      nextFingerprint = 8,
    ) shouldBe BlockedNewerBackup
  }

  @Test
  fun `manual export can replace corrupt primary without rotating it`() {
    plan(
      forceWrite = true,
      allowOverwriteCorrupt = true,
      existing = Corrupt,
    ) shouldBe Write(rotatePrevious = false)
  }

  @Test
  fun `missing primary writes without rotating`() {
    plan(existing = Missing) shouldBe Write(rotatePrevious = false)
  }

  private fun plan(
    hasFolder: Boolean = true,
    hasSnapshot: Boolean = true,
    forceWrite: Boolean = false,
    allowOverwriteCorrupt: Boolean = false,
    existing: ExistingExternalBackup = Missing,
    nextFingerprint: Long = 8,
  ): ExternalBackupExportDecision {
    return ExternalBackupExportPlanner.plan(
      hasFolder = hasFolder,
      hasSnapshot = hasSnapshot,
      forceWrite = forceWrite,
      allowOverwriteCorrupt = allowOverwriteCorrupt,
      existing = existing,
      nextFingerprint = nextFingerprint,
    )
  }
}
