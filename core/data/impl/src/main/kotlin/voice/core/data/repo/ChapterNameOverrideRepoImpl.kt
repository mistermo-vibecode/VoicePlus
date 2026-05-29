package voice.core.data.repo

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.Flow
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride
import voice.core.data.repo.internals.dao.ChapterNameOverrideDao

@ContributesBinding(AppScope::class)
public class ChapterNameOverrideRepoImpl(
  private val dao: ChapterNameOverrideDao,
) : ChapterNameOverrideRepo {

  override fun overridesForBook(bookId: BookId): Flow<List<ChapterNameOverride>> =
    dao.overridesForBook(bookId.value)

  override suspend fun set(chapterId: ChapterId, markStartMs: Long, bookId: BookId, name: String) {
    dao.insert(ChapterNameOverride(chapterId.value, markStartMs, bookId.value, name))
  }

  override suspend fun delete(chapterId: ChapterId, markStartMs: Long) {
    dao.delete(chapterId.value, markStartMs)
  }

  override suspend fun deleteAll(bookId: BookId) {
    dao.deleteAll(bookId.value)
  }
}
