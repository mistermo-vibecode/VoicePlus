package voice.core.data.store.snapshot

internal object RotationGuard {

  // Decline a write when at least this fraction of previously-active books vanished unexplained.
  // Boundary cases are pinned by RotationGuardTest; tune here if real-world false positives appear.
  const val SUSPICIOUS_SHRINK_RATIO = 0.5

  fun isSuspiciousShrink(
    best: LibrarySnapshot?,
    incoming: LibrarySnapshot,
    excludedIds: Set<String>,
  ): Boolean {
    if (best == null) return false
    val bestActive = best.activeIds()
    if (bestActive.isEmpty()) return false
    val unexplained = bestActive - incoming.activeIds() - excludedIds
    return unexplained.size.toDouble() / bestActive.size >= SUSPICIOUS_SHRINK_RATIO
  }
}
