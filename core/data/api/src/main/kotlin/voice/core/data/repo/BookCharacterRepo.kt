package voice.core.data.repo

import kotlinx.coroutines.flow.Flow
import voice.core.data.BookCharacter
import voice.core.data.BookId

public interface BookCharacterRepo {
  public suspend fun upsert(character: BookCharacter)
  public fun characters(bookId: BookId): Flow<List<BookCharacter>>
  public fun characterCount(bookId: BookId): Flow<Int>
  public suspend fun delete(id: Long)
}
