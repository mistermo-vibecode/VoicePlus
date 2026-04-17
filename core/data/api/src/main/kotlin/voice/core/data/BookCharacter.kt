package voice.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "book_character")
public data class BookCharacter(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val bookId: BookId,
  val name: String,
  val description: String,
  @androidx.room.ColumnInfo(defaultValue = "0")
  val sortOrder: Int = 0,
  val createdAt: Instant,
  val updatedAt: Instant,
)
