package voice.features.playbackScreen.characterlist

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

  fun addCharacter(name: String, description: String) {
    if (name.isBlank()) return
    scope.launch {
      val existing = characterRepo.characters(bookId).firstOrNull() ?: emptyList()
      val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
      val now = Instant.now()
      characterRepo.upsert(
        BookCharacter(
          bookId = bookId,
          name = name.trim(),
          description = description.trim(),
          sortOrder = nextOrder,
          createdAt = now,
          updatedAt = now,
        ),
      )
    }
  }

  fun updateCharacter(id: Long, name: String, description: String, position: Int) {
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
      mutable.forEachIndexed { index, char ->
        val updated = if (char.id == id) {
          char.copy(name = name.trim(), description = description.trim(), sortOrder = index, updatedAt = now)
        } else {
          char.copy(sortOrder = index)
        }
        characterRepo.upsert(updated)
      }
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

data class CharacterListViewState(
  val characters: List<CharacterItemViewState>,
)

data class CharacterItemViewState(
  val id: Long,
  val name: String,
  val description: String,
  val position: Int,
)
