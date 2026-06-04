package voice.core.playback.session

import android.content.Intent
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import voice.core.common.rootGraphAs
import voice.core.logging.api.Logger
import voice.core.playback.di.PlaybackGraph
import voice.core.playback.history.ListeningEventRecorder
import voice.core.playback.player.VoicePlayer
import voice.core.playback.playstate.PositionUpdater

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
    // Flush before releasing: both suspending writes need the scope alive.
    runBlocking {
      positionUpdater.flushPositionNow()
      listeningEventRecorder.flushOpenSessionNow()
    }
    positionUpdater.release()
    listeningEventRecorder.release()
    player.release()
    session.release()
    scope.cancel()
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    // Don't call super: MediaSessionService.onTaskRemoved calls stopSelf(), which would stop
    // the service even if another controller is still connected. release() does the full teardown.
    release()
  }

  override fun onDestroy() {
    super.onDestroy()
    release()
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    Logger.d("onTaskRemoved: persisting in-flight position and session")
    runBlocking {
      positionUpdater.flushPositionNow()
    }
    super.onTaskRemoved(rootIntent)
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
