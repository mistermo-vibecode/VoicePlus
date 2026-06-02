package voice.core.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
  tableName = "chapter_name_overrides",
  primaryKeys = ["chapterId", "markStartMs"],
  // bookId is the lookup key for overridesForBook(...)/deleteAll(bookId); index it so those
  // queries don't full-scan the table. The composite PK can't satisfy a bookId-only predicate.
  indices = [Index("bookId")],
)
public data class ChapterNameOverride(
  public val chapterId: String, // ChapterId.value (URI string)
  public val markStartMs: Long, // unique within a Chapter — composite PK with chapterId
  public val bookId: String, // BookId.value — for deleteAll(bookId)
  public val name: String,
)
