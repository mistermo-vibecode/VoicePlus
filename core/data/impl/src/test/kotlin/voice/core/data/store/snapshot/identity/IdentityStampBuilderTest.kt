package voice.core.data.store.snapshot.identity

import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class IdentityStampBuilderTest {

  private val authority = "com.android.externalstorage.documents"

  private fun docUri(documentId: String): String {
    val enc = java.net.URLEncoder.encode(documentId, "UTF-8").replace("+", "%20")
    return "content://$authority/tree/primary%3ABooks/document/$enc"
  }

  private fun chapter(documentId: String) = Chapter(
    id = ChapterId(docUri(documentId)),
    name = null,
    duration = 1_000,
    fileLastModified = Instant.EPOCH,
    markData = emptyList(),
  )

  private fun book(
    documentId: String,
    chapterDocIds: List<String>,
  ): BookContent {
    val chapterIds = chapterDocIds.map { ChapterId(docUri(it)) }
    return BookContent(
      id = BookId(docUri(documentId)), playbackSpeed = 1f, skipSilence = false, isActive = true,
      lastPlayedAt = Instant.EPOCH, author = null, name = "B", addedAt = Instant.EPOCH,
      chapters = chapterIds, currentChapter = chapterIds.first(), positionInChapter = 0,
      cover = null, gain = 0f, genre = null, narrator = null, series = null, part = null,
    )
  }

  @Test
  fun `folder book stamp captures relPath, folderName, children and authority`() {
    val stamp = IdentityStampBuilder.build(
      book = book("primary:Books/Dune", listOf("primary:Books/Dune/01.mp3", "primary:Books/Dune/Disc2/02.mp3")),
      chapters = listOf(chapter("primary:Books/Dune/01.mp3"), chapter("primary:Books/Dune/Disc2/02.mp3")),
    )
    stamp.authority shouldBe authority
    stamp.isSingleFile shouldBe false
    stamp.relPath shouldBe "primary:Books/Dune"
    stamp.folderName shouldBe "Dune"
    stamp.children shouldBe listOf("01.mp3", "Disc2/02.mp3")
  }

  @Test
  fun `single-file book is detected as single-file`() {
    val stamp = IdentityStampBuilder.build(
      book = book("primary:Books/book.m4b", listOf("primary:Books/book.m4b")),
      chapters = listOf(chapter("primary:Books/book.m4b")),
    )
    stamp.isSingleFile shouldBe true
    stamp.relPath shouldBe "primary:Books/book.m4b"
    stamp.children.single() shouldBe ""
  }
}
