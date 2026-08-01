package voice.core.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ListeningSession
import voice.core.data.ListeningSessionEndReason
import voice.core.data.OpenSessionCheckpoint
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.MemoryDataStore
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class InterruptedSessionFinalizerImplTest {

  private lateinit var db: AppDb
  private val store = MemoryDataStore<OpenSessionCheckpoint?>(null)

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build()
  }

  @After
  fun teardown() = db.close()

  private fun finalizer() = InterruptedSessionFinalizerImpl(store, db.listeningSessionDao())

  private fun checkpoint(
    startedAt: Long = 1_000_000,
    lastSeenAt: Long = 1_180_000,
  ) = OpenSessionCheckpoint(
    startedAtEpochMillis = startedAt,
    bookId = "book",
    chapterId = "chapter",
    startPositionMs = 0,
    lastSeenAtEpochMillis = lastSeenAt,
    lastSeenPositionMs = 180_000,
    lastSeenChapterId = "chapter",
  )

  @Test
  fun `orphaned checkpoint becomes an Interrupted session and the store is cleared`() = runTest {
    store.updateData { checkpoint() }

    finalizer().finalizeIfNeeded()

    val session = db.listeningSessionDao().all().single()
    session.endReason shouldBe ListeningSessionEndReason.Interrupted.id
    session.startedAt shouldBe Instant.ofEpochMilli(1_000_000)
    session.endedAt shouldBe Instant.ofEpochMilli(1_180_000)
    session.durationMs shouldBe 180_000L
    session.endPositionMs shouldBe 180_000L
    store.data.first() shouldBe null
  }

  @Test
  fun `sub-3s orphan is discarded but still cleared`() = runTest {
    store.updateData { checkpoint(startedAt = 1_000_000, lastSeenAt = 1_002_000) }

    finalizer().finalizeIfNeeded()

    db.listeningSessionDao().all() shouldHaveSize 0
    store.data.first() shouldBe null
  }

  @Test
  fun `already-recorded session is not duplicated`() = runTest {
    db.listeningSessionDao().insert(
      ListeningSession(
        bookId = BookId("book"),
        chapterId = ChapterId("chapter"),
        startedAt = Instant.ofEpochMilli(1_000_000),
        endedAt = Instant.ofEpochMilli(1_200_000),
        durationMs = 200_000,
        startPositionMs = 0,
        endPositionMs = 200_000,
        endReason = ListeningSessionEndReason.Paused.id,
      ),
    )
    store.updateData { checkpoint() }

    finalizer().finalizeIfNeeded()

    db.listeningSessionDao().all() shouldHaveSize 1
    db.listeningSessionDao().all().single().endReason shouldBe ListeningSessionEndReason.Paused.id
  }

  @Test
  fun `second run is a no-op`() = runTest {
    store.updateData { checkpoint() }
    val f = finalizer()
    f.finalizeIfNeeded()
    f.finalizeIfNeeded()

    db.listeningSessionDao().all() shouldHaveSize 1
  }
}
