package voice.core.data.store.snapshot

import kotlin.time.Duration.Companion.minutes

internal class SnapshotExternalBackupCadence(
  private val clockMillis: () -> Long = System::currentTimeMillis,
  private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL.inWholeMilliseconds,
) {

  private var lastCoveredMillis: Long? = null
  private var pending = false

  @Synchronized
  fun shouldExportAfterSnapshot(force: Boolean): Boolean {
    if (force) {
      pending = false
      return true
    }
    if (isIntervalOpen()) return true
    pending = true
    return false
  }

  @Synchronized
  fun shouldExportPending(): Boolean {
    return pending && isIntervalOpen()
  }

  @Synchronized
  fun record(result: BackupExportResult) {
    when (result) {
      BackupExportResult.Written,
      BackupExportResult.SkippedUnchanged,
      -> {
        lastCoveredMillis = clockMillis()
        pending = false
      }
      BackupExportResult.Failed -> pending = true
      BackupExportResult.SkippedNoFolder,
      BackupExportResult.SkippedNoSnapshot,
      BackupExportResult.BlockedCorruptBackup,
      BackupExportResult.BlockedNewerBackup,
      -> pending = false
    }
  }

  private fun isIntervalOpen(): Boolean {
    val lastCovered = lastCoveredMillis ?: return true
    return clockMillis() - lastCovered >= minIntervalMillis
  }

  private companion object {
    val DEFAULT_MIN_INTERVAL = 1.minutes
  }
}
