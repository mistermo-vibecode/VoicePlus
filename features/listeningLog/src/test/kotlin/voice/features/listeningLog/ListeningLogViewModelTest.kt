package voice.features.listeningLog

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride
import voice.core.data.ListeningEvent
import voice.core.data.ListeningEventType
import voice.core.data.ListeningSession
import voice.core.data.ListeningSessionEndReason
import voice.core.data.MarkData
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.core.data.repo.ListeningEventRepo
import voice.core.data.repo.ListeningSessionRepo
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ListeningLogViewModelTest {

  private val bookId = BookId("content://books/1")
  private val chapterId = ChapterId("content://chapters/1")

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun book(offset: Int): Book {
    val chapter = Chapter(
      id = chapterId,
      name = "Chapter 5",
      duration = 60_000L,
      fileLastModified = Instant.EPOCH,
      markData = listOf(MarkData(startMs = 0L, name = "Chapter 5")),
    )
    return Book(
      content = BookContent(
        id = bookId,
        playbackSpeed = 1F,
        skipSilence = false,
        isActive = true,
        lastPlayedAt = Instant.EPOCH,
        author = null,
        name = "TestBook",
        addedAt = Instant.EPOCH,
        chapters = listOf(chapter.id),
        currentChapter = chapter.id,
        positionInChapter = 0L,
        cover = null,
        gain = 0F,
        genre = null,
        narrator = null,
        series = null,
        part = null,
        chapterNameOffset = offset,
      ),
      chapters = listOf(chapter),
    )
  }

  private fun session(
    id: Long = 1L,
    startedAt: Instant = Instant.EPOCH,
    endedAt: Instant = Instant.EPOCH.plusSeconds(60),
    startPositionMs: Long = 0L,
    endPositionMs: Long = 0L,
    endReason: Int? = null,
  ): ListeningSession = ListeningSession(
    id = id,
    bookId = bookId,
    chapterId = chapterId,
    startedAt = startedAt,
    endedAt = endedAt,
    durationMs = endedAt.toEpochMilli() - startedAt.toEpochMilli(),
    startPositionMs = startPositionMs,
    endPositionMs = endPositionMs,
    endChapterId = null,
    endReason = endReason,
  )

  private fun event(
    id: Long,
    type: ListeningEventType,
    at: Instant,
    positionMs: Long = 0L,
    fromPositionMs: Long? = null,
  ): ListeningEvent = ListeningEvent(
    id = id,
    bookId = bookId,
    type = type.id,
    chapterId = chapterId,
    positionMs = positionMs,
    fromPositionMs = fromPositionMs,
    at = at,
  )

  private fun viewModel(
    book: Book,
    overrides: List<ChapterNameOverride> = emptyList(),
    sessions: List<ListeningSession> = listOf(session()),
    events: List<ListeningEvent> = emptyList(),
  ): ListeningLogViewModel {
    val sessionRepo = mockk<ListeningSessionRepo>(relaxed = true) {
      every { sessions(bookId) } returns MutableStateFlow(sessions)
    }
    val eventRepo = mockk<ListeningEventRepo>(relaxed = true) {
      every { events(bookId) } returns MutableStateFlow(events)
    }
    val bookRepo = mockk<BookRepository>(relaxed = true) {
      every { flow(bookId) } returns MutableStateFlow(book)
    }
    val overrideRepo = mockk<ChapterNameOverrideRepo>(relaxed = true) {
      every { overridesForBook(bookId) } returns MutableStateFlow(overrides)
    }
    return ListeningLogViewModel(
      sessionRepo = sessionRepo,
      eventRepo = eventRepo,
      bookRepo = bookRepo,
      chapterNameOverrideRepo = overrideRepo,
      playerController = mockk(relaxed = true),
      navigator = mockk(relaxed = true),
      context = mockk(relaxed = true),
      bookId = bookId,
    )
  }

  @Test
  fun `offset is applied to chapter names in the log`() = runTest {
    val vm = viewModel(book(offset = 2))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitItem()
      while (state.groups.flatMap { it.entries }.none { it.chapterName == "Chapter 7" }) {
        state = awaitItem()
      }
      val names = state.groups.flatMap { it.entries }.map { it.chapterName }
      assertTrue("expected all entries to be 'Chapter 7' but was $names", names.isNotEmpty() && names.all { it == "Chapter 7" })
    }
  }

  @Test
  fun `override takes precedence in the log`() = runTest {
    val override = ChapterNameOverride(
      chapterId = chapterId.value,
      markStartMs = 0L,
      bookId = bookId.value,
      name = "Finale",
    )
    val vm = viewModel(book(offset = 2), overrides = listOf(override))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitItem()
      while (state.groups.flatMap { it.entries }.none { it.chapterName == "Finale" }) {
        state = awaitItem()
      }
      val names = state.groups.flatMap { it.entries }.map { it.chapterName }
      assertTrue("expected all entries to be 'Finale' but was $names", names.all { it == "Finale" })
    }
  }

  @Test
  fun `a back event shows as a transport entry in time order`() = runTest {
    val vm = viewModel(
      book(offset = 0),
      events = listOf(event(id = 1L, type = ListeningEventType.Back, at = Instant.EPOCH.plusSeconds(30))),
    )
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      val entries = awaitEntries { it.any { e -> e is ListeningLogEntry.Transport } }
      val transports = entries.filterIsInstance<ListeningLogEntry.Transport>()
      assertEquals(1, transports.size)
      assertEquals(ListeningEventType.Back, transports.single().type)
      // Time-sorted DESC: Pause(@60s) > Back(@30s) > Play(@0s)
      assertEquals(
        listOf(
          ListeningLogEntry.Pause::class,
          ListeningLogEntry.Transport::class,
          ListeningLogEntry.Play::class,
        ),
        entries.map { it::class },
      )
    }
  }

  @Test
  fun `no fabricated skip from a position gap between sessions`() = runTest {
    val first = session(id = 1L, startedAt = Instant.EPOCH, endedAt = Instant.EPOCH.plusSeconds(60))
    // Second session starts far later in the book; the old code fabricated a "Skip" for the gap.
    val second = session(
      id = 2L,
      startedAt = Instant.EPOCH.plusSeconds(120),
      endedAt = Instant.EPOCH.plusSeconds(180),
      startPositionMs = 600_000L,
      endPositionMs = 600_000L,
    )
    val vm = viewModel(book(offset = 0), sessions = listOf(first, second))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      val entries = awaitEntries { it.count { e -> e is ListeningLogEntry.Play } == 2 }
      assertTrue("expected no Transport entries but was $entries", entries.none { it is ListeningLogEntry.Transport })
    }
  }

  @Test
  fun `adjacent back events coalesce within the window but not beyond it`() = runTest {
    val close = viewModel(
      book(offset = 0),
      events = listOf(
        event(id = 1L, type = ListeningEventType.Back, at = Instant.EPOCH.plusSeconds(10)),
        event(id = 2L, type = ListeningEventType.Back, at = Instant.EPOCH.plusSeconds(11)),
      ),
    )
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { close.viewState() }.test {
      val entries = awaitEntries { it.any { e -> e is ListeningLogEntry.Transport } }
      assertEquals(1, entries.count { it is ListeningLogEntry.Transport })
    }

    val far = viewModel(
      book(offset = 0),
      events = listOf(
        event(id = 1L, type = ListeningEventType.Back, at = Instant.EPOCH.plusSeconds(10)),
        event(id = 2L, type = ListeningEventType.Back, at = Instant.EPOCH.plusSeconds(15)),
      ),
    )
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { far.viewState() }.test {
      val entries = awaitEntries { it.count { e -> e is ListeningLogEntry.Transport } == 2 }
      assertEquals(2, entries.count { it is ListeningLogEntry.Transport })
    }
  }

  @Test
  fun `an event with an unknown type is dropped and produces no Transport entry`() = runTest {
    val unknownTypeEvent = ListeningEvent(
      id = 99L,
      bookId = bookId,
      type = 99,
      chapterId = chapterId,
      positionMs = 0L,
      fromPositionMs = null,
      at = Instant.EPOCH.plusSeconds(30),
    )
    val vm = viewModel(book(offset = 0), events = listOf(unknownTypeEvent))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      // Wait for a stable state that has the session's Play and Pause entries.
      val entries = awaitEntries { it.count { e -> e is ListeningLogEntry.Play } == 1 }
      assertTrue("expected no Transport entries but was $entries", entries.none { it is ListeningLogEntry.Transport })
    }
  }

  @Test
  fun `a session ended by the sleep timer carries the sleep end reason`() = runTest {
    val vm = viewModel(
      book(offset = 0),
      sessions = listOf(session(endReason = ListeningSessionEndReason.Sleep.id)),
    )
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      val entries = awaitEntries { it.any { e -> e is ListeningLogEntry.Pause } }
      val pause = entries.filterIsInstance<ListeningLogEntry.Pause>().single()
      assertEquals(ListeningSessionEndReason.Sleep, pause.endReason)
    }
  }

  @Test
  fun `a play shortly after a sleep stop is marked resumed-after-sleep`() = runTest {
    val sleepEnd = Instant.EPOCH.plusSeconds(60)
    val vm = viewModel(
      book(offset = 0),
      sessions = listOf(
        session(id = 1, endedAt = sleepEnd, endReason = ListeningSessionEndReason.Sleep.id),
        // Half-asleep resume 5 minutes later.
        session(id = 2, startedAt = sleepEnd.plusSeconds(300), endedAt = sleepEnd.plusSeconds(900)),
      ),
    )
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      val entries = awaitEntries { it.count { e -> e is ListeningLogEntry.Play } == 2 }
      val plays = entries.filterIsInstance<ListeningLogEntry.Play>()
      assertEquals(true, plays.single { it.id == "s2-play" }.resumedAfterSleep)
      assertEquals(false, plays.single { it.id == "s1-play" }.resumedAfterSleep)
    }
  }

  @Test
  fun `a play long after a sleep stop is not marked`() = runTest {
    val sleepEnd = Instant.EPOCH.plusSeconds(60)
    val vm = viewModel(
      book(offset = 0),
      sessions = listOf(
        session(id = 1, endedAt = sleepEnd, endReason = ListeningSessionEndReason.Sleep.id),
        // Next morning, two hours later: a fresh awake session.
        session(id = 2, startedAt = sleepEnd.plusSeconds(7_200), endedAt = sleepEnd.plusSeconds(7_800)),
      ),
    )
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      val entries = awaitEntries { it.count { e -> e is ListeningLogEntry.Play } == 2 }
      assertTrue(entries.filterIsInstance<ListeningLogEntry.Play>().none { it.resumedAfterSleep })
    }
  }

  @Test
  fun `a play after a normal pause is not marked`() = runTest {
    val pauseEnd = Instant.EPOCH.plusSeconds(60)
    val vm = viewModel(
      book(offset = 0),
      sessions = listOf(
        session(id = 1, endedAt = pauseEnd, endReason = ListeningSessionEndReason.Paused.id),
        session(id = 2, startedAt = pauseEnd.plusSeconds(300), endedAt = pauseEnd.plusSeconds(900)),
      ),
    )
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      val entries = awaitEntries { it.count { e -> e is ListeningLogEntry.Play } == 2 }
      assertTrue(entries.filterIsInstance<ListeningLogEntry.Play>().none { it.resumedAfterSleep })
    }
  }

  @Test
  fun `a go-to-chapter event renders as its own transport entry`() = runTest {
    val vm = viewModel(
      book(offset = 0),
      events = listOf(event(id = 1L, type = ListeningEventType.GoToChapter, at = Instant.EPOCH.plusSeconds(30))),
    )
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      val entries = awaitEntries { it.any { e -> e is ListeningLogEntry.Transport } }
      assertEquals(
        ListeningEventType.GoToChapter,
        entries.filterIsInstance<ListeningLogEntry.Transport>().single().type,
      )
    }
  }
}

private suspend fun app.cash.turbine.ReceiveTurbine<ListeningLogViewState>.awaitEntries(
  predicate: (List<ListeningLogEntry>) -> Boolean,
): List<ListeningLogEntry> {
  while (true) {
    val entries = awaitItem().groups.flatMap { it.entries }
    if (predicate(entries)) return entries
  }
}
