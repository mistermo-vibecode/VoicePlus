package voice.features.chapterEditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.navigation.Destination
import voice.navigation.NavEntryProvider

@Composable
public fun ChapterEditorScreen(viewModel: ChapterEditorViewModel) {
  // Placeholder — full implementation in Task 9
}

@ContributesTo(AppScope::class)
public interface ChapterEditorProvider {
  @Provides
  @IntoSet
  public fun chapterEditorNavEntryProvider(
    factory: ChapterEditorViewModel.Factory,
  ): NavEntryProvider<*> = NavEntryProvider<Destination.ChapterEditor> { key ->
    NavEntry(key) {
      val viewModel = remember(key) { factory.create(key.bookId) }
      ChapterEditorScreen(viewModel = viewModel)
    }
  }
}
