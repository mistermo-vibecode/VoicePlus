plugins {
  alias(libs.plugins.compose.compiler) apply false
  id("voice.ktlint")
}

tasks.wrapper {
  distributionType = Wrapper.DistributionType.ALL
}

tasks.register("criticalTest") {
  group = "verification"
  description = "Runs the fast playback, persistence, scanner, and sleep-timer safety net."
  dependsOn(
    ":core:data:impl:testDebugUnitTest",
    ":core:playback:testDebugUnitTest",
    ":core:scanner:testDebugUnitTest",
    ":core:sleepTimer:impl:testDebugUnitTest",
    ":features:playbackScreen:testDebugUnitTest",
  )
}
