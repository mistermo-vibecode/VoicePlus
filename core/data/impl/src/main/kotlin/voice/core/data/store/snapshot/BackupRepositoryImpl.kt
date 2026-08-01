package voice.core.data.store.snapshot

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import voice.core.data.folders.PersistedUriPermissions
import voice.core.data.repo.internals.AppDb
import voice.core.data.store.snapshot.rekey.ReKeyResult
import voice.core.logging.api.Logger
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

@Serializable
internal data class BackupState(
  val folderUri: String? = null,
  val lastBackupMillis: Long? = null,
  // Fingerprint of the last successfully exported snapshot, so an unchanged library doesn't mint
  // a new file. Reset when the folder changes: a new folder deserves a first file regardless.
  val lastBackupFingerprint: Long? = null,
  // A restore left unmatched books whose data exists ONLY in the backup files. While set,
  // automatic exports are suppressed: a partial-library export would become the newest save,
  // and after 7 autos the pre-wipe file holding the unmatched books' data would be pruned away.
  // Cleared by a fully-matched restore, or overridden by an explicit "Back up now".
  val restorePending: Boolean = false,
)

/** The automatic saves kept in the folder. Older autos are pruned; manual saves never are. */
internal const val KEEP_AUTO_BACKUPS = 7

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class BackupRepositoryImpl internal constructor(
  private val context: Application,
  @SnapshotBackupStateStore private val stateStore: DataStore<BackupState>,
  @SnapshotJson private val json: Json,
  private val ring: SnapshotRing,
  private val persistedUriPermissions: PersistedUriPermissions,
  private val osWipeRestorer: OsWipeRestorer,
  private val backupRestorer: BackupRestorer,
  private val settingsSnapshotter: SettingsSnapshotter,
) : BackupRepository {

  // Serialize import/export so a restore cannot interleave with a backup write.
  private val mutex = Mutex()

  override val backupFolder: Flow<Uri?> = stateStore.data.map { it.folderUri?.toUri() }
  override val lastBackupAt: Flow<Instant?> = stateStore.data.map { state ->
    state.lastBackupMillis?.let(Instant::ofEpochMilli)
  }
  private val lastRestoreState = MutableStateFlow<RestoreSummary?>(null)
  override val lastRestore: Flow<RestoreSummary?> = lastRestoreState
  private val statusState = MutableStateFlow<BackupStatus?>(null)
  override val status: Flow<BackupStatus?> = statusState
  private val busyState = MutableStateFlow(false)
  override val busy: Flow<Boolean> = busyState

  override suspend fun setBackupFolder(uri: Uri) {
    busyState.value = true
    try {
      val granted = runCatching {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        uri in persistedUriPermissions.persistedUris()
      }.getOrElse { false }
      if (!granted) {
        Logger.w("Backup folder permission was not granted; not saving the folder")
        statusState.value = BackupStatus(BackupStatusKind.PermissionDenied)
        return
      }
      stateStore.updateData { it.copy(folderUri = uri.toString(), lastBackupFingerprint = null) }
      val probed = withContext(Dispatchers.IO) { probe(listEntries(uri)) }
      statusState.value = when (probed) {
        is ExternalReadResult.Valid -> BackupStatus(BackupStatusKind.BackupFound)
        ExternalReadResult.Corrupt -> BackupStatus(BackupStatusKind.BackupUnreadable)
        ExternalReadResult.RefusedNewer -> BackupStatus(BackupStatusKind.RefusedNewerBackup)
        ExternalReadResult.Missing -> {
          // A fresh folder gets its first backup immediately, so choosing a folder IS turning
          // backup on — no separate first "Back up now" step needed.
          when (export(manual = false)) {
            BackupExportResult.Written -> BackupStatus(BackupStatusKind.BackupSaved)
            else -> BackupStatus(BackupStatusKind.NoBackupFound)
          }
        }
      }
    } finally {
      busyState.value = false
    }
  }

  override suspend fun clearBackupFolder() {
    activeFolder()?.let { uri ->
      runCatching {
        context.contentResolver.releasePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
      }.onFailure { Logger.w(it, "Could not release backup folder permission") }
    }
    stateStore.updateData { BackupState() }
    statusState.value = null
  }

  override suspend fun listBackups(): List<BackupEntry> {
    val folder = activeFolder() ?: return emptyList()
    return withContext(Dispatchers.IO) {
      runCatching { listEntries(folder) }.getOrElse {
        Logger.w(it, "Could not list backup folder")
        emptyList()
      }
    }
  }

  override suspend fun deleteBackup(entry: BackupEntry): Boolean {
    return withContext(Dispatchers.IO) {
      deleteDocument(entry.uri)
    }
  }

  override suspend fun exportNow(): BackupExportResult {
    busyState.value = true
    try {
      val result = export(manual = true)
      if (result == BackupExportResult.Written) {
        // The user explicitly chose to save the current state; stop suppressing autos.
        stateStore.updateData { it.copy(restorePending = false) }
      }
      statusState.value = when (result) {
        BackupExportResult.Written -> BackupStatus(BackupStatusKind.BackupSaved)
        BackupExportResult.Failed -> BackupStatus(BackupStatusKind.BackupFailed)
        else -> statusState.value
      }
      return result
    } finally {
      busyState.value = false
    }
  }

  override suspend fun exportAfterSnapshot(): BackupExportResult = export(manual = false)

  private suspend fun export(manual: Boolean): BackupExportResult {
    return mutex.withLock {
      try {
        val state = stateStore.data.first()
        if (!manual && state.restorePending) {
          // A partial restore is awaiting a re-grant-and-retry; the backup files still hold the
          // unmatched books' data. An automatic export now would shadow and eventually prune it.
          Logger.w("Automatic backup suppressed: a partial restore is pending")
          return@withLock BackupExportResult.SkippedNoSnapshot
        }
        val folder = activeFolder()
        if (folder == null) {
          if (state.folderUri != null) {
            // A folder is configured but its grant is gone (SD ejected, folder deleted, grant
            // revoked). Without a status the UI would keep claiming backups are on.
            statusState.value = BackupStatus(BackupStatusKind.PermissionDenied)
          }
          return@withLock BackupExportResult.SkippedNoFolder
        }
        val snapshot = ring.best() ?: return@withLock BackupExportResult.SkippedNoSnapshot
        withContext(Dispatchers.IO) {
          val fingerprint = ExternalBackupBundleCodec.meaningfulFingerprint(json, snapshot)
          if (!manual && stateStore.data.first().lastBackupFingerprint == fingerprint) {
            return@withContext BackupExportResult.SkippedUnchanged
          }
          // Always a NEW file: a crashed or interrupted write can only ever add a bad file, never
          // damage an existing good one. (A same-second name collision makes SAF append " (1)" —
          // the file works but won't be listed; the next write self-heals a second later.)
          val documentUri = createDocument(folder, BackupFileNames.fileName(manual, Instant.now()))
            ?: return@withContext BackupExportResult.Failed
          if (!writeDocumentText(documentUri, ExternalBackupBundleCodec.encode(json, snapshot))) {
            discardBadWrite(documentUri)
            return@withContext BackupExportResult.Failed
          }
          val verified = readDocumentText(documentUri)
            ?.let { ExternalBackupBundleCodec.decode(json, it) }
          if (verified != ExternalBackupBundleDecodeResult.Valid(snapshot)) {
            discardBadWrite(documentUri)
            return@withContext BackupExportResult.Failed
          }
          pruneAutoBackups(folder)
          stateStore.updateData {
            it.copy(lastBackupMillis = System.currentTimeMillis(), lastBackupFingerprint = fingerprint)
          }
          BackupExportResult.Written
        }
      } catch (e: CancellationException) {
        throw e
      } catch (t: Throwable) {
        Logger.w(t, "External backup export failed; library is unaffected")
        statusState.value = BackupStatus(BackupStatusKind.BackupFailed)
        BackupExportResult.Failed
      }
    }
  }

  private fun pruneAutoBackups(folder: Uri) {
    pruneCandidates(listEntries(folder), keep = KEEP_AUTO_BACKUPS).forEach { entry ->
      if (deleteDocument(entry.uri)) {
        Logger.d("Pruned old auto backup ${entry.displayName}")
      }
    }
  }

  override suspend fun importAndRestore(entry: BackupEntry?) {
    mutex.withLock {
      busyState.value = true
      try {
        val folder = activeFolder() ?: return@withLock
        val read = withContext(Dispatchers.IO) {
          if (entry != null) readEntry(entry.uri) else probe(listEntries(folder))
        }
        val snapshot = when (read) {
          is ExternalReadResult.Valid -> read.snapshot
          ExternalReadResult.Corrupt -> {
            statusState.value = BackupStatus(BackupStatusKind.BackupUnreadable)
            return@withLock
          }
          ExternalReadResult.Missing -> {
            statusState.value = BackupStatus(BackupStatusKind.NoBackupFound)
            return@withLock
          }
          ExternalReadResult.RefusedNewer -> {
            lastRestoreState.value = RestoreSummary(restoredCount = 0, unmatched = emptyList(), refusedNewerBackup = true)
            statusState.value = BackupStatus(BackupStatusKind.RefusedNewerBackup)
            return@withLock
          }
        }

        if (backupRestorer.canApplyDirect(snapshot)) {
          // Same device, ids alive: apply additively without the scan + re-key machinery.
          // applyDirect owns the whole commit (rows, then hidden set + settings on success).
          val restored = backupRestorer.applyDirect(snapshot)
          stateStore.updateData { it.copy(restorePending = false) }
          lastRestoreState.value = RestoreSummary(restoredCount = restored, unmatched = emptyList())
          statusState.value = BackupStatus(BackupStatusKind.RestoreComplete, restoredCount = restored)
        } else {
          // The re-key scan derives names, so the one scan-affecting setting must precede it;
          // everything else is applied only after the restore succeeds.
          settingsSnapshotter.applyScanAffecting(snapshot.settings)
          val result = osWipeRestorer.run(snapshot)
          settingsSnapshotter.apply(snapshot.settings)
          // While books remain unmatched, their data lives ONLY in the backup files: suppress
          // automatic exports so a partial-library save can't shadow (and eventually prune) them.
          stateStore.updateData { it.copy(restorePending = result.unmatched.isNotEmpty()) }
          lastRestoreState.value = result.toSummary()
          statusState.value = result.toStatus()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (t: Throwable) {
        Logger.w(t, "External backup import failed; library is unaffected")
        statusState.value = BackupStatus(BackupStatusKind.BackupUnreadable)
      } finally {
        busyState.value = false
      }
    }
  }

  private suspend fun activeFolder(): Uri? {
    val uri = stateStore.data.first().folderUri?.toUri() ?: return null
    return uri.takeIf { it in persistedUriPermissions.persistedUris() }
  }

  private sealed interface ExternalReadResult {
    data class Valid(val snapshot: LibrarySnapshot) : ExternalReadResult
    data object Missing : ExternalReadResult
    data object Corrupt : ExternalReadResult
    data object RefusedNewer : ExternalReadResult
  }

  /**
   * Read the newest usable save: corrupt files are skipped (an older good save still restores),
   * but a save from a NEWER app version stops the search — silently restoring older data past it
   * would look like data loss. The user is told to update the app instead.
   */
  private fun probe(entries: List<BackupEntry>): ExternalReadResult {
    if (entries.isEmpty()) return ExternalReadResult.Missing
    entries.forEach { entry ->
      when (val read = readEntry(entry.uri)) {
        is ExternalReadResult.Valid, ExternalReadResult.RefusedNewer -> return read
        ExternalReadResult.Corrupt, ExternalReadResult.Missing -> Unit // try the next one
      }
    }
    return ExternalReadResult.Corrupt
  }

  private fun readEntry(uri: Uri): ExternalReadResult {
    val text = runCatching { readDocumentText(uri) }.getOrNull() ?: return ExternalReadResult.Missing
    return when (val decoded = ExternalBackupBundleCodec.decode(json, text)) {
      ExternalBackupBundleDecodeResult.Corrupt -> ExternalReadResult.Corrupt
      ExternalBackupBundleDecodeResult.NewerFormat -> ExternalReadResult.RefusedNewer
      is ExternalBackupBundleDecodeResult.Valid -> decoded.snapshot.compatibility()
    }
  }

  private fun LibrarySnapshot.compatibility(): ExternalReadResult {
    return if (schemaVersion > LibrarySnapshot.SCHEMA_VERSION || dbVersion > AppDb.VERSION) {
      Logger.w("Refusing external backup from a newer version (schema=$schemaVersion, db=$dbVersion)")
      ExternalReadResult.RefusedNewer
    } else {
      ExternalReadResult.Valid(this)
    }
  }

  private fun readDocumentText(uri: Uri): String? {
    return context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
  }

  private fun writeDocumentText(
    uri: Uri,
    text: String,
  ): Boolean {
    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return false
    stream.bufferedWriter().use { it.write(text) }
    return true
  }

  /** All VoicePlus backup files in the folder, newest first (legacy fixed-name bundles last). */
  private fun listEntries(treeUri: Uri): List<BackupEntry> {
    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
    val entries = mutableListOf<BackupEntry>()
    context.contentResolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null,
      null,
      null,
    )?.use { cursor ->
      val documentIdColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val displayNameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (cursor.moveToNext()) {
        val displayName = cursor.getString(displayNameColumn)
        val parsed = BackupFileNames.parse(displayName) ?: continue
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(documentIdColumn))
        entries += BackupEntry(
          uri = documentUri,
          displayName = displayName,
          savedAt = parsed.savedAt,
          manual = parsed.manual,
          legacy = parsed.legacy,
        )
      }
    }
    return entries.sortedWith(newestFirst)
  }

  private fun createDocument(
    treeUri: Uri,
    displayName: String,
  ): Uri? {
    val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    return DocumentsContract.createDocument(context.contentResolver, parent, "application/json", displayName)
  }

  private fun deleteDocument(uri: Uri): Boolean {
    return runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
      .getOrElse { false }
  }

  // A failed or unverifiable write leaves only this new file behind; older saves are untouched.
  private fun discardBadWrite(uri: Uri) {
    if (!deleteDocument(uri)) {
      Logger.w("Could not delete a bad backup write; it will be skipped on restore")
    }
  }
}

