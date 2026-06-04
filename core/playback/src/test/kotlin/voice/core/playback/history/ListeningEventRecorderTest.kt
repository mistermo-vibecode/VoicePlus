package voice.core.playback.history

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ListeningEvent
import voice.core.data.ListeningEventType
import voice.core.data.ListeningSession
import voice.core.data.ListeningSessionEndReason
import voice.core.data.repo.ListeningEventRepo
import voice.core.data.repo.ListeningSessionRepo
import voice.core.playback.session.MediaId
import java.time.Instant

class ListeningEventRecorderTest {

  private val bookId = BookId("content://books/1")
  private val chapterId = ChapterId("content://chapters/1")

  private val saved = mutableListOf<ListeningSession>()
  private val sessionRepo: ListeningSessionRepo = mockk {
    coEvery { addSession(any()) } answers { saved += firstArg<ListeningSession>() }
  }
  private val events = mutableListOf<ListeningEvent>()
  private val eventRepo: ListeningEventRepo = mockk {
    coEvery { addEvent(any()) } answers { events += firstArg<ListeningEvent>() }
  }
  private val holder = PlaybackIntentHolder()

  private val chapterMediaId =
    Json.encodeToString(MediaId.serializer(), MediaId.Chapter(bookId, chapterId))

  private fun mockPlayer(position: () -> Long): Player = mockk(relaxed = true) {
    every { currentMediaItem } returns MediaItem.Builder().setMediaId(chapterMediaId).build()
    every { currentPosition } answers { position() }
  }

  private fun recorder(scope: CoroutineScope): ListeningEventRecorder = ListeningEventRecorder(sessionRepo, eventRepo, holder, scope)

  @Test
  fun `play then pause writes one session with paused reason`() = runTest {
    val recorder = recorder(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
    var position = 0L
    val player = mockPlayer { position }
    recorder.attachTo(player)

    val start = Instant.ofEpochMilli(1_000_000)
    recorder.clock = { start }
    position = 0L
    recorder.onIsPlayingChanged(true)

    recorder.clock = { start.plusMillis(60_000) }
    position = 60_000L
    recorder.onIsPlayingChanged(false)

    saved shouldHaveSize 1
    val session = saved.single()
    session.endReason shouldBe ListeningSessionEndReason.Paused.id
    session.startPositionMs shouldBe 0L
    session.endPositionMs shouldBe 60_000L
    session.chapterId shouldBe chapterId
    session.endChapterId shouldBe chapterId
    session.durationMs shouldBe 60_000L
  }

  @Test
  fun `short session below debounce threshold is discarded`() = runTest {
    val recorder = recorder(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
    var position = 0L
    val player = mockPlayer { position }
    recorder.attachTo(player)

    val start = Instant.ofEpochMilli(1_000_000)
    recorder.clock = { start }
    recorder.onIsPlayingChanged(true)

    recorder.clock = { start.plusMillis(1_000) }
    position = 1_000L
    recorder.onIsPlayingChanged(false)

    saved shouldHaveSize 0
  }

  @Test
  fun `playback ended closes session with end of book reason`() = runTest {
    val recorder = recorder(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
    var position = 0L
    val player = mockPlayer { position }
    recorder.attachTo(player)

    val start = Instant.ofEpochMilli(1_000_000)
    recorder.clock = { start }
    recorder.onIsPlayingChanged(true)

    recorder.clock = { start.plusMillis(60_000) }
    position = 60_000L
    recorder.onPlaybackStateChanged(Player.STATE_ENDED)

    saved shouldHaveSize 1
    saved.single().endReason shouldBe ListeningSessionEndReason.EndOfBook.id
  }

  @Test
  fun `sleep timer pause uses sleep reason and resets flag`() = runTest {
    val recorder = recorder(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
    var position = 0L
    val player = mockPlayer { position }
    recorder.attachTo(player)

    val start = Instant.ofEpochMilli(1_000_000)
    recorder.clock = { start }
    recorder.onIsPlayingChanged(true)

    recorder.clock = { start.plusMillis(60_000) }
    position = 60_000L
    holder.stoppedBySleepTimer = true
    recorder.onIsPlayingChanged(false)

    saved shouldHaveSize 1
    saved.single().endReason shouldBe ListeningSessionEndReason.Sleep.id
    holder.stoppedBySleepTimer shouldBe false
  }

  @Test
  fun `pending pause end position overrides live position`() = runTest {
    val recorder = recorder(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
    var position = 0L
    val player = mockPlayer { position }
    recorder.attachTo(player)

    val start = Instant.ofEpochMilli(1_000_000)
    recorder.clock = { start }
    recorder.onIsPlayingChanged(true)

    recorder.clock = { start.plusMillis(60_000) }
    position = 60_000L
    holder.pendingPauseEndPositionMs = 42_000L
    recorder.onIsPlayingChanged(false)

    saved shouldHaveSize 1
    saved.single().endPositionMs shouldBe 42_000L
    holder.pendingPauseEndPositionMs shouldBe null
  }

  private fun posInfo(positionMs: Long) = Player.PositionInfo(null, 0, null, null, 0, positionMs, positionMs, C.INDEX_UNSET, C.INDEX_UNSET)

  @Test
  fun `suppressed seek emits no event and resets flag`() = runTest {
    val recorder = recorder(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
    recorder.attachTo(mockPlayer { 0L })
    holder.suppressNextSeek = true

    recorder.onPositionDiscontinuity(posInfo(60_000), posInfo(55_000), Player.DISCONTINUITY_REASON_SEEK)

    events shouldHaveSize 0
    holder.suppressNextSeek shouldBe false
  }

  @Test
  fun `tagged back seek emits back event and clears intent`() = runTest {
    val recorder = recorder(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
    recorder.attachTo(mockPlayer { 0L })
    holder.pendingSeekIntent = ListeningEventType.Back

    recorder.onPositionDiscontinuity(posInfo(30_000), posInfo(10_000), Player.DISCONTINUITY_REASON_SEEK)

    events shouldHaveSize 1
    val event = events.single()
    event.type shouldBe ListeningEventType.Back.id
    event.fromPositionMs shouldBe 30_000L
    event.positionMs shouldBe 10_000L
    event.bookId shouldBe bookId
    event.chapterId shouldBe chapterId
    holder.pendingSeekIntent shouldBe null
  }

  @Test
  fun `untagged seek emits set position event`() = runTest {
    val recorder = recorder(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
    recorder.attachTo(mockPlayer { 0L })

    recorder.onPositionDiscontinuity(posInfo(10_000), posInfo(120_000), Player.DISCONTINUITY_REASON_SEEK)

    events shouldHaveSize 1
    events.single().type shouldBe ListeningEventType.SetPosition.id
  }

  @Test
  fun `auto transition emits auto advance event without intent`() = runTest {
    val recorder = recorder(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
    recorder.attachTo(mockPlayer { 0L })

    recorder.onPositionDiscontinuity(posInfo(180_000), posInfo(0), Player.DISCONTINUITY_REASON_AUTO_TRANSITION)

    events shouldHaveSize 1
    events.single().type shouldBe ListeningEventType.AutoAdvance.id
  }

  @Test
  fun `tagged forward seek emits forward event`() = runTest {
    val recorder = recorder(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
    recorder.attachTo(mockPlayer { 0L })
    holder.pendingSeekIntent = ListeningEventType.Forward

    recorder.onPositionDiscontinuity(posInfo(10_000), posInfo(40_000), Player.DISCONTINUITY_REASON_SEEK)

    events shouldHaveSize 1
    events.single().type shouldBe ListeningEventType.Forward.id
  }
}
