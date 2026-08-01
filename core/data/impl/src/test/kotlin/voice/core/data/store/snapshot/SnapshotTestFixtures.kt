package voice.core.data.store.snapshot

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import voice.core.data.BookId
import voice.core.data.GridMode
import voice.core.data.MediaButtonClickAction
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.DocumentFileWithUri
import voice.core.data.folders.FolderType
import voice.core.data.repo.internals.MemoryDataStore
import voice.core.data.sleeptimer.SleepTimerPreference
import kotlin.time.Duration.Companion.seconds

internal val snapshotTestJson: Json = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

/** A [SettingsSnapshotter] over in-memory stores at their production defaults. */
internal fun testSettingsSnapshotter(): SettingsSnapshotter = SettingsSnapshotter(
  darkTheme = MemoryDataStore(false),
  autoRewind = MemoryDataStore(2),
  seekTime = MemoryDataStore(20),
  fadeOut = MemoryDataStore(10.seconds),
  sleepTimer = MemoryDataStore(SleepTimerPreference.Default),
  gridMode = MemoryDataStore(GridMode.FOLLOW_DEVICE),
  mediaDoubleClick = MemoryDataStore(MediaButtonClickAction.NONE),
  mediaTripleClick = MemoryDataStore(MediaButtonClickAction.NONE),
  experimentalPersistence = MemoryDataStore(false),
  ignoreFileTags = MemoryDataStore(false),
  json = snapshotTestJson,
)

internal object EmptyAudiobookFolders : AudiobookFolders {
  override fun all(): Flow<Map<FolderType, List<DocumentFileWithUri>>> = flowOf(emptyMap())

  override fun add(
    uri: Uri,
    type: FolderType,
  ) = Unit

  override fun remove(
    uri: Uri,
    folderType: FolderType,
  ) = Unit

  override suspend fun hasAnyFolders(): Boolean = false

  override suspend fun isManaged(bookId: BookId): Boolean = true
}
