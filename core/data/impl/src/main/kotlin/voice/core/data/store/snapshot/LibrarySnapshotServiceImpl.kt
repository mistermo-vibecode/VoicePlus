package voice.core.data.store.snapshot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class LibrarySnapshotServiceImpl internal constructor(
  private val writer: SnapshotWriter,
  private val restorer: BackupRestorer,
  private val scope: CoroutineScope,
) : LibrarySnapshotService {

  override fun start() {
    writer.start(scope)
  }

  override suspend fun restoreIfNeeded() {
    restorer.restoreIfNeeded()
  }

  override suspend fun flushNow() {
    writer.flushNow()
  }
}
