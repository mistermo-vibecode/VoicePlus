package voice.features.bookmark

import voice.core.data.Bookmark

sealed interface BookmarkViewEffect {
  // Carries the deleted bookmark so a stacked snackbar's Undo restores THIS one, not whichever
  // deletion happened to be last.
  data class BookmarkDeleted(val bookmark: Bookmark) : BookmarkViewEffect
  data object BookmarkUnavailable : BookmarkViewEffect
}
