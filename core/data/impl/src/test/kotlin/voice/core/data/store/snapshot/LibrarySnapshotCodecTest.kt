package voice.core.data.store.snapshot

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.Test
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.ChapterId
import java.time.Instant

class LibrarySnapshotCodecTest {

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  private fun book(id: String, active: Boolean): BookContent = BookContent(
    id = BookId(id), playbackSpeed = 1f, skipSilence = false, isActive = active,
    lastPlayedAt = Instant.ofEpochMilli(10), author = null, name = id,
    addedAt = Instant.ofEpochMilli(20), chapters = listOf(ChapterId("ch-$id")),
    currentChapter = ChapterId("ch-$id"), positionInChapter = 5, cover = null,
    gain = 0f, genre = null, narrator = null, series = null, part = null,
  )

  @Test
  fun `BookContent round-trips through dto`() {
    val original = book("b1", active = true)
    val restored = original.toDto().toBookContentOrNull()
    restored shouldBe original
  }

  @Test
  fun `LibrarySnapshot round-trips through json`() {
    val snap = LibrarySnapshot(
      schemaVersion = LibrarySnapshot.SCHEMA_VERSION, sequence = 3, savedAtEpochMillis = 99,
      totalCount = 1, activeCount = 1,
      books = listOf(book("b1", true).toDto()),
      bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
    )
    val text = json.encodeToString(LibrarySnapshot.serializer(), snap)
    json.decodeFromString(LibrarySnapshot.serializer(), text) shouldBe snap
  }

  @Test
  fun `a dto violating BookContent invariant maps to null, not a crash`() {
    val bad = book("b1", true).toDto().copy(currentChapter = "ch-does-not-exist")
    bad.toBookContentOrNull().shouldBeNull()
  }

  @Test
  fun `unknown json keys are tolerated`() {
    val text = """{"schemaVersion":1,"sequence":1,"savedAtEpochMillis":0,"totalCount":0,"activeCount":0,""" +
      """"books":[],"bookmarks":[],"characters":[],"chapterNameOverrides":[],"futureField":123}"""
    json.decodeFromString(LibrarySnapshot.serializer(), text).sequence shouldBe 1L
  }

  @Test
  fun `activeIds reflects only active books`() {
    val snap = LibrarySnapshot(
      schemaVersion = 1, sequence = 1, savedAtEpochMillis = 0, totalCount = 2, activeCount = 1,
      books = listOf(book("b1", true).toDto(), book("b2", false).toDto()),
      bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
    )
    snap.activeIds() shouldBe setOf("b1")
  }
}
