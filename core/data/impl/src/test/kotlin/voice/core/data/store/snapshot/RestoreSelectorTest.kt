package voice.core.data.store.snapshot

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.data.repo.internals.AppDb

class RestoreSelectorTest {

  private fun snap(
    seq: Long,
    activeIds: List<String>,
    dbVersion: Int = AppDb.VERSION,
    schemaVersion: Int = LibrarySnapshot.SCHEMA_VERSION,
  ) = LibrarySnapshot(
    schemaVersion = schemaVersion, dbVersion = dbVersion, sequence = seq, savedAtEpochMillis = 0,
    totalCount = activeIds.size, activeCount = activeIds.size,
    books = activeIds.map {
      BookContentDto(it, 1f, false, true, 0, null, it, 0, listOf("c"), "c", 0, null, 0f, null, null, null, null)
    },
    bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
  )

  @Test
  fun `empty Room restores the newest non-empty candidate`() {
    RestoreSelector.select(
      liveTotal = 0,
      candidates = listOf(snap(1, listOf("a")), snap(3, listOf("a", "b"))),
    )!!.sequence shouldBe 3L
  }

  @Test
  fun `non-empty Room is never auto-restored, even when all books are inactive`() {
    // The user removed their folders, leaving inactive rows. Auto-restoring would fight that removal.
    RestoreSelector.select(
      liveTotal = 2,
      candidates = listOf(snap(1, listOf("a", "b"))),
    ) shouldBe null
  }

  @Test
  fun `no non-empty candidate does not restore`() {
    RestoreSelector.select(liveTotal = 0, candidates = emptyList()) shouldBe null
  }

  @Test
  fun `an empty Room with only an all-inactive candidate does not restore`() {
    RestoreSelector.select(liveTotal = 0, candidates = listOf(snap(1, emptyList()))) shouldBe null
  }

  @Test
  fun `candidate from a newer db schema is rejected`() {
    RestoreSelector.select(
      liveTotal = 0,
      candidates = listOf(snap(5, listOf("a"), dbVersion = AppDb.VERSION + 1)),
    ) shouldBe null
  }

  @Test
  fun `candidate from a newer storage schemaVersion is rejected`() {
    RestoreSelector.select(
      liveTotal = 0,
      candidates = listOf(snap(5, listOf("a"), schemaVersion = LibrarySnapshot.SCHEMA_VERSION + 1)),
    ) shouldBe null
  }
}
