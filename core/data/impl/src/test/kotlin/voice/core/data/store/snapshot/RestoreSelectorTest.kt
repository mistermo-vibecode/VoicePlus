package voice.core.data.store.snapshot

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.data.repo.internals.AppDb

class RestoreSelectorTest {

  private fun snap(seq: Long, activeIds: List<String>, dbVersion: Int = AppDb.VERSION) = LibrarySnapshot(
    schemaVersion = 1, dbVersion = dbVersion, sequence = seq, savedAtEpochMillis = 0,
    totalCount = activeIds.size, activeCount = activeIds.size,
    books = activeIds.map {
      BookContentDto(it, 1f, false, true, 0, null, it, 0, listOf("c"), "c", 0, null, 0f, null, null, null, null)
    },
    bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
  )

  @Test
  fun `empty Room restores the newest non-empty candidate`() {
    RestoreSelector.select(
      liveTotal = 0, liveActiveIds = emptySet(), excludedIds = emptySet(),
      candidates = listOf(snap(1, listOf("a")), snap(3, listOf("a", "b"))),
    )!!.sequence shouldBe 3L
  }

  @Test
  fun `full active-collapse not explained by exclusions restores`() {
    RestoreSelector.select(
      liveTotal = 2, liveActiveIds = emptySet(), excludedIds = emptySet(),
      candidates = listOf(snap(1, listOf("a", "b"))),
    )!!.sequence shouldBe 1L
  }

  @Test
  fun `collapse fully explained by exclusions does not restore`() {
    RestoreSelector.select(
      liveTotal = 2, liveActiveIds = emptySet(), excludedIds = setOf("a", "b"),
      candidates = listOf(snap(1, listOf("a", "b"))),
    ) shouldBe null
  }

  @Test
  fun `healthy live library does not restore`() {
    RestoreSelector.select(
      liveTotal = 2, liveActiveIds = setOf("a", "b"), excludedIds = emptySet(),
      candidates = listOf(snap(1, listOf("a", "b"))),
    ) shouldBe null
  }

  @Test
  fun `no non-empty candidate does not restore`() {
    RestoreSelector.select(0, emptySet(), emptySet(), emptyList()) shouldBe null
  }

  @Test
  fun `candidate from a newer schema is rejected`() {
    RestoreSelector.select(
      liveTotal = 0, liveActiveIds = emptySet(), excludedIds = emptySet(),
      candidates = listOf(snap(5, listOf("a"), dbVersion = AppDb.VERSION + 1)),
    ) shouldBe null
  }
}
