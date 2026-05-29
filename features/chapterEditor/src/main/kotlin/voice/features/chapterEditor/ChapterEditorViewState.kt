package voice.features.chapterEditor

import voice.core.data.BookId
import voice.core.data.ChapterId

public data class ChapterEditorViewState(
  val offset: Int,
  val chapters: List<ChapterItemState>,
  val currentChapterIndex: Int,
  val editingChapter: ChapterItemState? = null,
  val showResetConfirm: Boolean = false,
)

public data class ChapterItemState(
  val chapterId: ChapterId,
  val bookId: BookId,
  val markStartMs: Long,
  val displayNumber: Int,
  val displayName: String,
  val hasOverride: Boolean,
  val isCurrent: Boolean,
)
