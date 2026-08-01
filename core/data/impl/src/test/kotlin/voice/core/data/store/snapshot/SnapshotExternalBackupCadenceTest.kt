package voice.core.data.store.snapshot

import io.kotest.matchers.shouldBe
import org.junit.Test

class SnapshotExternalBackupCadenceTest {

  private var now = 1_000L
  private val cadence = SnapshotExternalBackupCadence(
    clockMillis = { now },
    minIntervalMillis = 60_000L,
  )

  @Test
  fun `first snapshot exports immediately and second inside interval is deferred`() {
    cadence.shouldExportAfterSnapshot(force = false) shouldBe true
    cadence.record(BackupExportResult.Written)

    now += 10_000L

    cadence.shouldExportAfterSnapshot(force = false) shouldBe false
    cadence.shouldExportPending() shouldBe false

    now += 50_000L

    cadence.shouldExportPending() shouldBe true
  }

  @Test
  fun `forced snapshot bypasses interval and clears pending work`() {
    cadence.shouldExportAfterSnapshot(force = false) shouldBe true
    cadence.record(BackupExportResult.Written)
    now += 10_000L
    cadence.shouldExportAfterSnapshot(force = false) shouldBe false

    cadence.shouldExportAfterSnapshot(force = true) shouldBe true
    cadence.record(BackupExportResult.Written)

    cadence.shouldExportPending() shouldBe false
  }

  @Test
  fun `failed export remains pending for retry`() {
    cadence.shouldExportAfterSnapshot(force = false) shouldBe true
    cadence.record(BackupExportResult.Failed)

    cadence.shouldExportPending() shouldBe true
  }

  @Test
  fun `unchanged export counts as covered`() {
    cadence.shouldExportAfterSnapshot(force = false) shouldBe true
    cadence.record(BackupExportResult.SkippedUnchanged)

    now += 10_000L

    cadence.shouldExportAfterSnapshot(force = false) shouldBe false
  }

  @Test
  fun `missing folder does not stay pending`() {
    cadence.shouldExportAfterSnapshot(force = false) shouldBe true
    cadence.record(BackupExportResult.SkippedNoFolder)

    cadence.shouldExportPending() shouldBe false
  }

  @Test
  fun `missing snapshot does not stay pending`() {
    cadence.shouldExportAfterSnapshot(force = false) shouldBe true
    cadence.record(BackupExportResult.SkippedNoSnapshot)

    cadence.shouldExportPending() shouldBe false
  }

  @Test
  fun `newer backup block does not retry in a loop`() {
    cadence.shouldExportAfterSnapshot(force = false) shouldBe true
    cadence.record(BackupExportResult.BlockedNewerBackup)

    cadence.shouldExportPending() shouldBe false
  }
}
