package voice.core.data.store.snapshot

import voice.core.data.repo.internals.AppDb

internal object RestoreSelector {

  /**
   * Decides whether to restore and from which generation. Pure.
   *
   * Restores the newest candidate that is non-empty AND not from a newer schema, IFF the live DB is
   * catastrophically empty OR its active set collapsed to nothing in a way NOT explained by the user's
   * own deletes (excludedIds). Returns null otherwise — including a legitimately empty library.
   */
  fun select(
    liveTotal: Int,
    liveActiveIds: Set<String>,
    excludedIds: Set<String>,
    candidates: List<LibrarySnapshot>,
  ): LibrarySnapshot? {
    val best = candidates
      .filter {
        it.activeCount > 0 &&
          it.dbVersion <= AppDb.VERSION &&
          it.schemaVersion <= LibrarySnapshot.SCHEMA_VERSION
      }
      .maxByOrNull { it.sequence }
      ?: return null

    val catastrophicEmpty = liveTotal == 0
    val unexplainedCollapse = liveActiveIds.isEmpty() && (best.activeIds() - excludedIds).isNotEmpty()
    return if (catastrophicEmpty || unexplainedCollapse) best else null
  }
}
