package voice.core.data.store.snapshot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Marks that an OS-wipe restore is in flight. The [SnapshotWriter] consults [active] and suppresses its
 * debounced background save while a restore runs, so it can't overwrite the on-device ring / external bundle
 * with the freshly-scanned-but-not-yet-re-keyed library (which has active books but no user data). The save
 * resumes after the restore commits, capturing the final re-keyed state.
 *
 * [flushRequests] lets a fully-successful restore ask the writer to flush the re-keyed state to the ring +
 * external bundle immediately (rather than waiting on the debounce timer, which steady playback could starve).
 */
@SingleIn(AppScope::class)
@Inject
internal class RestoreGate {

  private val _active = MutableStateFlow(false)
  val active: StateFlow<Boolean> = _active

  private val _flushRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val flushRequests: SharedFlow<Unit> = _flushRequests.asSharedFlow()

  suspend fun <T> withRestoreActive(block: suspend () -> T): T {
    _active.value = true
    try {
      return block()
    } finally {
      _active.value = false
    }
  }

  /** Request an immediate snapshot flush. Call AFTER [withRestoreActive] returns, so the gate is already clear. */
  fun requestFlush() {
    _flushRequests.tryEmit(Unit)
  }
}
