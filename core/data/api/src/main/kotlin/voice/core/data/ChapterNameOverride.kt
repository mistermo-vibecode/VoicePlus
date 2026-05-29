package voice.core.data

import androidx.room.Entity

@Entity(
  tableName = "chapter_name_overrides",
  primaryKeys = ["chapterId", "markStartMs"],
)
public data class ChapterNameOverride(
  public val chapterId: String,    // ChapterId.value (URI string)
  public val markStartMs: Long,    // unique within a Chapter — composite PK with chapterId
  public val bookId: String,       // BookId.value — for deleteAll(bookId)
  public val name: String,
)
