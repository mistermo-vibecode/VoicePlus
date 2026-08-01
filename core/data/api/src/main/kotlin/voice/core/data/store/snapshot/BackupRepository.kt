package voice.core.data.store.snapshot

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Maintains an off-device copy of the latest library snapshot in a user-chosen SAF folder, so the
 * data survives uninstall / clear-data, and can be re-imported on a wiped/fresh install.
 */
public interface BackupRepository {

  /** The currently configured external backup folder, or null if none chosen. */
  public val backupFolder: Flow<Uri?>

  /** When the external bundle was last written, or null if never. */
  public val lastBackupAt: Flow<Instant?>

  /** The outcome of the most recent [importAndRestore], or null if none has run this session. */
  public val lastRestore: Flow<RestoreSummary?>

  /** Latest backup/restore action state for the settings UI. */
  public val status: Flow<BackupStatus?>

  /** True while a backup or restore operation is running. */
  public val busy: Flow<Boolean>

  /** Persist [uri] as the export folder (taking a persistable read/write grant). Does not restore or overwrite. */
  public suspend fun setBackupFolder(uri: Uri)

  /** Stop backing up and forget the folder (releasing the grant). */
  public suspend fun clearBackupFolder()

  /** Write the latest local snapshot to the external folder. Best-effort; no-op without a folder or a snapshot. */
  public suspend fun exportNow(): BackupExportResult

  /** Write after an automatic local snapshot. Refuses to overwrite a corrupt existing external bundle. */
  public suspend fun exportAfterSnapshot(): BackupExportResult

  /** Explicitly restore from the external bundle. Returns true when any matched/unmatched backup data was found. */
  @IgnorableReturnValue
  public suspend fun importAndRestore(): Boolean
}

public data class BackupStatus(
  val kind: BackupStatusKind,
  val restoredCount: Int = 0,
  val unmatchedCount: Int = 0,
)

public enum class BackupStatusKind {
  NoBackupFound,
  BackupFound,
  BackupSaved,
  BackupUnreadable,
  BackupFailed,
  RestoreComplete,
  RestorePartial,
  RestoreNoMatch,
  RefusedNewerBackup,
  PermissionDenied,
}

public enum class BackupExportResult {
  Written,
  SkippedUnchanged,
  SkippedNoFolder,
  SkippedNoSnapshot,
  BlockedCorruptBackup,
  BlockedNewerBackup,
  Failed,
}

/**
 * The result of an OS-wipe restore. [restoredCount] books were re-attached to their freshly-scanned files;
 * [unmatched] books could not be safely auto-matched (e.g. the folder wasn't re-granted, or its contents
 * changed) and are surfaced rather than silently dropped or guessed onto another book.
 */
public data class RestoreSummary(
  val restoredCount: Int,
  val unmatched: List<UnmatchedBookInfo>,
  // The folder held a backup from a NEWER app version than this build can safely read, so it was refused
  // (and NOT overwritten). The user should update the app, then restore again.
  val refusedNewerBackup: Boolean = false,
)

public data class UnmatchedBookInfo(
  val folderName: String,
  val relPath: String,
  val reason: String,
)
