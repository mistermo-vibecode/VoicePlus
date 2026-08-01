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
)

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class BackupRepositoryImpl internal constructor(
  private val context: Application,
  @SnapshotBackupStateStore private val stateStore: DataStore<BackupState>,
  @SnapshotJson private val json: Json,
  @SnapshotSlot0Store slot0: DataStore<LibrarySnapshot?>,
  @SnapshotSlot1Store slot1: DataStore<LibrarySnapshot?>,
  @SnapshotSlot2Store slot2: DataStore<LibrarySnapshot?>,
  private val persistedUriPermissions: PersistedUriPermissions,
  private val osWipeRestorer: OsWipeRestorer,
) : BackupRepository {

  private val ring = SnapshotRing(listOf(slot0, slot1, slot2))

  // Serialize import/export so a restore cannot interleave with a bundle rewrite.
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
      stateStore.updateData { it.copy(folderUri = uri.toString()) }
      statusState.value = when (readExternalSnapshot(uri)) {
        is ExternalReadResult.Valid -> BackupStatus(BackupStatusKind.BackupFound)
        ExternalReadResult.Corrupt -> BackupStatus(BackupStatusKind.BackupUnreadable)
        ExternalReadResult.Missing -> BackupStatus(BackupStatusKind.NoBackupFound)
        ExternalReadResult.RefusedNewer -> BackupStatus(BackupStatusKind.RefusedNewerBackup)
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

  override suspend fun exportNow(): BackupExportResult {
    return export(
      allowOverwriteCorrupt = true,
      forceWrite = true,
      publishBusy = true,
      publishSuccessStatus = true,
    )
  }

  override suspend fun exportAfterSnapshot(): BackupExportResult {
    return export(
      allowOverwriteCorrupt = false,
      forceWrite = false,
      publishBusy = false,
      publishSuccessStatus = false,
    )
  }

  private suspend fun export(
    allowOverwriteCorrupt: Boolean,
    forceWrite: Boolean,
    publishBusy: Boolean,
    publishSuccessStatus: Boolean,
  ): BackupExportResult {
    return mutex.withLock {
      if (publishBusy) busyState.value = true
      try {
        val folder = activeFolder()
        val snapshot = ring.best()
        if (folder == null || snapshot == null) {
          return@withLock ExternalBackupExportPlanner.plan(
            hasFolder = folder != null,
            hasSnapshot = snapshot != null,
            forceWrite = forceWrite,
            allowOverwriteCorrupt = allowOverwriteCorrupt,
            existing = ExistingExternalBackup.Missing,
            nextFingerprint = 0L,
          ).toResult()
        }
        withContext(Dispatchers.IO) {
          val documents = listBackupDocuments(folder)
          val currentText = documents.primary?.let { readDocumentText(it) }
          val currentDecode = currentText?.let { ExternalBackupBundleCodec.decode(json, it) }
          val existing = when (currentDecode) {
            null -> ExistingExternalBackup.Missing
            ExternalBackupBundleDecodeResult.Corrupt -> ExistingExternalBackup.Corrupt
            is ExternalBackupBundleDecodeResult.Valid -> ExistingExternalBackup.Valid(
              meaningfulFingerprint = ExternalBackupBundleCodec.meaningfulFingerprint(json, currentDecode.snapshot),
              isNewerThanThisApp = currentDecode.snapshot.isNewerThanThisApp(),
            )
          }
          val decision = ExternalBackupExportPlanner.plan(
            hasFolder = true,
            hasSnapshot = true,
            forceWrite = forceWrite,
            allowOverwriteCorrupt = allowOverwriteCorrupt,
            existing = existing,
            nextFingerprint = ExternalBackupBundleCodec.meaningfulFingerprint(json, snapshot),
          )
          when (decision) {
            ExternalBackupExportDecision.BlockedCorruptBackup -> {
              statusState.value = BackupStatus(BackupStatusKind.BackupUnreadable)
              return@withContext BackupExportResult.BlockedCorruptBackup
            }
            ExternalBackupExportDecision.BlockedNewerBackup -> {
              statusState.value = BackupStatus(BackupStatusKind.RefusedNewerBackup)
              return@withContext BackupExportResult.BlockedNewerBackup
            }
            ExternalBackupExportDecision.SkippedNoFolder,
            ExternalBackupExportDecision.SkippedNoSnapshot,
            ExternalBackupExportDecision.SkippedUnchanged,
            -> return@withContext decision.toResult()
            is ExternalBackupExportDecision.Write -> {
              if (decision.rotatePrevious && currentText != null) {
                val previousUri = documents.previous ?: createDocument(folder, PREVIOUS_BUNDLE_NAME)
                if (previousUri != null && !writeDocumentText(previousUri, currentText)) {
                  Logger.w("Could not rotate previous external backup; continuing with primary backup write")
                }
              }
            }
          }
          val primaryUri = documents.primary ?: createDocument(folder, BUNDLE_NAME)
          if (primaryUri == null) {
            statusState.value = BackupStatus(BackupStatusKind.BackupFailed)
            return@withContext BackupExportResult.Failed
          }
          if (!writeDocumentText(primaryUri, ExternalBackupBundleCodec.encode(json, snapshot))) {
            statusState.value = BackupStatus(BackupStatusKind.BackupFailed)
            return@withContext BackupExportResult.Failed
          }
          val verified = readDocumentText(primaryUri)
            ?.let { ExternalBackupBundleCodec.decode(json, it) }
          if (verified != ExternalBackupBundleDecodeResult.Valid(snapshot)) {
            statusState.value = BackupStatus(BackupStatusKind.BackupFailed)
            return@withContext BackupExportResult.Failed
          }
          stateStore.updateData { it.copy(lastBackupMillis = System.currentTimeMillis()) }
          if (publishSuccessStatus) statusState.value = BackupStatus(BackupStatusKind.BackupSaved)
          BackupExportResult.Written
        }
      } catch (e: CancellationException) {
        throw e
      } catch (t: Throwable) {
        Logger.w(t, "External backup export failed; library is unaffected")
        statusState.value = BackupStatus(BackupStatusKind.BackupFailed)
        BackupExportResult.Failed
      } finally {
        if (publishBusy) busyState.value = false
      }
    }
  }

  override suspend fun importAndRestore(): Boolean {
    val outcome = import()
    return outcome is ImportOutcome.Imported && outcome.changed
  }

  private sealed interface ImportOutcome {
    data object NothingToImport : ImportOutcome
    data object Corrupt : ImportOutcome
    data object RefusedNewer : ImportOutcome
    data class Imported(val changed: Boolean) : ImportOutcome
  }

  private suspend fun import(): ImportOutcome {
    return mutex.withLock {
      busyState.value = true
      try {
        val folder = activeFolder() ?: return@withLock ImportOutcome.NothingToImport
        val snapshot = when (val read = readExternalSnapshot(folder)) {
          is ExternalReadResult.Valid -> read.snapshot
          ExternalReadResult.Corrupt -> {
            statusState.value = BackupStatus(BackupStatusKind.BackupUnreadable)
            return@withLock ImportOutcome.Corrupt
          }
          ExternalReadResult.Missing -> {
            statusState.value = BackupStatus(BackupStatusKind.NoBackupFound)
            return@withLock ImportOutcome.NothingToImport
          }
          ExternalReadResult.RefusedNewer -> {
            lastRestoreState.value = RestoreSummary(restoredCount = 0, unmatched = emptyList(), refusedNewerBackup = true)
            statusState.value = BackupStatus(BackupStatusKind.RefusedNewerBackup)
            return@withLock ImportOutcome.RefusedNewer
          }
        }

        val result = osWipeRestorer.run(snapshot)
        lastRestoreState.value = result.toSummary()
        statusState.value = result.toStatus()
        ImportOutcome.Imported(result.matched.isNotEmpty() || result.unmatched.isNotEmpty())
      } catch (e: CancellationException) {
        throw e
      } catch (t: Throwable) {
        Logger.w(t, "External backup import failed; library is unaffected")
        statusState.value = BackupStatus(BackupStatusKind.BackupUnreadable)
        ImportOutcome.Corrupt
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

  private suspend fun readExternalSnapshot(folder: Uri): ExternalReadResult = withContext(Dispatchers.IO) {
    val documents = listBackupDocuments(folder)
    val primary = documents.primary?.let { readDocumentText(it) }
    if (primary != null) {
      when (val decoded = ExternalBackupBundleCodec.decode(json, primary)) {
        ExternalBackupBundleDecodeResult.Corrupt -> {
          documents.previous?.let { readDocumentText(it) }?.let { previous ->
            when (val previousDecoded = ExternalBackupBundleCodec.decode(json, previous)) {
              ExternalBackupBundleDecodeResult.Corrupt -> return@withContext ExternalReadResult.Corrupt
              is ExternalBackupBundleDecodeResult.Valid -> return@withContext previousDecoded.snapshot.compatibility()
            }
          }
          return@withContext ExternalReadResult.Corrupt
        }
        is ExternalBackupBundleDecodeResult.Valid -> return@withContext decoded.snapshot.compatibility()
      }
    }
    val previous = documents.previous?.let { readDocumentText(it) } ?: return@withContext ExternalReadResult.Missing
    when (val decoded = ExternalBackupBundleCodec.decode(json, previous)) {
      ExternalBackupBundleDecodeResult.Corrupt -> ExternalReadResult.Corrupt
      is ExternalBackupBundleDecodeResult.Valid -> decoded.snapshot.compatibility()
    }
  }

  private fun LibrarySnapshot.compatibility(): ExternalReadResult {
    return if (isNewerThanThisApp()) {
      Logger.w("Refusing external backup from a newer version (schema=$schemaVersion, db=$dbVersion)")
      ExternalReadResult.RefusedNewer
    } else {
      ExternalReadResult.Valid(this)
    }
  }

  private fun LibrarySnapshot.isNewerThanThisApp(): Boolean {
    return schemaVersion > LibrarySnapshot.SCHEMA_VERSION || dbVersion > AppDb.VERSION
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

  private fun listBackupDocuments(treeUri: Uri): BackupDocuments {
    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
    var primary: Uri? = null
    var previous: Uri? = null
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
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(documentIdColumn))
        when (displayName) {
          BUNDLE_NAME -> primary = documentUri
          PREVIOUS_BUNDLE_NAME -> previous = documentUri
        }
      }
    }
    return BackupDocuments(primary = primary, previous = previous)
  }

  private fun createDocument(
    treeUri: Uri,
    displayName: String,
  ): Uri? {
    val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    return DocumentsContract.createDocument(context.contentResolver, parent, "application/json", displayName)
  }

  private data class BackupDocuments(
    val primary: Uri?,
    val previous: Uri?,
  )

  private companion object {
    const val BUNDLE_NAME = "voiceplus-backup.json"
    const val PREVIOUS_BUNDLE_NAME = "voiceplus-backup.previous.json"
  }
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

private fun ExternalBackupExportDecision.toResult(): BackupExportResult {
  return when (this) {
    ExternalBackupExportDecision.BlockedCorruptBackup -> BackupExportResult.BlockedCorruptBackup
    ExternalBackupExportDecision.BlockedNewerBackup -> BackupExportResult.BlockedNewerBackup
    ExternalBackupExportDecision.SkippedNoFolder -> BackupExportResult.SkippedNoFolder
    ExternalBackupExportDecision.SkippedNoSnapshot -> BackupExportResult.SkippedNoSnapshot
    ExternalBackupExportDecision.SkippedUnchanged -> BackupExportResult.SkippedUnchanged
    is ExternalBackupExportDecision.Write -> BackupExportResult.Written
  }
}
