package voice.core.data.repo

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.InterruptedSessionFinalizer
import voice.core.data.ListeningSession
import voice.core.data.ListeningSessionEndReason
import voice.core.data.OpenSessionCheckpoint
import voice.core.data.repo.internals.dao.ListeningSessionDao
import voice.core.data.store.OpenListeningSessionStore
import voice.core.logging.api.Logger
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class InterruptedSessionFinalizerImpl internal constructor(
  @OpenListeningSessionStore private val store: DataStore<OpenSessionCheckpoint?>,
  private val sessionDao: ListeningSessionDao,
) : InterruptedSessionFinalizer {

  override suspend fun finalizeIfNeeded() {
    try {
      val checkpoint = store.data.first() ?: return
      // Clear FIRST, one-shot: whatever happens below, a bad checkpoint can never loop.
      store.updateData { null }
      val spanMs = checkpoint.lastSeenAtEpochMillis - checkpoint.startedAtEpochMillis
      // Same gate as the recorder's MIN_SESSION_MS: sub-3s orphans are noise, not listening.
      if (spanMs < 3_000L) return
      val startedAt = Instant.ofEpochMilli(checkpoint.startedAtEpochMillis)
      // The session may have closed normally right before the process died (clear write lost).
      if (sessionDao.countAt(BookId(checkpoint.bookId), startedAt) > 0) return
      sessionDao.insert(
        ListeningSession(
          bookId = BookId(checkpoint.bookId),
          chapterId = ChapterId(checkpoint.chapterId),
          startedAt = startedAt,
          endedAt = Instant.ofEpochMilli(checkpoint.lastSeenAtEpochMillis),
          durationMs = spanMs,
          startPositionMs = checkpoint.startPositionMs,
          endPositionMs = checkpoint.lastSeenPositionMs,
          endChapterId = ChapterId(checkpoint.lastSeenChapterId),
          endReason = ListeningSessionEndReason.Interrupted.id,
        ),
      )
      Logger.i("Finalized an interrupted listening session of ${spanMs / 1000}s")
    } catch (e: CancellationException) {
      throw e
    } catch (t: Throwable) {
      Logger.w(t, "Could not finalize the interrupted listening session")
    }
  }
}
