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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import voice.core.data.folders.PersistedUriPermissions
import voice.core.data.repo.internals.AppDb
import voice.core.logging.api.Logger
import java.time.Instant

@Serializable
internal data class BackupState(
  val folderUri: String? = null,
  val lastBackupMillis: Long? = null,
)

@OptIn(ExperimentalSerializationApi::class)
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

  override val backupFolder: Flow<Uri?> = stateStore.data.map { it.folderUri?.toUri() }
  override val lastBackupAt: Flow<Instant?> = stateStore.data.map { state ->
    state.lastBackupMillis?.let(Instant::ofEpochMilli)
  }

  override suspend fun setBackupFolder(uri: Uri) {
    val granted = runCatching {
      context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
      uri in persistedUriPermissions.persistedUris()
    }.getOrElse { false }
    if (!granted) {
      Logger.w("Backup folder permission was not granted; not saving the folder")
      return
    }
    stateStore.updateData { it.copy(folderUri = uri.toString()) }
    importAndRestore()
    exportNow()
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
  }

  override suspend fun exportNow() {
    runCatching {
      val folder = activeFolder() ?: return
      val snapshot = ring.best() ?: return
      withContext(Dispatchers.IO) {
        val bundleUri = findOrCreateBundle(folder) ?: return@withContext
        context.contentResolver.openOutputStream(bundleUri, "wt")?.use { out ->
          json.encodeToStream(LibrarySnapshot.serializer(), snapshot, out)
        }
      }
      stateStore.updateData { it.copy(lastBackupMillis = System.currentTimeMillis()) }
    }.onFailure { Logger.w(it, "External backup export failed; library is unaffected") }
  }

  override suspend fun importAndRestore(): Boolean {
    return runCatching {
      val folder = activeFolder() ?: return false
      val snapshot = withContext(Dispatchers.IO) {
        val bundleUri = findBundle(folder) ?: return@withContext null
        context.contentResolver.openInputStream(bundleUri)?.use { input ->
          json.decodeFromStream(LibrarySnapshot.serializer(), input)
        }
      } ?: return false
      // Only ingest genuine, compatible VoicePlus bundles.
      if (snapshot.schemaVersion != LibrarySnapshot.SCHEMA_VERSION) {
        Logger.w("Ignoring external backup with unexpected schemaVersion=${snapshot.schemaVersion}")
        return false
      }
      if (snapshot.dbVersion > AppDb.VERSION) {
        Logger.w("Ignoring external backup from a newer dbVersion=${snapshot.dbVersion}")
        return false
      }
      // OS-wipe path: scan for the re-granted books under their NEW URIs, then re-key the bundle onto them.
      // We deliberately do NOT write this (dead-URI) bundle to the on-device ring; the SnapshotWriter
      // captures the freshly re-keyed (new-URI) state into the ring right after.
      val result = osWipeRestorer.run(snapshot)
      result.matched.isNotEmpty() || result.unmatched.isNotEmpty()
    }.getOrElse {
      Logger.w(it, "External backup import failed; library is unaffected")
      false
    }
  }

  private suspend fun activeFolder(): Uri? {
    val uri = stateStore.data.first().folderUri?.toUri() ?: return null
    return uri.takeIf { it in persistedUriPermissions.persistedUris() }
  }

  private fun findBundle(treeUri: Uri): Uri? {
    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
    context.contentResolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null,
      null,
      null,
    )?.use { cursor ->
      while (cursor.moveToNext()) {
        if (cursor.getString(1) == BUNDLE_NAME) {
          return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
        }
      }
    }
    return null
  }

  private fun findOrCreateBundle(treeUri: Uri): Uri? {
    findBundle(treeUri)?.let { return it }
    val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    return DocumentsContract.createDocument(context.contentResolver, parent, "application/json", BUNDLE_NAME)
  }

  private companion object {
    const val BUNDLE_NAME = "voiceplus-backup.json"
  }
}
