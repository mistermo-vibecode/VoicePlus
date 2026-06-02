package voice.core.data.repo.internals.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import voice.core.data.Bookmark
import voice.core.data.ChapterId

@Dao
public interface BookmarkDao {

  @Query("DELETE FROM bookmark2 WHERE id = :id")
  public suspend fun deleteBookmark(id: Bookmark.Id)

  // A change trigger for the snapshot writer: Room re-emits on any insert/update/delete to the table.
  @Query("SELECT COUNT(*) FROM bookmark2")
  public fun count(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  public suspend fun addBookmark(bookmark: Bookmark)

  @Query("SELECT * FROM bookmark2 WHERE chapterId IN(:chapters)")
  public suspend fun allForChapters(chapters: List<@JvmSuppressWildcards ChapterId>): List<Bookmark>

  @Query("SELECT * FROM bookmark2")
  public suspend fun all(): List<Bookmark>
}
