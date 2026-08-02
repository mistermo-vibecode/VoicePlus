package voice.features.hiddenBooks

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.common.RetainedViewModel
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.MediaScanWaiter
import voice.core.data.repo.BookContentRepo
import voice.core.data.store.ExcludedBooksStore
import voice.navigation.Navigator

@Inject
class HiddenBooksViewModel(
  private val contentRepo: BookContentRepo,
  @ExcludedBooksStore
  private val excludedBooksStore: DataStore<Set<String>>,
  private val mediaScanWaiter: MediaScanWaiter,
  private val navigator: Navigator,
) : RetainedViewModel() {

  @Composable
  fun viewState(): HiddenBooksViewState {
    val state by remember {
      // Content rows, not BookRepository.flow(): that filters out inactive books, and hiding a book
      // makes it inactive — so every hidden book missed its title and fell back to a raw URI.
      combine(excludedBooksStore.data, contentRepo.flow(), ::hiddenBooksViewState)
    }.collectAsState(initial = HiddenBooksViewState(emptyList()))
    return state
  }

  fun restore(id: String) {
    scope.launch {
      excludedBooksStore.updateData { it - id }
      reactivate(id)
      // The book only reappears once a scan reconciles active books; without this an in-flight scan
      // (the library triggers one on every entry) can flip it straight back to inactive, leaving it
      // in neither the library nor this list.
      mediaScanWaiter.scanAndAwait()
    }
  }

  fun restoreAll() {
    scope.launch {
      val restored = excludedBooksStore.data.first()
      // Subtract what was actually listed rather than clearing: a concurrent backup restore merges
      // hidden ids into this same store, and emptySet() would silently drop them.
      excludedBooksStore.updateData { it - restored }
      restored.forEach { reactivate(it) }
      mediaScanWaiter.scanAndAwait()
    }
  }

  private suspend fun reactivate(id: String) {
    contentRepo.get(BookId(id))?.let { contentRepo.put(it.copy(isActive = true)) }
  }

  fun onClose() {
    navigator.goBack()
  }
}

internal fun hiddenBooksViewState(
  excludedIds: Set<String>,
  contents: List<BookContent>,
): HiddenBooksViewState {
  val namesById = contents.associate { it.id.value to it.name }
  return HiddenBooksViewState(
    books = excludedIds
      .map { id -> HiddenBookItem(id = id, name = namesById[id] ?: id.fallbackName()) }
      .sortedBy { it.name },
  )
}

/** Last resort for an excluded id with no content row left (files deleted): a readable file name. */
private fun String.fallbackName(): String = Uri.decode(this).substringAfterLast('/')

data class HiddenBooksViewState(val books: List<HiddenBookItem>)

data class HiddenBookItem(
  val id: String,
  val name: String,
)
