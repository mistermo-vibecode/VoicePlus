package voice.core.data.repo

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.Flow
import voice.core.data.BookId
import voice.core.data.ListeningEvent
import voice.core.data.repo.internals.dao.ListeningEventDao

@ContributesBinding(AppScope::class)
public class ListeningEventRepoImpl
internal constructor(private val dao: ListeningEventDao) : ListeningEventRepo {
  override suspend fun addEvent(event: ListeningEvent) {
    dao.insert(event)
  }

  override fun events(bookId: BookId): Flow<List<ListeningEvent>> = dao.eventsForBook(bookId)

  override suspend fun deleteAllForBook(bookId: BookId) {
    dao.deleteAllForBook(bookId)
  }
}
