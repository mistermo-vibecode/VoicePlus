package voice.features.bookOverview.di

import androidx.compose.runtime.retain.RetainObserver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import voice.features.bookOverview.bottomSheet.BottomSheetViewModel
import voice.features.bookOverview.deleteBook.DeleteBookViewModel
import voice.features.bookOverview.editTitle.EditBookTitleViewModel
import voice.features.bookOverview.fileCover.FileCoverViewModel
import voice.features.bookOverview.overview.BookOverviewViewModel

abstract class BookOverviewScope private constructor()

@GraphExtension(scope = BookOverviewScope::class)
interface BookOverviewGraph : RetainObserver {
  val bookOverviewViewModel: BookOverviewViewModel
  val editBookTitleViewModel: EditBookTitleViewModel
  val bottomSheetViewModel: BottomSheetViewModel
  val deleteBookViewModel: DeleteBookViewModel
  val fileCoverViewModel: FileCoverViewModel

  override fun onRetained() = Unit

  override fun onEnteredComposition() = Unit

  override fun onExitedComposition() = Unit

  override fun onRetired() {
    bookOverviewViewModel.onRetired()
    editBookTitleViewModel.onRetired()
    bottomSheetViewModel.onRetired()
    deleteBookViewModel.onRetired()
  }

  override fun onUnused() {
    bookOverviewViewModel.onUnused()
    editBookTitleViewModel.onUnused()
    bottomSheetViewModel.onUnused()
    deleteBookViewModel.onUnused()
  }

  @GraphExtension.Factory
  @ContributesTo(AppScope::class)
  interface Factory {
    fun create(): BookOverviewGraph

    @ContributesTo(AppScope::class)
    interface Provider {
      val bookOverviewGraphProviderFactory: Factory
    }
  }
}
