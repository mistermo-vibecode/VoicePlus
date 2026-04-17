package voice.core.featureflag

import dev.zacsweers.metro.Inject

@Inject
class FeatureFlagFactory {

  fun boolean(
    key: String,
    defaultValue: Boolean = false,
  ): FeatureFlag<Boolean> = object : FeatureFlag<Boolean> {
    override fun get() = defaultValue
  }

  fun string(
    key: String,
    defaultValue: String,
  ): FeatureFlag<String> = object : FeatureFlag<String> {
    override fun get() = defaultValue
  }
}
