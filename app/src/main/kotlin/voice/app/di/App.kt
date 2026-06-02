package voice.app.di

import android.app.Application
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import voice.core.common.rootGraph
import voice.core.data.store.snapshot.LibrarySnapshotService
import voice.core.initializer.AppInitializer

@HasMemberInjections
open class App : Application() {

  @Inject
  lateinit var appInitializers: Set<AppInitializer>

  @Inject
  lateinit var librarySnapshotService: LibrarySnapshotService

  override fun onCreate() {
    super.onCreate()

    rootGraph = createGraph()
      .also { graph ->
        graph.inject(this)
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
