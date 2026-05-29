package voice.features.chapterEditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.common.resolveChapterName
import voice.core.data.BookId
import voice.core.data.markForPosition
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.navigation.Navigator

@AssistedInject
public class ChapterEditorViewModel(
  private val bookRepository: BookRepository,
  private val overrideRepo: ChapterNameOverrideRepo,
  private val navigator: Navigator,
  dispatcherProvider: DispatcherProvider,
  @Assisted private val bookId: BookId,
) {

  private val scope = MainScope(dispatcherProvider)
  private val localOffset = MutableStateFlow<Int?>(null)

  private val _editingChapter = mutableStateOf<ChapterItemState?>(null)
  internal val editingChapter: State<ChapterItemState?> get() = _editingChapter

  private val _showResetConfirm = mutableStateOf(false)

  init {
    scope.launch {
      val book = bookRepository.flow(bookId).filterNotNull().first()
      if (localOffset.value == null) {
        localOffset.value = book.content.chapterNameOffset
      }
    }
  }

  @Composable
  public fun viewState(): ChapterEditorViewState? {
    val book = bookRepository.flow(bookId)
      .filterNotNull()
      .collectAsState(null).value ?: return null

    val overrideList by overrideRepo.overridesForBook(bookId)
      .collectAsState(emptyList())

    val offset = localOffset.collectAsState().value ?: return null

    val overrideMap = overrideList.associateBy { Pair(it.chapterId, it.markStartMs) }

    val currentMark = book.currentChapter.markForPosition(book.content.positionInChapter)

    var globalIndex = 0
    var currentChapterIndex = 0
    val items = book.chapters.flatMap { chapter ->
      chapter.chapterMarks.map { mark ->
        val key = Pair(chapter.id.value, mark.startMs)
        val override = overrideMap[key]?.name
        val isCurrent = mark == currentMark && chapter == book.currentChapter
        val item = ChapterItemState(
          chapterId = chapter.id,
          bookId = bookId,
          markStartMs = mark.startMs,
          displayNumber = globalIndex + 1,
          displayName = resolveChapterName(mark.name ?: "", offset, override),
          hasOverride = override != null,
          isCurrent = isCurrent,
        )
        if (isCurrent) currentChapterIndex = globalIndex
        globalIndex++
        item
      }
    }

    return ChapterEditorViewState(
      offset = offset,
      chapters = items,
      currentChapterIndex = currentChapterIndex,
      editingChapter = _editingChapter.value,
      showResetConfirm = _showResetConfirm.value,
    )
  }

  public fun onOffsetIncrement() {
    localOffset.value = (localOffset.value ?: 0) + 1
    persistOffset()
  }

  public fun onOffsetDecrement() {
    localOffset.value = (localOffset.value ?: 0) - 1
    persistOffset()
  }

  public fun onOffsetSet(value: Int) {
    localOffset.value = value
    persistOffset()
  }

  public fun onEditChapterClick(item: ChapterItemState) {
    _editingChapter.value = item
  }

  public fun onEditDismiss() {
    _editingChapter.value = null
  }

  public fun onEditConfirm(
    item: ChapterItemState,
    newName: String,
  ) {
    scope.launch {
      overrideRepo.set(item.chapterId, item.markStartMs, bookId, newName.trim())
      _editingChapter.value = null
    }
  }

  public fun onDeleteOverride(item: ChapterItemState) {
    scope.launch { overrideRepo.delete(item.chapterId, item.markStartMs) }
  }

  public fun onResetAllClick() {
    _showResetConfirm.value = true
  }

  public fun onResetAllConfirm() {
    _showResetConfirm.value = false
    localOffset.value = 0
    scope.launch {
      bookRepository.updateBook(bookId) { it.copy(chapterNameOffset = 0) }
      overrideRepo.deleteAll(bookId)
    }
  }

  public fun onResetAllDismiss() {
    _showResetConfirm.value = false
  }

  public fun onBack() {
    scope.launch {
      localOffset.value?.let { offset ->
        bookRepository.updateBook(bookId) { it.copy(chapterNameOffset = offset) }
      }
      navigator.goBack()
    }
  }

  private fun persistOffset() {
    scope.launch {
      localOffset.value?.let { offset ->
        bookRepository.updateBook(bookId) { it.copy(chapterNameOffset = offset) }
      }
    }
  }

  @AssistedFactory
  public interface Factory {
    public fun create(bookId: BookId): ChapterEditorViewModel
  }
}
