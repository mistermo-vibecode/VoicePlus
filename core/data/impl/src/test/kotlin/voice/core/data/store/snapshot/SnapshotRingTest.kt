package voice.core.data.store.snapshot

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import voice.core.data.repo.internals.MemoryDataStore

class SnapshotRingTest {

  private val slot0 = MemoryDataStore<LibrarySnapshot?>(null)
  private val slot1 = MemoryDataStore<LibrarySnapshot?>(null)
  private val slot2 = MemoryDataStore<LibrarySnapshot?>(null)
  private val ring = SnapshotRing(listOf(slot0, slot1, slot2))

  private fun snap(
    seq: Long,
    active: Int,
  ) = LibrarySnapshot(
    schemaVersion = 1, sequence = seq, savedAtEpochMillis = 0,
    totalCount = active, activeCount = active,
    books = (1..active).map {
      BookContentDto("b$it", 1f, false, true, 0, null, "b$it", 0, listOf("c"), "c", 0, null, 0f, null, null, null, null)
    },
    bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
  )

  @Test
  fun `writeNext assigns increasing sequence and rotates slot = seq mod K`() = runTest {
    ring.writeNext(snap(seq = 0, active = 1)) // -> seq 1, slot 1
    ring.writeNext(snap(seq = 0, active = 1)) // -> seq 2, slot 2
    ring.writeNext(snap(seq = 0, active = 1)) // -> seq 3, slot 0
    ring.writeNext(snap(seq = 0, active = 1)) // -> seq 4, slot 1 (overwrites first)

    slot0.data.first()!!.sequence shouldBe 3L
    slot1.data.first()!!.sequence shouldBe 4L
    slot2.data.first()!!.sequence shouldBe 2L
  }

  @Test
  fun `best returns the highest-sequence snapshot with active books`() = runTest {
    slot0.updateData { snap(seq = 7, active = 0) }
    slot1.updateData { snap(seq = 5, active = 2) }
    slot2.updateData { snap(seq = 6, active = 3) }
    ring.best()!!.sequence shouldBe 6L
  }

  @Test
  fun `best is null when all slots empty or inactive`() = runTest {
    ring.best() shouldBe null
  }
}
