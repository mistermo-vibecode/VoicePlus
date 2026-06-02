package voice.core.data.store.snapshot

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.first

internal class SnapshotRing(private val slots: List<DataStore<LibrarySnapshot?>>) {

  // A slot whose bytes fail to decode throws on read; treat it as empty rather than crashing.
  suspend fun readAll(): List<LibrarySnapshot> = slots.mapNotNull { slot -> runCatching { slot.data.first() }.getOrNull() }

  suspend fun best(): LibrarySnapshot? = readAll().filter { it.activeCount > 0 }.maxByOrNull { it.sequence }

  suspend fun writeNext(snapshot: LibrarySnapshot) {
    val nextSeq = (readAll().maxOfOrNull { it.sequence } ?: 0L) + 1
    val slotIndex = (nextSeq % slots.size).toInt()
    slots[slotIndex].updateData { snapshot.copy(sequence = nextSeq) }
  }
}
