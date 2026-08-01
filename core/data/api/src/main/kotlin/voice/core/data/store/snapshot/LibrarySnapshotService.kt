package voice.core.data.store.snapshot

public interface LibrarySnapshotService {
  /** Begin observing the library and writing durable snapshots. Idempotent. */
  public fun start()

  /** Restore from a snapshot if the live DB is catastrophically empty/collapsed. */
  public suspend fun restoreIfNeeded()

  /**
   * Write any pending library changes into the snapshot ring NOW, bypassing the debounce.
   * Call before a manual export so "Back up now" captures the current state, not a
   * seconds-stale generation.
   */
  public suspend fun flushNow()
}
