package voice.core.data.store.snapshot

import io.kotest.matchers.shouldBe
import org.junit.Test

class RotationGuardTest {

  private fun snap(
    active: List<String>,
    inactive: List<String> = emptyList(),
    // User-authored rows, keyed by owning bookId, expressed as a count per book. Each count produces that
    // many sessions/bookmarks/characters/overrides for the book (so a wipe = dropping the entries).
    sessions: Map<String, Int> = emptyMap(),
    bookmarks: Map<String, Int> = emptyMap(),
    characters: Map<String, Int> = emptyMap(),
    overrides: Map<String, Int> = emptyMap(),
  ): LibrarySnapshot {
    val books = (active.map { it to true } + inactive.map { it to false })
      .map { (id, isActive) -> dto(id, isActive) }
    return LibrarySnapshot(
      schemaVersion = 1, sequence = 1, savedAtEpochMillis = 0,
      totalCount = books.size, activeCount = active.size,
      books = books,
      bookmarks = bookmarks.flatMap { (bookId, n) -> List(n) { i -> bookmarkDto(bookId, "$bookId-bm$i") } },
      characters = characters.flatMap { (bookId, n) -> List(n) { i -> characterDto(bookId, i.toLong()) } },
      chapterNameOverrides = overrides.flatMap { (bookId, n) -> List(n) { i -> overrideDto(bookId, i.toLong()) } },
      sessions = sessions.flatMap { (bookId, n) -> List(n) { i -> sessionDto(bookId, i.toLong()) } },
    )
  }

  private fun bookmarkDto(
    bookId: String,
    id: String,
  ) = BookmarkDto(
    bookId = bookId,
    chapterId = "c$bookId",
    title = null,
    time = 0,
    addedAtEpochMillis = 0,
    setBySleepTimer = false,
    id = id,
  )

  private fun characterDto(
    bookId: String,
    id: Long,
  ) = BookCharacterDto(
    id = id,
    bookId = bookId,
    name = "n",
    description = "d",
    createdAtEpochMillis = 0,
    updatedAtEpochMillis = 0,
  )

  private fun overrideDto(
    bookId: String,
    markStartMs: Long,
  ) = ChapterNameOverrideDto(chapterId = "c$bookId", markStartMs = markStartMs, bookId = bookId, name = "ovr")

  private fun sessionDto(
    bookId: String,
    id: Long,
  ) = ListeningSessionDto(
    id = id, bookId = bookId, chapterId = "c$bookId", startedAtEpochMillis = 0,
    endedAtEpochMillis = 0, durationMs = 0, startPositionMs = 0, endPositionMs = 0, endChapterId = null,
  )

