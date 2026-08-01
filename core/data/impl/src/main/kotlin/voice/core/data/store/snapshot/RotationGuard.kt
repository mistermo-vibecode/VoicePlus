package voice.core.data.store.snapshot

internal object RotationGuard {

  // Decline a write when at least this fraction of previously-active books vanished unexplained.
  // Boundary cases are pinned by RotationGuardTest; tune here if real-world false positives appear.
  const val SUSPICIOUS_SHRINK_RATIO = 0.5

  // The veto is for FAST-MOVING destruction (a bug wiping the library writes within seconds).
  // A shrink that still holds this long after the retained snapshot is a deliberate library
  // change (folder removed, migration); vetoing forever would freeze backups at a stale state
  // and resurrect the removed books on restore.
  const val VETO_EXPIRY_MS: Long = 48L * 60 * 60 * 1000

  fun isSuspiciousShrink(
    best: LibrarySnapshot?,
    incoming: LibrarySnapshot,
    excludedIds: Set<String>,
  ): Boolean {
    if (best == null) return false
    val bestActive = best.activeIds()
    if (bestActive.isEmpty()) return false
    if (incoming.savedAtEpochMillis - best.savedAtEpochMillis >= VETO_EXPIRY_MS) return false
    val unexplained = bestActive - incoming.activeIds() - excludedIds
    return unexplained.size.toDouble() / bestActive.size >= SUSPICIOUS_SHRINK_RATIO
  }
}
