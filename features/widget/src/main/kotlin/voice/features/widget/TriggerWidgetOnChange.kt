package voice.features.widget

import android.app.Application
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.SeekTimeStore
import voice.core.initializer.AppInitializer
import voice.core.playback.playstate.PlayStateManager

@ContributesIntoSet(AppScope::class)
class TriggerWidgetOnChange(
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
  @SeekTimeStore
  private val seekTimeStore: DataStore<Int>,
  private val repo: BookRepository,
  private val chapterNameOverrideRepo: ChapterNameOverrideRepo,
  private val playStateManager: PlayStateManager,
  private val widgetUpdater: WidgetUpdater,
  private val scope: CoroutineScope,
) : AppInitializer {

  override fun onAppStart(application: Application) {
    anythingChanged()
      // A single book switch makes several of the merged sources emit at once; conflate so the
      // burst collapses into one widget refresh instead of redundant Room reads + cover decodes.
      .conflate()
      .onEach {
        widgetUpdater.update()
      }
      .launchIn(scope)
  }

  private fun anythingChanged(): Flow<Any?> {
    return merge(
      currentBookChanged(),
      playStateChanged(),
      bookIdChanged(),
      overridesChanged(),
      seekTimeStore.data.distinctUntilChanged().map { },
    )
  }

  private fun overridesChanged(): Flow<Any?> {
    return currentBookStore.data.filterNotNull()
      .flatMapLatest { id -> chapterNameOverrideRepo.overridesForBook(id) }
      .distinctUntilChanged()
      .map { }
  }

  private fun bookIdChanged(): Flow<BookId?> {
    return currentBookStore.data.distinctUntilChanged()
  }

  private fun playStateChanged(): Flow<PlayStateManager.PlayState> {
    return playStateManager.flow
  }

  private fun currentBookChanged(): Flow<Book> {
    return currentBookStore.data.filterNotNull()
      .flatMapLatest { id ->
        repo.flow(id)
      }
      .filterNotNull()
      .distinctUntilChanged { previous, current ->
        previous.id == current.id &&
          previous.content.chapters == current.content.chapters &&
          previous.content.currentChapter == current.content.currentChapter &&
          previous.content.chapterNameOffset == current.content.chapterNameOffset &&
          // Compare the mark's startMs too: two marks in the same chapter can share a raw name but
          // resolve to different overrides, so a same-named mark crossing must still refresh.
          previous.currentMark.startMs == current.currentMark.startMs &&
          (previous.currentMark.name ?: "") == (current.currentMark.name ?: "")
      }
  }
}
