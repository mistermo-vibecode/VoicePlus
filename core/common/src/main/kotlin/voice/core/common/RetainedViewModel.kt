package voice.core.common

import androidx.compose.runtime.retain.RetainObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/** Owns a coroutine scope for exactly as long as this model is retained by Compose. */
abstract class RetainedViewModel(protected val scope: CoroutineScope = MainScope()) : RetainObserver {

  final override fun onRetained() = Unit

  final override fun onEnteredComposition() = Unit

  final override fun onExitedComposition() = Unit

  final override fun onRetired() {
    scope.cancel()
  }

  final override fun onUnused() {
    scope.cancel()
  }
}
