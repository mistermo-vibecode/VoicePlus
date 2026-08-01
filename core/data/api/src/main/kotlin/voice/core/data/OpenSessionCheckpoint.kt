package voice.core.data

import kotlinx.serialization.Serializable

/**
 * Periodic checkpoint of the in-flight listening session, persisted every ~30s while playing and
 * cleared on a normal close. If the process dies mid-listen (crash, force-stop, reboot), the next
 * app start finds the orphaned checkpoint and finalizes it as a session with
 * [ListeningSessionEndReason.Interrupted] — so a 3-hour listen killed by the OS records ~3 hours
 * instead of nothing.
 */
@Serializable
public data class OpenSessionCheckpoint(
  val startedAtEpochMillis: Long,
  val bookId: String,
  val chapterId: String,
  val startPositionMs: Long,
  val lastSeenAtEpochMillis: Long,
  val lastSeenPositionMs: Long,
  val lastSeenChapterId: String,
)

/** Finalizes an orphaned [OpenSessionCheckpoint] into an Interrupted session. Run once at app start. */
public interface InterruptedSessionFinalizer {
  public suspend fun finalizeIfNeeded()
}
