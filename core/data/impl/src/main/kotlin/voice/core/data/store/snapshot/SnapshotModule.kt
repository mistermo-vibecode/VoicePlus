package voice.core.data.store.snapshot

import android.app.Application
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import voice.core.data.store.VoiceDataStoreFactory

@ContributesTo(AppScope::class)
public interface SnapshotModule {

  @Provides
  @SingleIn(AppScope::class)
  @SnapshotJson
  private fun snapshotJson(): Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  // The ring owns its slot count and filenames; consumers inject the ring, never a slot.
  // Filenames must stay "librarySnapshot0/1/2" to preserve existing on-disk data.
  @Provides
  @SingleIn(AppScope::class)
  private fun snapshotRing(
    @SnapshotJson json: Json,
    context: Application,
  ): SnapshotRing {
    val factory = VoiceDataStoreFactory(json, context)
    return SnapshotRing(
      (0..2).map { slot ->
        factory.create(
          serializer = LibrarySnapshot.serializer().nullable,
          replaceCorruptedWithDefault = true,
          defaultValue = null,
          fileName = "librarySnapshot$slot",
        )
      },
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @SnapshotBackupStateStore
  private fun backupState(
    @SnapshotJson json: Json,
    context: Application,
  ): DataStore<BackupState> = VoiceDataStoreFactory(json, context).create(
    serializer = BackupState.serializer(),
    defaultValue = BackupState(),
    fileName = "snapshotBackupState",
    replaceCorruptedWithDefault = true,
  )
}
