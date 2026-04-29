package voice.core.sleeptimer

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import voice.core.initializer.AppInitializer

@ContributesIntoSet(AppScope::class)
class SleepTimerVolumeObserver(private val sleepTimer: SleepTimer) : AppInitializer {

  override fun onAppStart(application: Application) {
    val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
    application.registerReceiver(
      object : BroadcastReceiver() {
        override fun onReceive(
          context: Context,
          intent: Intent,
        ) {
          val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
          if (streamType == AudioManager.STREAM_MUSIC) {
            val currentState = sleepTimer.state.value
            if (currentState is SleepTimerState.Enabled.WithDuration) {
              sleepTimer.reset()
            }
          }
        }
      },
      filter,
    )
  }
}
