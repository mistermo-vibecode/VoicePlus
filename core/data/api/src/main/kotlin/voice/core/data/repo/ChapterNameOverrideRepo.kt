package voice.core.data.repo

import kotlinx.coroutines.flow.Flow
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride

public interface ChapterNameOverrideRepo {
  public fun overridesForBook(bookId: BookId): Flow<List<ChapterNameOverride>>
  public suspend fun set(
    chapterId: ChapterId,
    markStartMs: Long,
    bookId: BookId,
    name: String,
  )
  public suspend fun delete(
    chapterId: ChapterId,
    markStartMs: Long,
  )
  public suspend fun deleteAll(bookId: BookId)
}
