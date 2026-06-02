package voice.core.data.store.snapshot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class LibrarySnapshotServiceImpl internal constructor(
  private val writer: SnapshotWriter,
  private val scope: CoroutineScope,
) : LibrarySnapshotService {

  override fun start() {
    writer.start(scope)
  }

  // Filled in Phase 1a-2 (read-time restore). Intentionally a no-op now so wiring it into
  // App.onCreate() in 1a-2 is safe and tests are unaffected.
  override suspend fun restoreIfNeeded() {
    // no-op until Phase 1a-2
  }
}
