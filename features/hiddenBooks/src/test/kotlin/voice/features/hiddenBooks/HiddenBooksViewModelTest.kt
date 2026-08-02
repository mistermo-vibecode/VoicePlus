package voice.features.hiddenBooks

import androidx.datastore.core.DataStore
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.MediaScanWaiter
import voice.core.data.repo.BookContentRepo
import voice.navigation.Navigator
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class HiddenBooksViewModelTest {

  private val harryPotter = bookId("Harry Potter")
  private val dune = bookId("Dune")

  private val excluded = FakeSetStore()
  private val contentRepo = FakeContentRepo()
  private val scanner = RecordingScanWaiter()

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() = HiddenBooksViewModel(
    contentRepo = contentRepo,
    excludedBooksStore = excluded,
    mediaScanWaiter = scanner,
    navigator = mockk(relaxed = true),
  )

  @Test
  fun `a hidden book is listed under its own title`() {
    // Hiding deactivates the book, and BookRepository filters inactive books out — so reading titles
    // from there listed every hidden book as a raw percent-encoded URI instead.
    contentRepo.contents.value = listOf(content(harryPotter, "Harry Potter", isActive = false))

    val state = hiddenBooksViewState(setOf(harryPotter.value), contentRepo.contents.value)

    state.books.map { it.name } shouldBe listOf("Harry Potter")
    state.books.single().id shouldBe harryPotter.value
  }

  @Test
  fun `an id with no book row left falls back to a readable, decoded name`() {
    val state = hiddenBooksViewState(setOf(harryPotter.value), contents = emptyList())
    // Not "primary%3AAudiobooks%2FHarry%20Potter".
    state.books.single().name shouldBe "Harry Potter"
  }

  @Test
  fun `books are listed alphabetically`() {
    contentRepo.contents.value = listOf(
      content(harryPotter, "Harry Potter", isActive = false),
      content(dune, "Dune", isActive = false),
    )
    val state = hiddenBooksViewState(setOf(harryPotter.value, dune.value), contentRepo.contents.value)
    state.books.map { it.name } shouldBe listOf("Dune", "Harry Potter")
  }

  @Test
  fun `restore un-hides the book, reactivates it and rescans`() = runTest {
    contentRepo.contents.value = listOf(content(harryPotter, "Harry Potter", isActive = false))
    excluded.value.value = setOf(harryPotter.value)

    viewModel().restore(harryPotter.value)

    excluded.value.value shouldBe emptySet()
    contentRepo.contents.value.single().isActive shouldBe true
    // Without a scan an in-flight one can flip the book straight back to inactive, leaving it in
    // neither the library nor this list.
    scanner.scans shouldBe 1
  }

  @Test
  fun `restore all only clears the books it listed, keeping concurrently hidden ones`() = runTest {
    contentRepo.contents.value = listOf(
      content(harryPotter, "Harry Potter", isActive = false),
      content(dune, "Dune", isActive = false),
    )
    excluded.value.value = setOf(harryPotter.value)
    // A backup restore merges another hidden id in while Restore All is running.
    excluded.onUpdate = { excluded.value.value = it + dune.value }

    viewModel().restoreAll()

    excluded.value.value shouldBe setOf(dune.value)
    contentRepo.contents.value.first { it.id == harryPotter }.isActive shouldBe true
    contentRepo.contents.value.first { it.id == dune }.isActive shouldBe false
  }

  private fun bookId(name: String) = BookId(
    "content://com.android.externalstorage.documents/tree/primary%3AAudiobooks/document/primary%3AAudiobooks%2F${name.replace(
      " ",
      "%20",
    )}",
  )

  private fun content(
    id: BookId,
    name: String,
    isActive: Boolean,
  ) = BookContent(
    id = id,
    playbackSpeed = 1f,
    skipSilence = false,
    isActive = isActive,
    lastPlayedAt = Instant.EPOCH,
    author = null,
    name = name,
    addedAt = Instant.EPOCH,
    chapters = listOf(ChapterId("${id.value}/1.mp3")),
    currentChapter = ChapterId("${id.value}/1.mp3"),
    positionInChapter = 0,
    cover = null,
    gain = 0f,
    genre = null,
    narrator = null,
    series = null,
    part = null,
  )
}

private class FakeSetStore : DataStore<Set<String>> {
  val value = MutableStateFlow<Set<String>>(emptySet())

  /** Hook to simulate another writer touching the store between a read and the update. */
  var onUpdate: ((Set<String>) -> Unit)? = null

  override val data: Flow<Set<String>> get() = value

  override suspend fun updateData(transform: suspend (Set<String>) -> Set<String>): Set<String> {
    onUpdate?.let { hook ->
      onUpdate = null
      hook(value.value)
    }
    return value.updateAndGet { transform(it) }
  }
}

private class FakeContentRepo : BookContentRepo {
  val contents = MutableStateFlow<List<BookContent>>(emptyList())

  override fun flow(): Flow<List<BookContent>> = contents
  override suspend fun all(): List<BookContent> = contents.value
  override fun flow(id: BookId): Flow<BookContent?> = contents.map { list -> list.find { it.id == id } }
  override suspend fun get(id: BookId): BookContent? = contents.value.find { it.id == id }

  override suspend fun setAllInactiveExcept(ids: List<BookId>) {
    contents.value = contents.value.map { it.copy(isActive = it.id in ids) }
  }

  override suspend fun put(content: BookContent) {
    contents.value = contents.value.map { if (it.id == content.id) content else it }
  }

  override suspend fun invalidateCache() {}
}

private class RecordingScanWaiter : MediaScanWaiter {
  var scans = 0
    private set

  override suspend fun scanAndAwait() {
    scans++
  }
}
