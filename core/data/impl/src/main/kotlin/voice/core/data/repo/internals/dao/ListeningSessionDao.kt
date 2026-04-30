package voice.core.data.repo.internals.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import voice.core.data.BookId
import voice.core.data.ListeningSession

@Dao
public interface ListeningSessionDao {

  @Insert
  public suspend fun insert(session: ListeningSession)

  @Query("SELECT * FROM listening_session WHERE bookId = :bookId ORDER BY startedAt DESC LIMIT 500")
  public fun sessionsForBook(bookId: BookId): Flow<List<ListeningSession>>

  @Query("DELETE FROM listening_session WHERE bookId = :bookId")
  public suspend fun deleteAllForBook(bookId: BookId)

  @Query("SELECT * FROM listening_session ORDER BY startedAt ASC")
  public fun allSessions(): Flow<List<ListeningSession>>
}
