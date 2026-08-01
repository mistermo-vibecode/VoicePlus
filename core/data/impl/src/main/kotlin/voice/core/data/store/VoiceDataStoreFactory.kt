package voice.core.data.store

import android.app.Application
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.zacsweers.metro.Inject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

@Inject
internal class VoiceDataStoreFactory(
  private val json: Json,
  private val context: Application,
) {

  fun <T> create(
    serializer: KSerializer<T>,
    defaultValue: T,
    fileName: String,
    migrations: List<DataMigration<T>> = emptyList(),
    // Opt-in: a corrupted file is replaced with the default instead of poisoning every read AND
    // write of the store forever. Use for stores whose loss is acceptable (snapshots, checkpoints);
    // leave off where silent reset would itself be data loss.
    replaceCorruptedWithDefault: Boolean = false,
  ): DataStore<T> {
    return DataStoreFactory.create(
      migrations = migrations,
      corruptionHandler = if (replaceCorruptedWithDefault) {
        ReplaceFileCorruptionHandler { defaultValue }
      } else {
        null
      },
      serializer = KotlinxDataStoreSerializer(
        defaultValue = defaultValue,
        json = json,
        serializer = serializer,
      ),
    ) {
      File(context.applicationContext.filesDir, "datastore/$fileName")
    }
  }

  fun int(
    fileName: String,
    defaultValue: Int,
    migrations: List<DataMigration<Int>> = emptyList(),
  ): DataStore<Int> {
    return create(
      serializer = Int.serializer(),
      defaultValue = defaultValue,
      fileName = fileName,
      migrations = migrations,
    )
  }

  fun boolean(
    fileName: String,
    defaultValue: Boolean,
    migrations: List<DataMigration<Boolean>> = emptyList(),
  ): DataStore<Boolean> {
    return create(
      serializer = Boolean.serializer(),
      defaultValue = defaultValue,
      fileName = fileName,
      migrations = migrations,
    )
  }
}
