package voice.core.data.store.snapshot

import voice.core.data.repo.internals.AppDb

internal object RestoreSelector {

  /**
   * Decides whether to auto-restore on launch and from which generation. Pure.
   *
   * Restores the newest candidate that is non-empty AND not from a newer schema, IFF the live DB is
   * genuinely empty ([liveTotal] == 0) — a real clear-data / reinstall. A library that merely has all its
   * books INACTIVE (e.g. the user removed their folders) is deliberately NOT auto-restored: resurrecting it
   * would fight an intentional removal. Recovery from a destructive bug that leaves inactive rows behind is
   * the user's explicit Restore action, not a silent heuristic. Returns null when nothing should restore.
   */
  fun select(
    liveTotal: Int,
    candidates: List<LibrarySnapshot>,
  ): LibrarySnapshot? {
    if (liveTotal > 0) return null
    return candidates
      .filter {
        it.activeCount > 0 &&
          it.dbVersion <= AppDb.VERSION &&
          it.schemaVersion <= LibrarySnapshot.SCHEMA_VERSION
      }
      .maxByOrNull { it.sequence }
  }
}
