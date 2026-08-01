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
  fun `legacy raw snapshot json still decodes`() {
    val legacy = json.encodeToString(LibrarySnapshot.serializer(), snapshot())

    ExternalBackupBundleCodec.decode(json, legacy) shouldBe ExternalBackupBundleDecodeResult.Valid(snapshot())
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
}