/** Newest first; legacy bundles (no timestamp) last, primary before previous by name. */
internal val newestFirst: Comparator<BackupEntry> =
  compareByDescending<BackupEntry> { it.savedAt ?: Instant.EPOCH }.thenBy { it.displayName }

/** The auto saves beyond the newest [keep]. Manual and legacy files are never candidates. */
internal fun pruneCandidates(
  entries: List<BackupEntry>,
  keep: Int,
): List<BackupEntry> {
  return entries
    .filter { !it.manual && !it.legacy }
    .sortedWith(newestFirst)
    .drop(keep)
}

private fun ReKeyResult.toSummary(): RestoreSummary = RestoreSummary(
  restoredCount = matched.size,
  unmatched = unmatched.map { UnmatchedBookInfo(folderName = it.folderName, relPath = it.relPath, reason = it.reason.name) },
)

private fun ReKeyResult.toStatus(): BackupStatus {
  return when {
    matched.isNotEmpty() && unmatched.isEmpty() -> BackupStatus(
      BackupStatusKind.RestoreComplete,
      restoredCount = matched.size,
    )
    matched.isNotEmpty() -> BackupStatus(
      BackupStatusKind.RestorePartial,
      restoredCount = matched.size,
      unmatchedCount = unmatched.size,
    )
    unmatched.isNotEmpty() -> BackupStatus(BackupStatusKind.RestoreNoMatch, unmatchedCount = unmatched.size)
    else -> BackupStatus(BackupStatusKind.NoBackupFound)
  }
}
