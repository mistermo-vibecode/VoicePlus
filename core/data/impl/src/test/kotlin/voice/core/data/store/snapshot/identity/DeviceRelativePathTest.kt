package voice.core.data.store.snapshot.identity

import androidx.core.net.toUri
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceRelativePathTest {

  private val authority = "com.android.externalstorage.documents"

  private fun docUri(documentId: String): String {
    val enc = java.net.URLEncoder.encode(documentId, "UTF-8").replace("+", "%20")
    return "content://$authority/tree/primary%3ABooks/document/$enc"
  }

  @Test
  fun `documentId is the decoded volume-namespaced last segment`() {
    val uri = docUri("primary:Books/Dune").toUri()
    DeviceRelativePath.documentId(uri) shouldBe "primary:Books/Dune"
    DeviceRelativePath.authority(uri) shouldBe authority
  }

  @Test
  fun `relName is the child tail under the book relPath, sub-path qualified`() {
    val child = docUri("primary:Books/Dune/Disc1/01 - Intro.mp3").toUri()
    DeviceRelativePath.relName(child, "primary:Books/Dune") shouldBe "Disc1/01 - Intro.mp3"
  }

  @Test
  fun `a single-file book yields an empty relName`() {
    val file = docUri("primary:Books/book.m4b").toUri()
    DeviceRelativePath.relName(file, "primary:Books/book.m4b") shouldBe ""
  }

  @Test
  fun `a different volume keeps its prefix and never aliases`() {
    val sdChild = docUri("ABCD-1234:Books/Dune/01.mp3").toUri()
    // Same sub-path on a different volume must NOT resolve under the primary book's relPath.
    DeviceRelativePath.relName(sdChild, "primary:Books/Dune") shouldBe "ABCD-1234:Books/Dune/01.mp3"
  }
}
