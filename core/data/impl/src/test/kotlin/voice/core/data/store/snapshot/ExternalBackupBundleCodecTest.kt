package voice.core.data.store.snapshot

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import org.junit.Test
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.repo.internals.AppDb
import java.time.Instant

class ExternalBackupBundleCodecTest {

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private fun snapshot(
    name: String = "Book",
    sequence: Long = 7,
    savedAtEpochMillis: Long = 99,
  ) = LibrarySnapshot(
    schemaVersion = LibrarySnapshot.SCHEMA_VERSION,
    dbVersion = AppDb.VERSION,
    sequence = sequence,
    savedAtEpochMillis = savedAtEpochMillis,
    totalCount = 1,
    activeCount = 1,
    books = listOf(
      BookContent(
        id = BookId("book"),
        playbackSpeed = 1f,
        skipSilence = false,
        isActive = true,
        lastPlayedAt = Instant.EPOCH,
        author = null,
        name = name,
        addedAt = Instant.EPOCH,
        chapters = listOf(ChapterId("chapter")),
        currentChapter = ChapterId("chapter"),
        positionInChapter = 0,
        cover = null,
        gain = 0f,
        genre = null,
        narrator = null,
        series = null,
        part = null,
      ).toDto(),
    ),
    bookmarks = emptyList(),
    characters = emptyList(),
    chapterNameOverrides = emptyList(),
  )

  @Test
  fun `decodes a bundle whose checksum matches its payload`() {
    val encoded = ExternalBackupBundleCodec.encode(json, snapshot())

    ExternalBackupBundleCodec.decode(json, encoded) shouldBe ExternalBackupBundleDecodeResult.Valid(snapshot())
  }

  @Test
  fun `rejects a bundle whose payload was changed without updating the checksum`() {
    val encoded = ExternalBackupBundleCodec.encode(json, snapshot(name = "Book"))
    val tampered = encoded.replace("\"name\":\"Book\"", "\"name\":\"Other\"")

    ExternalBackupBundleCodec.decode(json, tampered) shouldBe ExternalBackupBundleDecodeResult.Corrupt
  }

  @Test
  fun `released raw snapshot fixture still decodes`() {
    val result = ExternalBackupBundleCodec.decode(json, backupFixture("schema1-raw-snapshot.json"))
    val restored = (result as ExternalBackupBundleDecodeResult.Valid).snapshot

    restored.schemaVersion shouldBe 1
    restored.dbVersion shouldBe 64
    restored.books.single().name shouldBe "Legacy Book"
    restored.books.single().positionInChapter shouldBe 15_000
    restored.bookmarks.single().time shouldBe 5_000
    restored.sessions shouldBe emptyList()
    restored.chapters shouldBe emptyList()
    restored.events shouldBe emptyList()
    restored.hiddenBooks shouldBe emptySet()
    restored.settings shouldBe emptyMap()
  }

  @Test
  fun `released db65 envelope restores every user data category`() {
    val result = ExternalBackupBundleCodec.decode(
      json,
      backupFixture("db65-envelope-without-chapter-file-size.json"),
    )
    val restored = (result as ExternalBackupBundleDecodeResult.Valid).snapshot

    restored.dbVersion shouldBe 65
    restored.books.single().name shouldBe "Compatibility Book"
    restored.books.single().positionInChapter shouldBe 42_000
    restored.bookmarks.single().title shouldBe "Test Bookmark"
    restored.characters.single().name shouldBe "Test Character"
    restored.chapterNameOverrides.single().name shouldBe "Renamed Section"
    restored.sessions.single().durationMs shouldBe 60_000
    restored.chapters.single().relName shouldBe "Compatibility Book/chapter-1.mp3"
    restored.chapters.single().fileSize shouldBe 0
    restored.events.single().positionMs shouldBe 42_000
    restored.hiddenBooks shouldBe emptySet()
    restored.settings shouldBe mapOf("darkTheme" to "false", "seekTime" to "30")
  }

  @Test
  fun `meaningful fingerprint ignores snapshot bookkeeping`() {
    val first = snapshot(sequence = 1, savedAtEpochMillis = 100)
    val second = snapshot(sequence = 2, savedAtEpochMillis = 200)

    ExternalBackupBundleCodec.meaningfulFingerprint(json, first) shouldBe
      ExternalBackupBundleCodec.meaningfulFingerprint(json, second)
  }

  @Test
  fun `meaningful fingerprint changes when user data changes`() {
    ExternalBackupBundleCodec.meaningfulFingerprint(json, snapshot(name = "Book")) shouldNotBe
      ExternalBackupBundleCodec.meaningfulFingerprint(json, snapshot(name = "Other"))
  }

  @Test
  fun `an envelope from a newer format version is refused, not treated as corrupt`() {
    val text = ExternalBackupBundleCodec.encode(json, snapshot())
      .replace("\"formatVersion\":1", "\"formatVersion\":2")
    ExternalBackupBundleCodec.decode(json, text) shouldBe ExternalBackupBundleDecodeResult.NewerFormat
  }
}
