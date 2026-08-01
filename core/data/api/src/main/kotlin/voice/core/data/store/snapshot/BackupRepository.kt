package voice.core.data.store.snapshot

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Maintains off-device copies of the library snapshot in a user-chosen SAF folder, so the data
 * survives uninstall / clear-data, and can be re-imported on a wiped/fresh install.
 *
 * Every save is a NEW timestamped file — nothing is ever overwritten, so a crashed or corrupt
 * write can only add a bad file, never destroy a good one. Automatic saves are pruned to the
 * newest few; manual saves are kept until the user deletes them.
 */
public interface BackupRepository {

  /** The currently configured external backup folder, or null if none chosen. */
  public val backupFolder: Flow<Uri?>

  /** When a backup file was last written, or null if never. */
  public val lastBackupAt: Flow<Instant?>

  /** The outcome of the most recent [importAndRestore], or null if none has run this session. */
  public val lastRestore: Flow<RestoreSummary?>

  /** Latest backup/restore action state for the settings UI. */
  public val status: Flow<BackupStatus?>

  /** True while a backup or restore operation is running. */
  public val busy: Flow<Boolean>

  /** Persist [uri] as the export folder (taking a persistable read/write grant). Writes a first backup if the folder has none. */
  public suspend fun setBackupFolder(uri: Uri)

  /** Stop backing up and forget the folder (releasing the grant). */
  public suspend fun clearBackupFolder()

  /** The saves found in the backup folder, newest first. Empty without a folder. */
  public suspend fun listBackups(): List<BackupEntry>

  /** Delete one save file. */
  @IgnorableReturnValue
  public suspend fun deleteBackup(entry: BackupEntry): Boolean

  /** Write a manual save point now. Always writes a new file, even if nothing changed. */
  @IgnorableReturnValue
  public suspend fun exportNow(): BackupExportResult

  /** Write an automatic save after a local snapshot. Skips when nothing meaningful changed. */
  @IgnorableReturnValue
  public suspend fun exportAfterSnapshot(): BackupExportResult

  /**
   * Restore from [entry], or from the newest readable save when null. Outcomes are reported
   * through [status] and [lastRestore].
   */
  public suspend fun importAndRestore(entry: BackupEntry? = null)
}

/** One save file in the backup folder. [savedAt] is parsed from the file name; null for legacy fixed-name bundles. */
public data class BackupEntry(
  val uri: Uri,
  val displayName: String,
  val savedAt: Instant?,
  val manual: Boolean,
  val legacy: Boolean,
)

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
  // The chosen backup is from a NEWER app version than this build can safely read, so it was refused.
  // The user should update the app, then restore again.
  val refusedNewerBackup: Boolean = false,
)

public data class UnmatchedBookInfo(
  val folderName: String,
  val relPath: String,
  val reason: String,
)
