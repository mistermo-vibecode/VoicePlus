package voice.core.playback

import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.playback.session.DISPLAY_TO_SOURCE_POSITION_OFFSET_MS
import voice.core.playback.session.MediaId
import voice.core.playback.session.toMediaIdOrNull

data class LivePlaybackState(
  val bookId: BookId,
  val chapterId: ChapterId,
  val positionMs: Long,
  val isPlaying: Boolean,
  val playbackSpeed: Float,
)

internal fun MediaController.livePlaybackStateSnapshot(bookId: BookId? = null): LivePlaybackState? {
  return toLivePlaybackState(
    mediaId = currentMediaItem?.mediaId?.toMediaIdOrNull(),
    bookId = bookId,
    displayedPositionMs = currentPosition,
    mediaMetadata = mediaMetadata,
    isPlaying = isPlaying,
    playbackSpeed = playbackParameters.speed,
  )
}

internal fun toLivePlaybackState(
  mediaId: MediaId?,
  bookId: BookId?,
  displayedPositionMs: Long,
  mediaMetadata: MediaMetadata?,
  isPlaying: Boolean,
  playbackSpeed: Float,
): LivePlaybackState? {
  val chapterMediaId = mediaId as? MediaId.Chapter ?: return null
  if (bookId != null && chapterMediaId.bookId != bookId) return null
  if (displayedPositionMs == C.TIME_UNSET || displayedPositionMs < 0L) return null

  val positionOffsetMs = mediaMetadata?.extras
    ?.takeIf { it.containsKey(DISPLAY_TO_SOURCE_POSITION_OFFSET_MS) }
    ?.getLong(DISPLAY_TO_SOURCE_POSITION_OFFSET_MS)
    ?: 0L
  return LivePlaybackState(
    bookId = chapterMediaId.bookId,
    chapterId = chapterMediaId.chapterId,
    positionMs = displayedPositionMs + positionOffsetMs,
    isPlaying = isPlaying,
    playbackSpeed = playbackSpeed,
  )
}

fun Book.overlay(livePlaybackState: LivePlaybackState): Book {
  return if (livePlaybackState.bookId == id) {
    update {
      it.copy(
        currentChapter = livePlaybackState.chapterId,
        positionInChapter = livePlaybackState.positionMs,
        playbackSpeed = livePlaybackState.playbackSpeed,
      )
    }
  } else {
    this
  }
}
