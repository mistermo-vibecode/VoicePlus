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

  @Provides
  @SingleIn(AppScope::class)
  @SnapshotSlot0Store
  private fun snapshotSlot0(
    @SnapshotJson json: Json,
    context: Application,
  ): DataStore<LibrarySnapshot?> = VoiceDataStoreFactory(json, context).create(
    serializer = LibrarySnapshot.serializer().nullable,
    defaultValue = null,
    fileName = "librarySnapshot0",
  )

  @Provides
  @SingleIn(AppScope::class)
  @SnapshotSlot1Store
  private fun snapshotSlot1(
    @SnapshotJson json: Json,
    context: Application,
  ): DataStore<LibrarySnapshot?> = VoiceDataStoreFactory(json, context).create(
    serializer = LibrarySnapshot.serializer().nullable,
    defaultValue = null,
    fileName = "librarySnapshot1",
  )

  @Provides
  @SingleIn(AppScope::class)
  @SnapshotSlot2Store
  private fun snapshotSlot2(
    @SnapshotJson json: Json,
    context: Application,
  ): DataStore<LibrarySnapshot?> = VoiceDataStoreFactory(json, context).create(
    serializer = LibrarySnapshot.serializer().nullable,
    defaultValue = null,
    fileName = "librarySnapshot2",
  )
}
