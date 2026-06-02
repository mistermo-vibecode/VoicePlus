package voice.core.data.store.snapshot

public interface LibrarySnapshotService {
  /** Begin observing the library and writing durable snapshots. Idempotent. */
  public fun start()

  /** Restore from a snapshot if the live DB is catastrophically empty/collapsed. No-op in Phase 1a-1. */
  public suspend fun restoreIfNeeded()
}
