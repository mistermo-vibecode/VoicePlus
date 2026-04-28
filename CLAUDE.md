# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

VoicePlus is a fork of [PaulWoitaschek/Voice](https://github.com/PaulWoitaschek/Voice), an Android audiobook player. The fork strips all telemetry (Firebase Analytics/Crashlytics/Remote Config modules deleted entirely) and adds listening stats, character lists, customizable media-button actions, an N-chapter sleep timer, and other features documented in [CHANGELOG.md](CHANGELOG.md). The current branch `sync-from-voice` is used to merge upstream Voice changes.

## Common commands

```bash
./gradlew voiceUnitTest                  # run unit tests across all modules
./gradlew :<module>:testDebugUnitTest    # run a single module's unit tests
./gradlew lintKotlin                     # ktlint (run before opening a PR)
./gradlew formatKotlin                   # auto-fix ktlint violations
./gradlew :app:lintLibreDebug            # Android Lint (CI gate)
./gradlew :app:assembleLibreDebug        # debug APK
./gradlew :app:assembleLibreRelease      # unsigned release APK (F-Droid build)
kotlin scripts/new_module.main.kts :features:foo   # scaffold a new module + register it
kotlin scripts/release.main.kts          # compute & push next release tag (date-based versioning)
```

CI runs `:app:lintLibreDebug`, then `voiceUnitTest lintKotlin :app:assembleLibreDebug`. Match this locally before pushing.

## Toolchain

- **JDK 21** required (Gradle toolchain + CI). The convention-plugin module (`plugins/`) targets language version 21; `baseSetup.kt` sets the Kotlin toolchain to 21 for all modules but compiles bytecode to JVM 11 / Java 11.
- **Android SDK**: compile/target 36, min 28, robolectric 35. Versions live in [gradle/libs.versions.toml](gradle/libs.versions.toml) under `[versions]`.
- **Gradle 9 + AGP 9 + Kotlin 2.3**. Configuration cache and parallel execution are enabled.
- `voice.warningsAsErrors=true` in [gradle.properties](gradle.properties) — Kotlin warnings fail the build. Don't suppress; fix them.

## Module layout

- `app/` — application entry, wires every feature/core module. Has product flavors `libre` (F-Droid, default) and `proprietary` (currently unused but kept for upstream compatibility).
- `core/*` — `common`, `data` (api/impl split), `playback`, `scanner`, `search`, `sleepTimer` (api/impl), `ui`, `strings`, `documentfile`, `initializer`, `logging` (api/debug), `featureflag`.
- `features/*` — UI features: `bookOverview`, `bookmark`, `cover`, `folderPicker`, `onboarding`, `playbackScreen`, `settings`, `sleepTimer`, `widget`, `review` (noop/play split).
- `navigation/` — navigation graph (uses AndroidX `navigation3`).
- `plugins/` — included build with **convention plugins** that every module applies. Source: [plugins/src/main/kotlin/](plugins/src/main/kotlin/).

### Convention plugins (apply these, not raw AGP/Kotlin plugins)

| Plugin id        | When to use                                              |
|------------------|----------------------------------------------------------|
| `voice.app`      | Only `app/`. Applies AGP application + ktlint + adds `voiceUnitTest` task. |
| `voice.library`  | All `core/*` and `features/*` modules.                    |
| `voice.compose`  | Add alongside `voice.library` (or `voice.app`) for any Compose UI. Applies the Compose compiler plugin and adds the `compose` bundle from the version catalog. |
| `voice.ktlint`   | Pulled in transitively by the above; rarely applied directly. |

`baseSetup.kt` auto-derives `android.namespace` from the Gradle path (`:features:bookOverview` → `voice.features.bookOverview`). Don't set `namespace` manually in module build files.

## Patterns to know

- **DI**: [Metro](https://github.com/zacsweers/metro) (not Dagger/Hilt/Anvil). Modules that participate add `alias(libs.plugins.metro)` in their `build.gradle.kts`.
- **UI**: 100% Jetpack Compose + Material 3. ViewModels typically use Molecule (`app.cash.molecule`) to expose state.
- **Persistence**: Room (`core/data/impl`), DataStore Preferences for settings.
- **Playback**: Media3 ExoPlayer + MediaSession.
- **Test conventions**: JVM tests use JUnit4 + MockK + Turbine + Robolectric. `failOnNoDiscoveredTests=false` is set on library modules so `voiceUnitTest` works as a repo-wide task even for modules without tests.
- **Reproducible builds**: `dependenciesInfo.includeInApk = false` in `app/build.gradle.kts`. Don't re-enable.

## Upstream sync setup

This repo merges from upstream Voice (`PaulWoitaschek/Voice`) periodically. The `.gitattributes` file marks several VoicePlus identity files (README, PRIVACY, CREDITS, CI workflows, English store text, `metadata/`) with `merge=ours` so they don't conflict on every sync.

**One-time setup per clone** — the `merge=ours` attribute requires a custom merge driver:

```bash
git config merge.ours.driver true
```

This writes to local `.git/config` (not tracked). Run it once after cloning. Without it, `merge=ours` silently does nothing and you'll see the conflicts again.

## Gotchas

- `fastlane/Fastfile` references `assembleGithubRelease` / `bundlePlayRelease` flavors that no longer exist (the flavors are now `libre`/`proprietary`). It's inherited from upstream and is not used by this fork's CI — release builds go through [.github/workflows/release.yml](.github/workflows/release.yml) which calls `:app:assembleLibreRelease` directly.
- Release versioning is date-based: `major = year - 2000 + 28`, `minor = month`, `patch` increments per release that month. See [scripts/release.main.kts](scripts/release.main.kts). Don't bump `versionName`/`versionCode` in `app/build.gradle.kts` by hand; the release script tags and the CI build picks up the tag.
- Telemetry is intentionally absent. If you find yourself reaching for analytics, crash reporting, or remote config, that's a deliberate non-goal of this fork — see the v1.13 entry in [CHANGELOG.md](CHANGELOG.md).
- The package id is `com.github.mistermo_vibecode.voiceplus` (debug builds get `.debug` suffix). Don't change without coordinating with the F-Droid listing.
