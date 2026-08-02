package voice.features.characterList

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import voice.core.data.BookCharacter
import voice.core.data.BookId
import voice.core.data.repo.BookCharacterRepo
import voice.navigation.Navigator
import java.time.Instant

@AssistedInject
class CharacterListViewModel(
  private val characterRepo: BookCharacterRepo,
  private val navigator: Navigator,
  @Assisted private val bookId: BookId,
) {

  private val scope = MainScope()

  @Composable
  fun viewState(): CharacterListViewState {
    val characters by remember { characterRepo.characters(bookId) }.collectAsState(initial = emptyList())
    return CharacterListViewState(
      characters = characters.mapIndexed { index, it -> it.toItemViewState(position = index + 1) },
    )
  }

  private fun BookCharacter.toItemViewState(position: Int) = CharacterItemViewState(
    id = id,
    name = name,
    description = description,
    position = position,
  )

  fun addCharacter(
    name: String,
    description: String,
  ) {
    if (name.isBlank()) return
    scope.launch {
      val now = Instant.now()
      characterRepo.upsert(
        BookCharacter(
          bookId = bookId,
          name = name.trim(),
          description = description.trim(),
          sortOrder = characterRepo.nextSortOrder(bookId),
          createdAt = now,
          updatedAt = now,
        ),
      )
    }
  }

  fun updateCharacter(
    id: Long,
    name: String,
    description: String,
    position: Int,
  ) {
    if (name.isBlank()) return
    scope.launch {
      val allChars = characterRepo.characters(bookId).firstOrNull() ?: return@launch
      val current = allChars.find { it.id == id } ?: return@launch

      // Reorder: move the character to the target position then reassign sortOrder indices
      val mutable = allChars.toMutableList()
      mutable.remove(current)
      val targetIndex = (position - 1).coerceIn(0, mutable.size)
      mutable.add(targetIndex, current)

      val now = Instant.now()
      // One atomic write of only the rows that actually changed: a partially-applied reorder would
      // leave duplicate sortOrders that never self-heal, and a row-at-a-time loop would make the
      // list visibly step through intermediate orders as Room re-emits after each write.
      val updates = mutable.mapIndexedNotNull { index, char ->
        val updated = if (char.id == id) {
          char.copy(name = name.trim(), description = description.trim(), sortOrder = index, updatedAt = now)
        } else {
          char.copy(sortOrder = index)
        }
        updated.takeIf { it != char }
      }
      characterRepo.updateAll(updates)
    }
  }

  fun deleteCharacter(id: Long) {
    scope.launch { characterRepo.delete(id) }
  }

  fun onClose() {
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(bookId: BookId): CharacterListViewModel
  }
}

data class CharacterListViewState(val characters: List<CharacterItemViewState>)

data class CharacterItemViewState(
  val id: Long,
  val name: String,
  val description: String,
  val position: Int,
)