  private fun dto(
    id: String,
    isActive: Boolean,
  ) = BookContentDto(
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

  // --- user-authored data collapse (the on-device data-loss signature) ---

  @Test
  fun `user data craters while books intact - declined`() {
    // 21 books, each with a session + bookmark + character + override; then every user-authored row is
    // wiped but the books survive. This is exactly the device evidence and must veto.
    val ids = (1..21).map { "b$it" }
    val counts = ids.associateWith { 1 }
    val rich = snap(active = ids, sessions = counts, bookmarks = counts, characters = counts, overrides = counts)
    val wiped = snap(active = ids)
    RotationGuard.isSuspiciousShrink(rich, wiped, excludedIds = emptySet()) shouldBe true
  }

  @Test
  fun `partial user data loss below ratio - written`() {
    // Pruning a minority of sessions/bookmarks is an organic edit, not a wipe.
    val ids = (1..10).map { "b$it" }
    val best = snap(active = ids, sessions = ids.associateWith { 1 }, bookmarks = ids.associateWith { 1 })
    val afterPrune = snap(
      active = ids,
      sessions = (1..8).map { "b$it" }.associateWith { 1 },
      bookmarks = ids.associateWith { 1 },
    )
    RotationGuard.isSuspiciousShrink(best, afterPrune, excludedIds = emptySet()) shouldBe false
  }

  @Test
  fun `user data loss explained by book removal - written`() {
    // The user deletes half their books; the sessions/bookmarks of those books go with them. That loss is
    // EXPLAINED by the missing books, so it must NOT trip the user-data guard. (Book-shrink guard handles
    // the active-book half; here the removed books are excluded so neither guard fires.)
    val all = (1..10).map { "b$it" }
    val kept = (1..5).map { "b$it" }
    val removed = (6..10).map { "b$it" }
    val best = snap(active = all, sessions = all.associateWith { 2 })
    val afterDelete = snap(active = kept, sessions = kept.associateWith { 2 })
    RotationGuard.isSuspiciousShrink(best, afterDelete, excludedIds = removed.toSet()) shouldBe false
  }

  @Test
  fun `first run with no prior user data - written`() {
    val best = snap(active = listOf("a", "b"))
    val incoming = snap(active = listOf("a", "b"), sessions = mapOf("a" to 3, "b" to 2))
    RotationGuard.isSuspiciousShrink(best, incoming, excludedIds = emptySet()) shouldBe false
  }

  @Test
  fun `user data growth - written`() {
    val best = snap(active = listOf("a"), sessions = mapOf("a" to 2), bookmarks = mapOf("a" to 1))
    val grown = snap(active = listOf("a"), sessions = mapOf("a" to 5), bookmarks = mapOf("a" to 3))
    RotationGuard.isSuspiciousShrink(best, grown, excludedIds = emptySet()) shouldBe false
  }

  @Test
  fun `user clears all data of an excluded book - written`() {
    // User intentionally removed a book (excluded) and its user data; books-only book remains untouched.
    val best = snap(active = listOf("a", "b"), sessions = mapOf("a" to 3, "b" to 3))
    val afterClear = snap(active = listOf("a"), inactive = listOf("b"), sessions = mapOf("a" to 3))
    RotationGuard.isSuspiciousShrink(best, afterClear, excludedIds = setOf("b")) shouldBe false
  }

  @Test
  fun `user clears half of one book's data (deliberate edit) - written`() {
    // A 50% drop is a normal/intentional edit, NOT the near-total wipe — under the near-zero policy it must
    // propagate to the backup (this is the over-veto we deliberately removed: it would FAIL on the old 0.5 rule).
    val best = snap(active = listOf("a"), sessions = mapOf("a" to 10))
    val halved = snap(active = listOf("a"), sessions = mapOf("a" to 5))
    RotationGuard.isSuspiciousShrink(best, halved, excludedIds = emptySet()) shouldBe false
  }

  @Test
  fun `near-total user data wipe of a substantial book - declined`() {
    // ~97% of a book's rows vanish in one step while the book survives: the wipe signature -> veto.
    val best = snap(active = listOf("a"), sessions = mapOf("a" to 30))
    val wiped = snap(active = listOf("a"), sessions = mapOf("a" to 1))
    RotationGuard.isSuspiciousShrink(best, wiped, excludedIds = emptySet()) shouldBe true
  }

  @Test
  fun `loss exactly at the near-total threshold - declined`() {
    val best = snap(active = listOf("a"), sessions = mapOf("a" to 100))
    val incoming = snap(active = listOf("a"), sessions = mapOf("a" to 10)) // 90% lost
    RotationGuard.isSuspiciousShrink(best, incoming, excludedIds = emptySet()) shouldBe true
  }

  @Test
  fun `large loss just under the near-total threshold - written`() {
    val best = snap(active = listOf("a"), sessions = mapOf("a" to 100))
    val incoming = snap(active = listOf("a"), sessions = mapOf("a" to 11)) // 89% lost
    RotationGuard.isSuspiciousShrink(best, incoming, excludedIds = emptySet()) shouldBe false
  }

  @Test
  fun `tiny user data fully cleared (below baseline) - written`() {
    // Only a handful of rows ever existed; clearing them is not a catastrophe worth freezing the backup over.
    val best = snap(active = listOf("a"), sessions = mapOf("a" to 3))
    val cleared = snap(active = listOf("a"))
    RotationGuard.isSuspiciousShrink(best, cleared, excludedIds = emptySet()) shouldBe false
  }

  @Test
  fun `drip erosion is vetoed against the high-water-mark, not just the latest generation`() {
    // A data-rich generation is still retained while the latest has already eroded (a prior allowed
    // sub-threshold step). A further near-total drop must veto against the PEAK, not the eroded latest —
    // otherwise repeated sub-90% losses would ratchet the data to zero with no veto ever firing.
    val rich = snap(active = listOf("a"), sessions = mapOf("a" to 100))
    val eroded = snap(active = listOf("a"), sessions = mapOf("a" to 15))
    val incoming = snap(active = listOf("a"), sessions = mapOf("a" to 1))
    RotationGuard.isSuspiciousShrink(listOf(rich, eroded), incoming, excludedIds = emptySet()) shouldBe true
  }

  @Test
  fun `single-book wipe diluted by a large library is allowed (aggregate-policy limit)`() {
    // DOCUMENTED LIMIT of the aggregate near-zero policy (chosen over a per-book rule to avoid vetoing
    // deliberate single-book clears): one book's data wiped among many is a tiny fraction of the total, so it
    // is not vetoed. A per-book policy would catch it but would also fight intentional single-book clears.
    val ids = (1..50).map { "b$it" }
    val best = snap(active = ids, sessions = ids.associateWith { 4 })
    val oneWiped = snap(active = ids, sessions = (2..50).map { "b$it" }.associateWith { 4 })
    RotationGuard.isSuspiciousShrink(best, oneWiped, excludedIds = emptySet()) shouldBe false
  }
}
