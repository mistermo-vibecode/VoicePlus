package voice.core.data.repo

import kotlinx.coroutines.flow.Flow
import voice.core.data.BookCharacter
import voice.core.data.BookId

public interface BookCharacterRepo {
  public suspend fun upsert(character: BookCharacter)

  /** Updates every character atomically — the reorder path, where partial application would scramble the order. */
  public suspend fun updateAll(characters: List<BookCharacter>)
  public fun characters(bookId: BookId): Flow<List<BookCharacter>>
  public fun characterCount(bookId: BookId): Flow<Int>

  /** The sortOrder to give a character appended to [bookId]'s roster. */
  public suspend fun nextSortOrder(bookId: BookId): Int
  public suspend fun delete(id: Long)
}
