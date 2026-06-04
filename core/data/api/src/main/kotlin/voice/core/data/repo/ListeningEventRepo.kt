package voice.core.data.repo

import kotlinx.coroutines.flow.Flow
import voice.core.data.BookId
import voice.core.data.ListeningEvent

public interface ListeningEventRepo {
  public suspend fun addEvent(event: ListeningEvent)
  public fun events(bookId: BookId): Flow<List<ListeningEvent>>
  public suspend fun deleteAllForBook(bookId: BookId)
}
