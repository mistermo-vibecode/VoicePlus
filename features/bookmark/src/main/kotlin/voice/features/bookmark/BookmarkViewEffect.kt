package voice.features.bookmark

sealed interface BookmarkViewEffect {
  data object BookmarkDeleted : BookmarkViewEffect
  data object BookmarkUnavailable : BookmarkViewEffect
}
