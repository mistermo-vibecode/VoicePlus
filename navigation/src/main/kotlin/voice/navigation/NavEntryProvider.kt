package voice.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.navigation3.runtime.NavEntry
import kotlin.reflect.KClass

class NavEntryProvider<T : Destination.Compose>(
  val key: KClass<T>,
  val create: (key: T, sharedTransitionScope: SharedTransitionScope) -> NavEntry<Destination.Compose>,
)

inline fun <reified T : Destination.Compose> NavEntryProvider(
  noinline create: (key: T) -> NavEntry<Destination.Compose>,
): NavEntryProvider<T> {
  return NavEntryProvider(T::class) { key, _ -> create(key) }
}

inline fun <reified T : Destination.Compose> SharedTransitionNavEntryProvider(
  noinline create: (key: T, sharedTransitionScope: SharedTransitionScope) -> NavEntry<Destination.Compose>,
): NavEntryProvider<T> {
  return NavEntryProvider(T::class, create)
}
