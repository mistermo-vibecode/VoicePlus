package voice.app.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.Inject
import voice.navigation.Destination
import voice.navigation.NavEntryProvider

@Inject
class NavEntryResolver(private val providers: Set<NavEntryProvider<*>>) {

  private val typedProviders = providers.associateBy { it.key }

  internal fun registeredClasses() = providers.map { it.key }

  fun create(
    key: Destination.Compose,
    sharedTransitionScope: SharedTransitionScope,
  ): NavEntry<Destination.Compose> {
    @Suppress("UNCHECKED_CAST")
    val provider = typedProviders[key::class]!! as NavEntryProvider<Destination.Compose>
    return provider.create(key, sharedTransitionScope)
  }
}
