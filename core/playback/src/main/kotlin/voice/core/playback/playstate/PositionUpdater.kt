package voice.core.playback.playstate

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import voice.core.data.repo.BookRepository
import voice.core.featureflag.ExperimentalPlaybackPersistenceQualifier
import voice.core.featureflag.FeatureFlag
import voice.core.logging.api.Logger
import voice.core.playback.di.PlaybackScope
import voice.core.playback.session.MediaId
import voice.core.playback.session.toMediaIdOrNull
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(PlaybackScope::class)
class PositionUpdater(
  private val bookRepo: BookRepository,
  private val scope: CoroutineScope,
  private val playStateManager: PlayStateManager,
  @ExperimentalPlaybackPersistenceQualifier
  private val experimentalPlaybackPersistenceFeatureFlag: FeatureFlag<Boolean>,
) : Player.Listener {

  private var player: Player? = null
  private var updateJob: Job? = null

  fun attachTo(player: Player) {
    this.player?.removeListener(this)
    this.player = player
    player.addListener(this)

    updateJob = scope.launch {
      combine(
        playStateManager.flow,
        experimentalPlaybackPersistenceFeatureFlag.flow,
      ) { playState, featureFlag ->
        (playState == PlayStateManager.PlayState.Playing) to featureFlag.value
      }
        .distinctUntilChanged()
        .collectLatest { (playing, experimentalPersistence) ->
          if (playing) {
            while (true) {
              delay(
                if (experimentalPersistence) {
                  5.minutes
                } else {
                  1.seconds
                },
              )
              flushPositionNow()
            }
          }
        }
    }
  }

  override fun onPositionDiscontinuity(
    oldPosition: Player.PositionInfo,
    newPosition: Player.PositionInfo,
    reason: Int,
  ) {
    val oldSnapshot = oldPosition.positionSnapshot()
    val newSnapshot = newPosition.positionSnapshot() ?: player?.positionSnapshot()
    val snapshots = if (oldSnapshot?.mediaId?.bookId != newSnapshot?.mediaId?.bookId) {
      listOfNotNull(oldSnapshot, newSnapshot)
    } else {
      listOfNotNull(newSnapshot ?: oldSnapshot)
    }
    flushPositions(snapshots)
  }

  override fun onPlayWhenReadyChanged(
    playWhenReady: Boolean,
    reason: Int,
  ) {
    flushPosition()
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
      flushPosition()
    }
  }

  override fun onMediaItemTransition(
    mediaItem: MediaItem?,
    reason: Int,
  ) {
    flushPosition()
  }

  private fun flushPosition() {
    val snapshot = player?.positionSnapshot() ?: return
    flushPositions(listOf(snapshot))
  }

  private fun flushPositions(snapshots: List<PositionSnapshot>) {
    if (snapshots.isEmpty()) return
    scope.launch {
      snapshots.forEach { persistPosition(it) }
    }
  }

  suspend fun flushPositionNow() {
    val snapshot = player?.positionSnapshot() ?: return
    persistPosition(snapshot)
  }

  private suspend fun persistPosition(snapshot: PositionSnapshot) {
    bookRepo.updateBook(snapshot.mediaId.bookId) { content ->
      if (snapshot.mediaId.chapterId in content.chapters) {
        Logger.d("${snapshot.positionMs} is the new position!")
        content.copy(
          currentChapter = snapshot.mediaId.chapterId,
          positionInChapter = snapshot.positionMs,
          lastPlayedAt = Instant.now(),
        )
      } else {
        Logger.w("${snapshot.mediaId} not in $content")
        content
      }
    }
  }

  fun release() {
    player?.removeListener(this)
    updateJob?.cancel()
  }
}

private data class PositionSnapshot(
  val mediaId: MediaId.Chapter,
  val positionMs: Long,
)

private fun Player.positionSnapshot(): PositionSnapshot? {
  val mediaItem = currentMediaItem ?: return null
  val position = currentPosition.takeIf { it >= 0 } ?: return null
  val mediaId = mediaItem.mediaId.toMediaIdOrNull() as? MediaId.Chapter ?: return null
  return PositionSnapshot(mediaId, position)
}

private fun Player.PositionInfo.positionSnapshot(): PositionSnapshot? {
  val mediaItem = mediaItem ?: return null
  val position = positionMs.takeIf { it >= 0 } ?: return null
  val mediaId = mediaItem.mediaId.toMediaIdOrNull() as? MediaId.Chapter ?: return null
  return PositionSnapshot(mediaId, position)
}
