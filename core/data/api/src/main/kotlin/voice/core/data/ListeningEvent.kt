package voice.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
  tableName = "listening_event",
  indices = [
    Index(value = ["bookId", "at"]),
  ],
)
public data class ListeningEvent(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val bookId: BookId,
  val type: Int,
  val chapterId: ChapterId,
  val positionMs: Long,
  val fromPositionMs: Long? = null,
  val at: Instant,
)
