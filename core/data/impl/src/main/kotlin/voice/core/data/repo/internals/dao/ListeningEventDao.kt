package voice.core.data.repo.internals.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import voice.core.data.BookId
import voice.core.data.ListeningEvent

@Dao
public interface ListeningEventDao {
  @Insert
  public suspend fun insert(event: ListeningEvent)

  // UI read cap — events accumulate faster than sessions, so this can truncate sooner than the session log.
  @Query("SELECT * FROM listening_event WHERE bookId = :bookId ORDER BY at DESC LIMIT 500")
  public fun eventsForBook(bookId: BookId): Flow<List<ListeningEvent>>

  // Snapshot capture; the caller caps per book in memory to mirror the UI read cap.
  @Query("SELECT * FROM listening_event ORDER BY at DESC")
  public suspend fun all(): List<ListeningEvent>

  @Query("SELECT COUNT(*) FROM listening_event")
  public fun count(): Flow<Int>

  @Query("DELETE FROM listening_event WHERE bookId = :bookId")
  public suspend fun deleteAllForBook(bookId: BookId)
}
