package voice.core.data.repo.internals.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import voice.core.data.ChapterNameOverride

@Dao
public interface ChapterNameOverrideDao {

  @Query("SELECT * FROM chapter_name_overrides WHERE bookId = :bookId")
  public fun overridesForBook(bookId: String): Flow<List<ChapterNameOverride>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  public suspend fun insert(override: ChapterNameOverride)

  @Query("DELETE FROM chapter_name_overrides WHERE chapterId = :chapterId AND markStartMs = :markStartMs")
  public suspend fun delete(chapterId: String, markStartMs: Long)

  @Query("DELETE FROM chapter_name_overrides WHERE bookId = :bookId")
  public suspend fun deleteAll(bookId: String)
}
