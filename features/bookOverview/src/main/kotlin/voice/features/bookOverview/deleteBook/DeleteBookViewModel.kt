package voice.features.bookOverview.deleteBook

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.documentfile.provider.DocumentFile
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.repo.BookContentRepo
import voice.core.data.store.ExcludedBooksStore
import voice.core.logging.api.Logger
import voice.core.scanner.MediaScanTrigger
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(BookOverviewScope::class)
@Inject
class DeleteBookViewModel(
  private val application: Application,
  private val mediaScanTrigger: MediaScanTrigger,
  private val contentRepo: BookContentRepo,
  @ExcludedBooksStore
  private val excludedBooksStore: DataStore<Set<String>>,
) : BottomSheetItemViewModel {

  private val scope = MainScope()

  private val _state = mutableStateOf<DeleteBookViewState?>(null)
  internal val state: State<DeleteBookViewState?> get() = _state

  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    return listOf(BottomSheetItem.DeleteBook)
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    if (item != BottomSheetItem.DeleteBook) return

    _state.value = DeleteBookViewState(
      id = bookId,
      alsoDeleteFiles = false,
      fileToDelete = bookId.toUri().pathSegments
        .let { segments ->
          val result = segments.lastOrNull()?.removePrefix("primary:")
          if (result.isNullOrEmpty()) {
            Logger.w("Could not determine path for $segments")
            segments.joinToString(separator = "\"")
          } else {
            result
          }
        },
    )
  }

  internal fun onDismiss() {
    _state.value = null
  }

  internal fun onToggleDeleteFiles(checked: Boolean) {
    _state.value = _state.value?.copy(alsoDeleteFiles = checked)
  }

  internal fun onConfirmRemove() {
    val state = _state.value ?: return
    _state.value = null
    scope.launch {
      // Add to exclusion list so the scanner won't re-activate this book
      excludedBooksStore.updateData { it + state.id.value }

      // Mark inactive immediately so it disappears from library now
      contentRepo.get(state.id)?.let { content ->
        contentRepo.put(content.copy(isActive = false))
      }

      if (state.alsoDeleteFiles) {
        val uri = state.id.toUri()
        DocumentFile.fromSingleUri(application, uri)?.delete()
      }

      mediaScanTrigger.scan(restartIfScanning = true)
    }
  }
}

data class DeleteBookViewState(
  val id: BookId,
  val alsoDeleteFiles: Boolean,
  val fileToDelete: String,
)
