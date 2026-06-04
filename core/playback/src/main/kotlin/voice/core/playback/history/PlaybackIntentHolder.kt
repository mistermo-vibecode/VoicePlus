package voice.core.playback.history

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.data.ListeningEventType
import voice.core.playback.di.PlaybackScope

@Inject
@SingleIn(PlaybackScope::class)
class PlaybackIntentHolder {
  // Main-looper-confined transient state (no locks needed — all access is on the playback looper).
  // Cleared by ListeningEventRecorder on the next discontinuity. If a tagged seek produces no
  // discontinuity (e.g. forceSeekToNext at the last chapter), the tag is left stale and will be
  // consumed by the next seek — a known best-effort labeling limitation.
  var pendingSeekIntent: ListeningEventType? = null
  var suppressNextSeek: Boolean = false // used by the transport-events task
  var pendingPauseEndPositionMs: Long? = null // true end position captured before pause auto-rewind
  var stoppedBySleepTimer: Boolean = false // pause originated from the sleep timer
}
