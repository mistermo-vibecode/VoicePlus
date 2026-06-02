package voice.core.data.store.snapshot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Marks that an OS-wipe restore is in flight. The [SnapshotWriter] consults [active] and suppresses its
 * debounced background save while a restore runs, so it can't overwrite the on-device ring / external bundle
 * with the freshly-scanned-but-not-yet-re-keyed library (which has active books but no user data). The save
 * resumes after the restore commits, capturing the final re-keyed state.
 */
@SingleIn(AppScope::class)
@Inject
internal class RestoreGate {

  private val _active = MutableStateFlow(false)
  val active: StateFlow<Boolean> = _active

  suspend fun <T> withRestoreActive(block: suspend () -> T): T {
    _active.value = true
    try {
      return block()
    } finally {
      _active.value = false
    }
  }
}
