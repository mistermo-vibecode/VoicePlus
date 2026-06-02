package voice.core.data.store.snapshot

import io.kotest.matchers.shouldBe
import org.junit.Test

class RotationGuardTest {

  private fun snap(active: List<String>, inactive: List<String> = emptyList()): LibrarySnapshot {
    val books = (active.map { it to true } + inactive.map { it to false })
      .map { (id, isActive) -> dto(id, isActive) }
    return LibrarySnapshot(
      schemaVersion = 1, sequence = 1, savedAtEpochMillis = 0,
      totalCount = books.size, activeCount = active.size,
      books = books, bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
    )
  }

  private fun dto(id: String, isActive: Boolean) = BookContentDto(
    id = id, playbackSpeed = 1f, skipSilence = false, isActive = isActive,
    lastPlayedAtEpochMillis = 0, author = null, name = id, addedAtEpochMillis = 0,
    chapters = listOf("c"), currentChapter = "c", positionInChapter = 0, coverPath = null,
    gain = 0f, genre = null, narrator = null, series = null, part = null,
  )

  @Test
  fun `no prior snapshot - always writes`() {
    RotationGuard.isSuspiciousShrink(best = null, incoming = snap(active = emptyList()), excludedIds = emptySet()) shouldBe false
  }

  @Test
  fun `bug wipe of full library - declined`() {
    val best = snap(active = listOf("a", "b", "c", "d"))
    val wiped = snap(active = emptyList(), inactive = listOf("a", "b", "c", "d"))
    RotationGuard.isSuspiciousShrink(best, wiped, excludedIds = emptySet()) shouldBe true
  }

  @Test
  fun `user deletes one book of two (excluded) - written`() {
    val best = snap(active = listOf("a", "b"))
    val afterDelete = snap(active = listOf("a"), inactive = listOf("b"))
    RotationGuard.isSuspiciousShrink(best, afterDelete, excludedIds = setOf("b")) shouldBe false
  }

  @Test
  fun `user empties whole small library (all excluded) - written, no zombie loop`() {
    val best = snap(active = listOf("a", "b"))
    val empty = snap(active = emptyList(), inactive = listOf("a", "b"))
    RotationGuard.isSuspiciousShrink(best, empty, excludedIds = setOf("a", "b")) shouldBe false
  }

  @Test
  fun `partial unexplained shrink above ratio - declined`() {
    val best = snap(active = (1..10).map { "b$it" })
    val afterFlap = snap(active = (1..4).map { "b$it" }, inactive = (5..10).map { "b$it" })
    RotationGuard.isSuspiciousShrink(best, afterFlap, excludedIds = emptySet()) shouldBe true
  }

  @Test
  fun `small unexplained shrink below ratio - written`() {
    val best = snap(active = (1..10).map { "b$it" })
    val afterOneGone = snap(active = (1..9).map { "b$it" }, inactive = listOf("b10"))
    RotationGuard.isSuspiciousShrink(best, afterOneGone, excludedIds = emptySet()) shouldBe false
  }
}
