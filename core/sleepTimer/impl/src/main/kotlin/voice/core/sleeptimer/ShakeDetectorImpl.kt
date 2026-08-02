package voice.core.sleeptimer

import android.content.Context
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.squareup.seismic.ShakeDetector as SeismicShakeDetector

@ContributesBinding(AppScope::class)
class ShakeDetectorImpl(private val context: Context) : ShakeDetector {

  override suspend fun detect() {
    suspendCancellableCoroutine { cont ->
      val sensorManager = context.getSystemService<SensorManager>()
        ?: return@suspendCancellableCoroutine
      lateinit var shakeDetector: SeismicShakeDetector
      val listener = SeismicShakeDetector.Listener {
        if (cont.isActive) {
          // Stop before resuming: invokeOnCancellation only runs when the wait is cancelled (the
          // timeout path), so a detected shake would otherwise leave a ~50Hz accelerometer listener
          // registered for the life of the process — every night, for every shake-to-extend.
          shakeDetector.stop()
          cont.resume(Unit)
        }
      }
      shakeDetector = SeismicShakeDetector(listener)
      shakeDetector.start(sensorManager, SensorManager.SENSOR_DELAY_GAME)
      cont.invokeOnCancellation {
        shakeDetector.stop()
      }
    }
  }
}
