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

  @Query("SELECT * FROM book_character WHERE bookId = :bookId ORDER BY sortOrder ASC, createdAt ASC")
  public fun charactersForBook(bookId: BookId): Flow<List<BookCharacter>>

  @Query("SELECT COUNT(*) FROM book_character WHERE bookId = :bookId")
  public fun countForBook(bookId: BookId): Flow<Int>

  @Query("DELETE FROM book_character WHERE id = :id")
  public suspend fun delete(id: Long)
}
