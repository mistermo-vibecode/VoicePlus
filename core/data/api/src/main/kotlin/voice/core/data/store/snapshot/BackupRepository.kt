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

  /** Persist [uri] as the export folder (taking a persistable read/write grant), import any existing bundle, then export. */
  public suspend fun setBackupFolder(uri: Uri)

  /** Stop backing up and forget the folder (releasing the grant). */
  public suspend fun clearBackupFolder()

  /** Write the latest local snapshot to the external folder. Best-effort; no-op without a folder or a snapshot. */
  public suspend fun exportNow()

  /** Read the external bundle into the local ring and restore if the library is empty/collapsed. Returns true if a bundle was found. */
  @IgnorableReturnValue
  public suspend fun importAndRestore(): Boolean
}
