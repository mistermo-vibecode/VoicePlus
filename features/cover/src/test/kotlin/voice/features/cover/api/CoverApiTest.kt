package voice.features.cover.api

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

internal class CoverApiTest {

  private val internalApi = mockk<InternalCoverApi>()
  private val coverApi = CoverApi(internalApi)

  @Test
  fun `token extracts DuckDuckGo request id`() = runTest {
    coEvery { internalApi.auth("unicorns") } returns "html vqd=12345-67890&more"

    coverApi.token("unicorns") shouldBe "12345-67890"
  }

  @Test
  fun `token returns null when response has no request id`() = runTest {
    coEvery { internalApi.auth("unicorns") } returns "unexpected response"

    coverApi.token("unicorns") shouldBe null
  }

  @Test
  fun `search delegates query token and continuation url`() = runTest {
    val response = SearchResponse(next = null, results = emptyList())
    coEvery {
      internalApi.search(url = "/next.js", query = "unicorns", auth = "token")
    } returns response

    coverApi.search(query = "unicorns", auth = "token", url = "/next.js") shouldBe response
  }
}
