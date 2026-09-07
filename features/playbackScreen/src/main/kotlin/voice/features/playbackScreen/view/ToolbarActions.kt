package voice.features.playbackScreen.view

import voice.core.data.PlaybackToolbarAction

/** How the now-playing app bar splits its actions between icons and the overflow menu. */
internal data class ToolbarActions(
  val pinned: List<PlaybackToolbarAction>,
  val overflow: List<PlaybackToolbarAction>,
)

/**
 * Display order is the enum order regardless of when an action was pinned. Chapter Fix only applies
 * to books that have something to fix, so when it is unavailable it is dropped from both lists
 * rather than shown dead. Anything pinned beyond [PlaybackToolbarAction.MAX_PINNED] (an older
 * store, or a concurrent write) falls back to the overflow.
 */
internal fun partitionToolbarActions(
  pinnedActions: Set<PlaybackToolbarAction>,
  chapterFixAvailable: Boolean,
): ToolbarActions {
  val available = PlaybackToolbarAction.entries.filter { action ->
    action != PlaybackToolbarAction.CHAPTER_FIX || chapterFixAvailable
  }
  val pinned = available.filter { it in pinnedActions }.take(PlaybackToolbarAction.MAX_PINNED)
  return ToolbarActions(
    pinned = pinned,
    overflow = available - pinned.toSet(),
  )
}
