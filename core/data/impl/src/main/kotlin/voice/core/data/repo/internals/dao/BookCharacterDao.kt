package voice.core.data.repo.internals.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import voice.core.data.BookCharacter
import voice.core.data.BookId

@Dao
public interface BookCharacterDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  public suspend fun insert(character: BookCharacter)

  @Update
  public suspend fun update(character: BookCharacter)

  // Room runs a list-parameter @Update as ONE transaction, so a reorder either lands whole or not
  // at all — a process death mid-reorder can't leave duplicate sortOrders behind.
  @Update
  public suspend fun update(characters: List<BookCharacter>)

  @Query("SELECT * FROM book_character WHERE bookId = :bookId ORDER BY sortOrder ASC, createdAt ASC")
  public fun charactersForBook(bookId: BookId): Flow<List<BookCharacter>>

  @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM book_character WHERE bookId = :bookId")
  public suspend fun nextSortOrder(bookId: BookId): Int

  @Query("SELECT COUNT(*) FROM book_character WHERE bookId = :bookId")
  public fun countForBook(bookId: BookId): Flow<Int>

  @Query("DELETE FROM book_character WHERE id = :id")
  public suspend fun delete(id: Long)

  @Query("SELECT * FROM book_character")
  public suspend fun all(): List<BookCharacter>

  // A change trigger for the snapshot writer: Room re-emits on any insert/update/delete to the table.
  @Query("SELECT COUNT(*) FROM book_character")
  public fun count(): Flow<Int>
}
