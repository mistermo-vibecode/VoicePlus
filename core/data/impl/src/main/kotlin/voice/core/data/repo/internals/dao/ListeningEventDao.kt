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

  @Query("DELETE FROM listening_event WHERE bookId = :bookId")
  public suspend fun deleteAllForBook(bookId: BookId)
}
