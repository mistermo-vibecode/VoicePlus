package voice.features.settings.hiddenbooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.BookRepository
import voice.core.data.store.ExcludedBooksStore
import voice.navigation.Navigator

@Inject
class HiddenBooksViewModel(
  private val bookRepo: BookRepository,
  private val contentRepo: BookContentRepo,
  @ExcludedBooksStore
  private val excludedBooksStore: DataStore<Set<String>>,
  private val navigator: Navigator,
) {

  private val scope = MainScope()

  @Composable
  fun viewState(): HiddenBooksViewState {
    val state by remember {
      combine(excludedBooksStore.data, bookRepo.flow()) { excludedIds, books ->
        val hiddenBooks = books
          .filter { it.id.value in excludedIds }
          .map { HiddenBookItem(id = it.id.value, name = it.content.name) }
          .sortedBy { it.name }
        // Also include excluded IDs for books not yet in the repo (e.g. file deleted)
        val repoIds = books.map { it.id.value }.toSet()
        val orphanItems = excludedIds
          .filter { it !in repoIds }
          .map { HiddenBookItem(id = it, name = it.substringAfterLast("/").substringAfterLast(":")) }
        HiddenBooksViewState(hiddenBooks + orphanItems)
      }
    }.collectAsState(initial = HiddenBooksViewState(emptyList()))
    return state
  }

  fun restore(id: String) {
    scope.launch {
      excludedBooksStore.updateData { it - id }
      contentRepo.all()
        .firstOrNull { it.id.value == id }
        ?.let { contentRepo.put(it.copy(isActive = true)) }
    }
  }

  fun restoreAll() {
    scope.launch {
      val allIds = excludedBooksStore.data.first()
      excludedBooksStore.updateData { emptySet() }
      contentRepo.all()
        .filter { it.id.value in allIds }
        .forEach { contentRepo.put(it.copy(isActive = true)) }
    }
  }

  fun onClose() {
    navigator.goBack()
  }
}

data class HiddenBooksViewState(val books: List<HiddenBookItem>)

data class HiddenBookItem(
  val id: String,
  val name: String,
)
