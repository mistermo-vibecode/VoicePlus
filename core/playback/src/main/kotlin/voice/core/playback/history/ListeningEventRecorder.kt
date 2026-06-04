package voice.core.playback.history

import androidx.media3.common.Player
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ListeningSession
import voice.core.data.ListeningSessionEndReason
import voice.core.data.repo.ListeningEventRepo
import voice.core.data.repo.ListeningSessionRepo
import voice.core.playback.di.PlaybackScope
import voice.core.playback.session.MediaId
import voice.core.playback.session.toMediaIdOrNull
import java.time.Instant

@Inject
@SingleIn(PlaybackScope::class)
class ListeningEventRecorder(
  private val sessionRepo: ListeningSessionRepo,
  private val eventRepo: ListeningEventRepo, // unused this task; wired now for the next task
  private val holder: PlaybackIntentHolder,
  private val scope: CoroutineScope,
) : Player.Listener {

  internal var clock: () -> Instant = { Instant.now() }

  private var player: Player? = null
  private var openSession: OpenSession? = null

  fun attachTo(player: Player) {
    this.player?.removeListener(this)
    this.player = player
    player.addListener(this)
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    if (isPlaying) {
      open()
    } else {
      close(ListeningSessionEndReason.Paused)
    }
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState == Player.STATE_ENDED) {
      close(ListeningSessionEndReason.EndOfBook)
    }
  }

  private fun open() {
    if (openSession != null) return
    val location = currentLocation() ?: return
    openSession = OpenSession(
      startedAt = clock(),
      bookId = location.bookId,
      chapterId = location.chapterId,
      startPositionMs = location.positionMs,
    )
  }

  private fun close(defaultReason: ListeningSessionEndReason) {
    val open = openSession ?: return
    openSession = null

    val endedAt = clock()
    val durationMs = endedAt.toEpochMilli() - open.startedAt.toEpochMilli()
    if (durationMs < MIN_SESSION_MS) {
      holder.pendingPauseEndPositionMs = null
      holder.stoppedBySleepTimer = false
      return
    }

    val location = currentLocation()
    val endReason = if (holder.stoppedBySleepTimer) ListeningSessionEndReason.Sleep else defaultReason
    val session = ListeningSession(
      bookId = open.bookId,
      chapterId = open.chapterId,
      startedAt = open.startedAt,
      endedAt = endedAt,
      durationMs = durationMs,
      startPositionMs = open.startPositionMs,
      endPositionMs = holder.pendingPauseEndPositionMs ?: (location?.positionMs ?: open.startPositionMs),
      endChapterId = location?.chapterId,
      endReason = endReason.id,
    )

    holder.pendingPauseEndPositionMs = null
    holder.stoppedBySleepTimer = false

    scope.launch { sessionRepo.addSession(session) }
  }

  private fun currentLocation(): Location? {
    val player = player ?: return null
    val mediaItem = player.currentMediaItem ?: return null
    val mediaId = mediaItem.mediaId.toMediaIdOrNull() as? MediaId.Chapter ?: return null
    val positionMs = player.currentPosition.takeIf { it >= 0 } ?: 0L
    return Location(mediaId.bookId, mediaId.chapterId, positionMs)
  }

  fun release() {
    player?.removeListener(this)
    player = null
  }

  private data class OpenSession(
    val startedAt: Instant,
    val bookId: BookId,
    val chapterId: ChapterId,
    val startPositionMs: Long,
  )

  private data class Location(
    val bookId: BookId,
    val chapterId: ChapterId,
    val positionMs: Long,
  )
}

private const val MIN_SESSION_MS = 3_000L
