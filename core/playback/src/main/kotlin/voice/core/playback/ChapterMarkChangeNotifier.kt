package voice.core.playback

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@SingleIn(AppScope::class)
@Inject
class ChapterMarkChangeNotifier {
  private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  val flow: Flow<Unit> get() = changes

  fun notifyChanged() {
    changes.tryEmit(Unit)
  }
}
