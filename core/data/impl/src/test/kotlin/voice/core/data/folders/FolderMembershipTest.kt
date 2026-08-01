package voice.core.data.folders

import androidx.core.net.toUri
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderMembershipTest {

  private val authority = "com.android.externalstorage.documents"

  private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
  private fun tree(treeDocId: String) = "content://$authority/tree/${enc(treeDocId)}".toUri()
  private fun doc(
    treeDocId: String,
    docId: String,
  ) = "content://$authority/tree/${enc(treeDocId)}/document/${enc(docId)}".toUri()
  private fun singleFile(docId: String) = "content://$authority/document/${enc(docId)}".toUri()

  @Test
  fun `Root - a direct child is under the folder`() {
    FolderMembership.isBookUnderFolder(
      bookUri = doc("primary:Books", "primary:Books/Dune"),
      folderUri = tree("primary:Books"),
      folderType = FolderType.Root,
    ) shouldBe true
  }

  @Test
  fun `Root - a sibling tree sharing a string prefix is NOT under the folder`() {
    FolderMembership.isBookUnderFolder(
      bookUri = doc("primary:BooksArchive", "primary:BooksArchive/Dune"),
      folderUri = tree("primary:Books"),
      folderType = FolderType.Root,
    ) shouldBe false
  }

  @Test
  fun `Author - a grandchild (book inside an author folder) is under the folder`() {
    FolderMembership.isBookUnderFolder(
      bookUri = doc("primary:Audiobooks", "primary:Audiobooks/Herbert/Dune"),
      folderUri = tree("primary:Audiobooks"),
      folderType = FolderType.Author,
    ) shouldBe true
  }

  @Test
  fun `Author - a loose direct-child file is under the folder`() {
    FolderMembership.isBookUnderFolder(
      bookUri = doc("primary:Audiobooks", "primary:Audiobooks/loose.mp3"),
      folderUri = tree("primary:Audiobooks"),
      folderType = FolderType.Author,
    ) shouldBe true
  }

  @Test
  fun `SingleFolder - matches only the folder document itself, not its children`() {
    val folder = tree("primary:Books/Dune")
    FolderMembership.isBookUnderFolder(
      doc("primary:Books/Dune", "primary:Books/Dune"),
      folder,
      FolderType.SingleFolder,
    ) shouldBe true
    FolderMembership.isBookUnderFolder(
      doc("primary:Books/Dune", "primary:Books/Dune/01.mp3"),
      folder,
      FolderType.SingleFolder,
    ) shouldBe false
  }

  @Test
  fun `SingleFile - matches the verbatim configured single-file URI`() {
    val fileUri = singleFile("primary:Books/book.m4b")
    FolderMembership.isBookUnderFolder(fileUri, fileUri, FolderType.SingleFile) shouldBe true
    FolderMembership.isBookUnderFolder(
      singleFile("primary:Books/other.m4b"),
      fileUri,
      FolderType.SingleFile,
    ) shouldBe false
  }

  @Test
  fun `a different storage authority is never under the folder`() {
    FolderMembership.isBookUnderFolder(
      "content://other.provider/tree/primary%3ABooks/document/primary%3ABooks%2FDune".toUri(),
      tree("primary:Books"),
      FolderType.Root,
    ) shouldBe false
  }

  @Test
  fun `percent-encoded documentIds compare on the decoded form`() {
    FolderMembership.isBookUnderFolder(
      bookUri = doc("primary:My Books", "primary:My Books/A Tale.mp3"),
      folderUri = tree("primary:My Books"),
      folderType = FolderType.Root,
    ) shouldBe true
  }
}
