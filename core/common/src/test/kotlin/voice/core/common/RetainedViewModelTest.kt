package voice.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertTrue
import org.junit.Test

class RetainedViewModelTest {

  @Test
  fun `retiring cancels owned scope`() {
    val job = Job()
    val viewModel = TestViewModel(CoroutineScope(job))

    viewModel.onRetired()

    assertTrue(job.isCancelled)
  }

  @Test
  fun `unused model cancels owned scope`() {
    val job = Job()
    val viewModel = TestViewModel(CoroutineScope(job))

    viewModel.onUnused()

    assertTrue(job.isCancelled)
  }

  private class TestViewModel(scope: CoroutineScope) : RetainedViewModel(scope)
}
