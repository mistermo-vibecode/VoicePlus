package voice.core.data.store.snapshot

import kotlinx.serialization.Serializable
import voice.core.data.BookCharacter
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Bookmark
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride
import java.io.File
import java.time.Instant
import java.util.UUID

@Serializable
internal data class LibrarySnapshot(
  val schemaVersion: Int,
  val dbVersion: Int = 0,
  val sequence: Long,
  val savedAtEpochMillis: Long,
  val totalCount: Int,
  val activeCount: Int,
  val books: List<BookContentDto>,
  val bookmarks: List<BookmarkDto>,
  val characters: List<BookCharacterDto>,
  val chapterNameOverrides: List<ChapterNameOverrideDto>,
) {
  fun activeIds(): Set<String> = books.filter { it.isActive }.map { it.id }.toSet()

  companion object {
    const val SCHEMA_VERSION = 1
  }
}

@Serializable
internal data class BookContentDto(
  val id: String,
  val playbackSpeed: Float,
  val skipSilence: Boolean,
  val isActive: Boolean,
  val lastPlayedAtEpochMillis: Long,
  val author: String?,
  val name: String,
  val addedAtEpochMillis: Long,
  val chapters: List<String>,
  val currentChapter: String,
  val positionInChapter: Long,
  val coverPath: String?,
  val gain: Float = 0f,
  val genre: String?,
  val narrator: String?,
  val series: String?,
  val part: String?,
  val chapterNameOffset: Int = 0,
)

@Serializable
internal data class BookmarkDto(
  val bookId: String,
  val chapterId: String,
  val title: String?,
  val time: Long,
  val addedAtEpochMillis: Long,
  val setBySleepTimer: Boolean,
  val id: String,
)

@Serializable
internal data class BookCharacterDto(
  val id: Long,
  val bookId: String,
  val name: String,
  val description: String,
  val sortOrder: Int = 0,
  val createdAtEpochMillis: Long,
  val updatedAtEpochMillis: Long,
)

@Serializable
internal data class ChapterNameOverrideDto(
  val chapterId: String,
  val markStartMs: Long,
  val bookId: String,
  val name: String,
)

internal fun BookContent.toDto() = BookContentDto(
  id = id.value,
  playbackSpeed = playbackSpeed,
  skipSilence = skipSilence,
  isActive = isActive,
  lastPlayedAtEpochMillis = lastPlayedAt.toEpochMilli(),
  author = author,
  name = name,
  addedAtEpochMillis = addedAt.toEpochMilli(),
  chapters = chapters.map { it.value },
  currentChapter = currentChapter.value,
  positionInChapter = positionInChapter,
  coverPath = cover?.absolutePath,
  gain = gain,
  genre = genre,
  narrator = narrator,
  series = series,
  part = part,
  chapterNameOffset = chapterNameOffset,
)

// Reconstruction runs BookContent.init{ require(...) }; wrap so one bad row is dropped, not fatal.
internal fun BookContentDto.toBookContentOrNull(): BookContent? = runCatching {
  BookContent(
    id = BookId(id),
    playbackSpeed = playbackSpeed,
    skipSilence = skipSilence,
    isActive = isActive,
    lastPlayedAt = Instant.ofEpochMilli(lastPlayedAtEpochMillis),
    author = author,
    name = name,
    addedAt = Instant.ofEpochMilli(addedAtEpochMillis),
    chapters = chapters.map { ChapterId(it) },
    currentChapter = ChapterId(currentChapter),
    positionInChapter = positionInChapter,
    cover = coverPath?.let { File(it) },
    gain = gain,
    genre = genre,
    narrator = narrator,
    series = series,
    part = part,
    chapterNameOffset = chapterNameOffset,
  )
}.getOrNull()

internal fun Bookmark.toDto() = BookmarkDto(
  bookId = bookId.value,
  chapterId = chapterId.value,
  title = title,
  time = time,
  addedAtEpochMillis = addedAt.toEpochMilli(),
  setBySleepTimer = setBySleepTimer,
  id = id.value.toString(),
)

internal fun BookmarkDto.toBookmark() = Bookmark(
  bookId = BookId(bookId),
  chapterId = ChapterId(chapterId),
  title = title,
  time = time,
  addedAt = Instant.ofEpochMilli(addedAtEpochMillis),
  setBySleepTimer = setBySleepTimer,
  id = Bookmark.Id(UUID.fromString(id)),
)

internal fun BookCharacter.toDto() = BookCharacterDto(
  id = id,
  bookId = bookId.value,
  name = name,
  description = description,
  sortOrder = sortOrder,
  createdAtEpochMillis = createdAt.toEpochMilli(),
  updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun BookCharacterDto.toBookCharacter() = BookCharacter(
  id = id,
  bookId = BookId(bookId),
  name = name,
  description = description,
  sortOrder = sortOrder,
  createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
  updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun ChapterNameOverride.toDto() = ChapterNameOverrideDto(chapterId, markStartMs, bookId, name)

internal fun ChapterNameOverrideDto.toOverride() = ChapterNameOverride(chapterId, markStartMs, bookId, name)
