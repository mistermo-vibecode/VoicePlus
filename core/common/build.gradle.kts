plugins {
  id("voice.library")
}

dependencies {
  implementation(libs.compose.runtime.retain)
  implementation(libs.serialization.json)
  implementation(libs.androidxCore)

  testImplementation(libs.bundles.testing.jvm)
}
