package voice.core.playback.history

import androidx.media3.common.Player
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ListeningEvent
import voice.core.data.ListeningEventType
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
  private val eventRepo: ListeningEventRepo,
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
      val player = player
      if (player != null && player.playbackState == Player.STATE_BUFFERING && player.playWhenReady) {
        // A rebuffer mid-listen, not a pause. Keep the session open — otherwise a stuttering
        // stream fragments one listen into sub-3s pieces the duration gate then discards.
        return
      }
      close(ListeningSessionEndReason.Paused)
    }
  }

  /**
   * Called by VoicePlayer.setBook BEFORE the timeline swap: close an open session that belongs to
   * a different book, so its time is billed to the book that was actually playing. The new book's
   * session opens on the next isPlaying edge.
   */
  fun onBookSwitch(newBookId: BookId) {
    val open = openSession ?: return
    if (open.bookId == newBookId) return
    close(ListeningSessionEndReason.BookSwitch)
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState == Player.STATE_ENDED) {
      close(ListeningSessionEndReason.EndOfBook)
    }
  }

  override fun onPositionDiscontinuity(
    oldPosition: Player.PositionInfo,
    newPosition: Player.PositionInfo,
    reason: Int,
  ) {
    if (holder.suppressNextSeek) {
      // The pause auto-rewind seek, not a user action — swallow it.
      holder.suppressNextSeek = false
      return
    }
    val loc = currentLocation() ?: return

    val intent = holder.pendingSeekIntent
    val type = when {
      reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> ListeningEventType.AutoAdvance
      intent != null -> intent.also { holder.pendingSeekIntent = null }
      reason == Player.DISCONTINUITY_REASON_SEEK -> ListeningEventType.SetPosition
      else -> return
    }

    scope.launch {
      eventRepo.addEvent(
        ListeningEvent(
          bookId = loc.bookId,
          type = type.id,
          chapterId = loc.chapterId,
          positionMs = newPosition.positionMs,
          fromPositionMs = oldPosition.positionMs,
          at = clock(),
        ),
      )
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

  private fun buildClosedSession(defaultReason: ListeningSessionEndReason): ListeningSession? {
    val open = openSession ?: run {
      // No session to close, but a pause that never opened one (sleep timer firing while already
      // paused, redundant pause during buffering) must not leave its flags behind — a stale
      // stoppedBySleepTimer/pendingPauseEndPositionMs would poison the NEXT real session with a
      // bogus Sleep badge and an end position from hours earlier.
      clearPauseFlags()
      return null
    }
    openSession = null

    val endedAt = clock()
    val durationMs = endedAt.toEpochMilli() - open.startedAt.toEpochMilli()
    if (durationMs < MIN_SESSION_MS) {
      clearPauseFlags()
      return null
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

    clearPauseFlags()

    return session
  }

  private fun close(defaultReason: ListeningSessionEndReason) {
    val session = buildClosedSession(defaultReason) ?: return
    scope.launch { sessionRepo.addSession(session) }
  }

  /**
   * Synchronously closes and persists the current open session, if any. Uses
   * [ListeningSessionEndReason.Paused] as the fallback end reason; [ListeningSessionEndReason.Sleep]
   * takes precedence if the sleep-timer flag is set. Idempotent after the first call.
   */
  suspend fun flushOpenSessionNow() {
    val session = buildClosedSession(ListeningSessionEndReason.Paused) ?: return
    sessionRepo.addSession(session)
  }

  private fun clearPauseFlags() {
    holder.pendingPauseEndPositionMs = null
    holder.stoppedBySleepTimer = false
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
