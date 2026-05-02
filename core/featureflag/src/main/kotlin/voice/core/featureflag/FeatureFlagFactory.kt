package voice.core.featureflag

import dev.zacsweers.metro.Inject

@Inject
class FeatureFlagFactory {

  fun boolean(
    key: String,
    description: String,
    defaultValue: Boolean = false,
  ): FeatureFlag<Boolean> = MemoryFeatureFlag(
    initialValue = defaultValue,
    key = key,
    description = description,
  )

  fun string(
    key: String,
    description: String,
    defaultValue: String,
  ): FeatureFlag<String> = MemoryFeatureFlag(
    initialValue = defaultValue,
    key = key,
    description = description,
  )
}
