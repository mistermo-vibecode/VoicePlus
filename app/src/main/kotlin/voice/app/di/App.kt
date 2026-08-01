package voice.app.di

import android.app.Application
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.runBlocking
import voice.core.common.rootGraph
import voice.core.data.InterruptedSessionFinalizer
import voice.core.data.store.snapshot.LibrarySnapshotService
import voice.core.initializer.AppInitializer

@HasMemberInjections
open class App : Application() {

  @Inject
  lateinit var appInitializers: Set<AppInitializer>

  @Inject
  lateinit var librarySnapshotService: LibrarySnapshotService

  @Inject
  lateinit var interruptedSessionFinalizer: InterruptedSessionFinalizer

  override fun onCreate() {
    super.onCreate()

    rootGraph = createGraph()
      .also { graph ->
        graph.inject(this)
      }

    runBlocking {
      // Finalize a session the dead process couldn't close, then restore-if-wiped. Both run before
      // the snapshot writer starts observing, so neither races a fresh snapshot write.
      interruptedSessionFinalizer.finalizeIfNeeded()
      librarySnapshotService.restoreIfNeeded()
    }
    librarySnapshotService.start()

    appInitializers.forEach {
      it.onAppStart(this)
    }
  }

  open fun createGraph(): AppGraph {
    return createGraphFactory<ProductionAppGraph.Factory>().create(this)
  }
}
