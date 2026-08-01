package voice.core.data.store.snapshot

internal sealed interface ExistingExternalBackup {
  data object Missing : ExistingExternalBackup
  data object Corrupt : ExistingExternalBackup
  data class Valid(
    val meaningfulFingerprint: Long,
    val isNewerThanThisApp: Boolean,
  ) : ExistingExternalBackup
}

internal sealed interface ExternalBackupExportDecision {
  data object SkippedNoFolder : ExternalBackupExportDecision
  data object SkippedNoSnapshot : ExternalBackupExportDecision
  data object BlockedCorruptBackup : ExternalBackupExportDecision
  data object BlockedNewerBackup : ExternalBackupExportDecision
  data object SkippedUnchanged : ExternalBackupExportDecision
  data class Write(val rotatePrevious: Boolean) : ExternalBackupExportDecision
}

internal object ExternalBackupExportPlanner {

  fun plan(
    hasFolder: Boolean,
    hasSnapshot: Boolean,
    forceWrite: Boolean,
    allowOverwriteCorrupt: Boolean,
    existing: ExistingExternalBackup,
    nextFingerprint: Long,
  ): ExternalBackupExportDecision {
    if (!hasFolder) return ExternalBackupExportDecision.SkippedNoFolder
    if (!hasSnapshot) return ExternalBackupExportDecision.SkippedNoSnapshot
    return when (existing) {
      ExistingExternalBackup.Missing -> ExternalBackupExportDecision.Write(rotatePrevious = false)
      ExistingExternalBackup.Corrupt -> {
        if (allowOverwriteCorrupt) {
          ExternalBackupExportDecision.Write(rotatePrevious = false)
        } else {
          ExternalBackupExportDecision.BlockedCorruptBackup
        }
      }
      is ExistingExternalBackup.Valid -> {
        when {
          existing.isNewerThanThisApp -> ExternalBackupExportDecision.BlockedNewerBackup
          !forceWrite && existing.meaningfulFingerprint == nextFingerprint -> {
            ExternalBackupExportDecision.SkippedUnchanged
          }
          else -> ExternalBackupExportDecision.Write(rotatePrevious = true)
        }
      }
    }
  }
}
