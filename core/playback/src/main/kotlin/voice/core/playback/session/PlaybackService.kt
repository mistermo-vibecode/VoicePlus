package voice.core.playback.session

import android.content.Intent
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import voice.core.common.rootGraphAs
import voice.core.logging.api.Logger
import voice.core.playback.di.PlaybackGraph
import voice.core.playback.history.ListeningEventRecorder
import voice.core.playback.player.VoicePlayer
import voice.core.playback.playstate.PositionUpdater
import kotlin.time.Duration.Companion.seconds

class PlaybackService : MediaLibraryService() {

  @Inject
  lateinit var session: MediaLibrarySession

  @Inject
  lateinit var scope: CoroutineScope

  @Inject
  lateinit var player: VoicePlayer

  @Inject
  lateinit var positionUpdater: PositionUpdater

  @Inject
  lateinit var listeningEventRecorder: ListeningEventRecorder

  @Inject
  lateinit var voiceNotificationProvider: VoiceMediaNotificationProvider

  private var released = false

  override fun onCreate() {
    super.onCreate()
    rootGraphAs<PlaybackGraph.Provider>()
      .playbackGraphFactory
      .create(this)
      .inject(this)
    setMediaNotificationProvider(voiceNotificationProvider)
  }

  private fun release() {
    if (released) return
    released = true
    try {
      // Stop the 1s position checkpoint loop FIRST: it re-enters the book-repo mutex from
      // Main.immediate, and flushing while it still runs can deadlock the blocked main thread
      // (ANR -> system kill -> both writes lost).
      positionUpdater.release()
      runBlocking {
        // Session before position: the open session is irreplaceable at this layer, while the
        // position checkpoints every second and can lose at most that. Each flush is fenced so
        // one failure can't starve the other or skip the releases below.
        runCatching { withTimeoutOrNull(FLUSH_TIMEOUT) { listeningEventRecorder.flushOpenSessionNow() } }
          .onFailure { Logger.w(it, "Could not flush the open listening session on teardown") }
        runCatching { withTimeoutOrNull(FLUSH_TIMEOUT) { positionUpdater.flushPositionNow() } }
          .onFailure { Logger.w(it, "Could not flush the playback position on teardown") }
      }
    } finally {
      listeningEventRecorder.release()
      player.release()
      session.release()
      scope.cancel()
    }
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    // Swipe-away stops playback (existing behavior), but the service must actually STOP:
    // without stopSelf() this released instance lingers with `released` latched, onGetSession
    // returns null forever, and no listening gets recorded until the process dies. We still skip
    // super.onTaskRemoved() — its stopSelf() path would run before our flushes.
    release()
    stopSelf()
  }

  override fun onDestroy() {
    super.onDestroy()
    release()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
    return session.takeUnless { session ->
      session.invokeIsReleased
    }.also {
      if (it == null) {
        Logger.w("onGetSession returns null because the session is already released")
      }
    }
  }
}

private val FLUSH_TIMEOUT = 2.seconds

private val MediaSession.invokeIsReleased: Boolean
  get() = try {
    // temporarily checked to debug
    // https://github.com/androidx/media/issues/422
    MediaSession::class.java.getDeclaredMethod("isReleased")
      .apply { isAccessible = true }
      .invoke(this) as Boolean
  } catch (e: Exception) {
    Logger.w(e, "Couldn't check if it's released")
    false
  }
