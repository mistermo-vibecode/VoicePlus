package voice.core.data.store.snapshot

internal object RotationGuard {

  // Decline a write when at least this fraction of previously-active books vanished unexplained.
  // Boundary cases are pinned by RotationGuardTest; tune here if real-world false positives appear.
  const val SUSPICIOUS_SHRINK_RATIO = 0.5

  // Decline a write only when a previously-SUBSTANTIAL body of USER-AUTHORED rows (listening sessions +
  // bookmarks + character notes + chapter-name overrides) collapses to (near) nothing in one step WHILE
  // THE BOOKS THAT OWN THEM ARE STILL PRESENT — the in-place wipe seen on-device. We react only to that
  // catastrophic "almost everything vanished while the library survived" signature: an organic edit, or
  // even a deliberate clear of up to ~half a book's data, leaves enough behind to be allowed through, so
  // real deletions still propagate to the backup. Both thresholds are tunable if false positives appear.
  const val NEAR_TOTAL_USER_DATA_LOSS_FRACTION = 0.90
  const val MIN_USER_DATA_BASELINE = 8

  fun isSuspiciousShrink(
    retained: List<LibrarySnapshot>,
    incoming: LibrarySnapshot,
    excludedIds: Set<String>,
  ): Boolean {
    val best = retained.filter { it.activeCount > 0 }.maxByOrNull { it.sequence }
    val bookShrink = best != null && isSuspiciousBookShrink(best, incoming, excludedIds)
    return bookShrink || isSuspiciousUserDataCollapse(retained, incoming, excludedIds)
  }

  // Convenience for a single known baseline (tests, single-snapshot callers).
  fun isSuspiciousShrink(
    best: LibrarySnapshot?,
    incoming: LibrarySnapshot,
    excludedIds: Set<String>,
  ): Boolean = isSuspiciousShrink(listOfNotNull(best), incoming, excludedIds)

  private fun isSuspiciousBookShrink(
    best: LibrarySnapshot,
    incoming: LibrarySnapshot,
    excludedIds: Set<String>,
  ): Boolean {
    val bestActive = best.activeIds()
    if (bestActive.isEmpty()) return false
    val unexplained = bestActive - incoming.activeIds() - excludedIds
    return unexplained.size.toDouble() / bestActive.size >= SUSPICIOUS_SHRINK_RATIO
  }

  // Veto when user-authored rows crater but the books that own them are still present. We only count
  // rows whose book is NOT being legitimately removed (excluded, or genuinely gone from the incoming
  // library) so that deleting a book — and with it its sessions/bookmarks/characters/overrides — is an
  // EXPLAINED loss and never trips this guard. The remaining (still-present-book) rows collapsing to a
  // small fraction of what they were is the bug signature.
  private fun isSuspiciousUserDataCollapse(
    retained: List<LibrarySnapshot>,
    incoming: LibrarySnapshot,
    excludedIds: Set<String>,
  ): Boolean {
    val survivingBookIds = incoming.allBookIds() - excludedIds
    // Compare against the HIGH-WATER-MARK of user-authored rows ever retained for the surviving books — not
    // just the latest generation — so a slow multi-step drip (each step individually under the threshold,
    // which would otherwise re-base the comparison downward) is still vetoed against the peak, as long as a
    // data-rich generation remains in the ring.
    val peakRows = retained.maxOfOrNull { it.userDataRowsForBooks(survivingBookIds) } ?: 0
    if (peakRows < MIN_USER_DATA_BASELINE) return false
    val incomingRows = incoming.userDataRowsForBooks(survivingBookIds)
    val lost = peakRows - incomingRows
    if (lost <= 0) return false
    return lost.toDouble() / peakRows >= NEAR_TOTAL_USER_DATA_LOSS_FRACTION
  }
}
