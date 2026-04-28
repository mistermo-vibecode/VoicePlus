# Upstream Voice → VoicePlus Sync — Merge Log

**Date:** 2026-04-28
**Branch:** `sync-from-voice`
**Baseline:** `26.2.4-5402004` (upstream commit `f2374fbf85580ec245436166a5d7e0a122809736`, 2026-02-04)
**Target:** `upstream/main` (commit `052a2f032`, 2026-04-27)
**Delta:** 98 upstream commits, 172 files, +2496 / -486 lines
**Method:** Temporary `git replace --graft` connecting our root commit to v26.2.4, then standard `git merge upstream/main`. The graft will be removed after the merge completes; commit SHAs are unchanged.

---

## Why this log exists

If the build fails or behavior regresses, this log lets us reconstruct exactly what was kept vs. changed vs. rejected, and re-evaluate any individual decision. Each section maps to a phase of the merge resolution.

---

## Phase 1 — modify/delete conflicts (36 files)

Pattern: upstream modified a file VoicePlus had deleted (or vice versa). Resolution recorded per group.

### D1 — fastlane translation files (32 files, all → `git rm`)
Reason: VoicePlus has its own English-only store metadata under `metadata/`. Upstream-translated "Voice" descriptions don't apply.

```
fastlane/metadata/android/cs-CZ/{short_description,title}.txt
fastlane/metadata/android/de-DE/{full_description,short_description,title}.txt
fastlane/metadata/android/et/{short_description,title}.txt
fastlane/metadata/android/fr-FR/{short_description,title}.txt
fastlane/metadata/android/hu-HU/{short_description,title}.txt
fastlane/metadata/android/iw-IL/{short_description,title}.txt
fastlane/metadata/android/ja-JP/{short_description,title}.txt
fastlane/metadata/android/lt/{short_description,title}.txt
fastlane/metadata/android/nl-NL/{short_description,title}.txt
fastlane/metadata/android/pl-PL/{short_description,title}.txt
fastlane/metadata/android/ro/{short_description,title}.txt
fastlane/metadata/android/ru-RU/{short_description,title}.txt
fastlane/metadata/android/sv-SE/{short_description,title}.txt
fastlane/metadata/android/uk/{short_description,title}.txt
fastlane/metadata/android/zh-CN/{short_description,title}.txt
```

### D2 — developer settings files (3 files, all → `git rm`)
Reason: VoicePlus removed the developer menu in v1.13 (it was the entry point for remote-config refresh, which doesn't exist anymore).

```
features/settings/src/main/kotlin/voice/features/settings/developer/DeveloperSettings.kt
features/settings/src/main/kotlin/voice/features/settings/developer/DeveloperSettingsViewModel.kt
features/settings/src/main/kotlin/voice/features/settings/developer/DeveloperSettingsViewState.kt
```

### D3 — `.github/workflows/deploy_pages.yml` → `git rm`
Reason: VoicePlus has minimal CI (ci.yml + release.yml only); no GitHub Pages deploy.

### D4 — `docs/development.md` → accepted upstream's version
Reason: VoicePlus's `README.md` already references this file as a placeholder. Upstream's newly-written developer docs satisfy that pointer.

---

## Phase 2 — review of 21 added-by-upstream files

### Removed (6 files via `git rm`)
| File | Reason |
|---|---|
| `.github/workflows/copilot-setup-steps.yml` | GitHub Copilot integration; VoicePlus keeps CI minimal |
| `.github/workflows/play_metadata.yml` | Play Store metadata workflow; VoicePlus is F-Droid only |
| `core/featureflag/src/main/kotlin/voice/core/featureflag/FeatureFlagOverride.kt` | Developer-menu remnant |
| `features/settings/src/main/kotlin/voice/features/settings/developer/BooleanFeatureFlagRow.kt` | Developer menu UI |
| `features/settings/src/main/kotlin/voice/features/settings/developer/EditStringFeatureFlagDialog.kt` | Developer menu UI |
| `features/settings/src/main/kotlin/voice/features/settings/developer/StringFeatureFlagRow.kt` | Developer menu UI |

### Kept (15 files — all useful upstream additions)
- `compose_stability.conf` — Compose performance config
- `core/data/api/src/main/kotlin/voice/core/data/folders/PersistedUriPermissions.kt` (new feature: hide audiobook folders)
- `core/data/impl/src/main/kotlin/voice/core/data/folders/PersistedUriPermissionsImpl.kt`
- `core/playback/src/main/kotlin/voice/core/playback/CurrentBookResolver.kt` (playback infrastructure)
- `core/playback/src/main/kotlin/voice/core/playback/LivePlaybackState.kt` (live playback state)
- `core/playback/src/test/kotlin/voice/core/playback/CurrentBookResolverTest.kt`
- `core/scanner/src/test/kotlin/voice/core/scanner/BookParserTest.kt`
- `core/ui/src/debug/res/values/donottranslate.xml`
- `docs/development.md` (already noted in D4)
- `features/bookOverview/src/test/kotlin/voice/features/bookOverview/overview/BookOverviewViewModelTest.kt`
- `features/settings/src/main/kotlin/voice/features/settings/SettingsViewEffect.kt`
- `gradle/gradle-daemon-jvm.properties`
- `scripts/collect_compose_reports.sh`
- `scripts/test-backup-restore.sh`
- `app/src/androidTest/kotlin/voice/app/misc/ScreenshotCapture.kt` (Compose UI test for store screenshots — verified non-telemetry)

---

## Phase 3 — Group A trivial content conflicts (6 files, all `git checkout --ours`)

Reason: VoicePlus identity / minimal CI — never accept upstream's version.

```
.github/actions/build_setup/action.yml
.github/workflows/ci.yml
.github/workflows/release.yml
fastlane/metadata/android/en-US/full_description.txt
fastlane/metadata/android/en-US/short_description.txt
fastlane/metadata/android/en-US/title.txt
```

Example: store title — kept "VoicePlus – Audiobook Player", rejected upstream's "Voice Offline Audiobook Player".

---

## Phase 4 — Group B build/dependency conflicts (8 files)

### `app/build.gradle.kts` — 3 conflict zones, mixed resolution
1. **`defaultConfig` (lines 28–36)**: kept ours (VoicePlus identity).
   - Kept: `applicationId = "com.github.mistermo_vibecode.voiceplus"`, `versionName = "1.19"`, `versionCode = 5404008`
   - Rejected: upstream's parameterized `applicationId = "de.ph1b.audiobook"` and `providers.gradleProperty(...)` for versionName/versionCode.
2. **`flavorDimensions` + signing (lines 41–71)**: kept ours.
   - Kept: simple `flavorDimensions += "distribution"` with libre/proprietary product flavors.
   - Rejected: upstream's `sourceSets` injecting `../Images`, `createSigningConfig` reading `signing/<name>/signing.properties`, `playSigningConfig`, `githubSigningConfig`, and the `signing` flavor dimension.
3. **`debug` build type (lines 88–94)**: combined both sides.
   - Result: `isShrinkResources = false` (ours) + `applicationIdSuffix = ".debug"` (both) + `versionNameSuffix = "-debug"` (upstream — useful for distinguishing debug installs).

**Watch for in build:** lint config — auto-merge dropped your `lintConfig = rootProject.file("lint.xml")` line because upstream commit #3477 moved lint config into the convention plugin. The convention plugin (`baseSetup.kt`) now sets `lint.lintConfig = project.layout.settingsDirectory.file("lint.xml").asFile` for all modules. Your `lint.xml` in repo root still applies.

### `gradle.properties` — kept ours (skipped all upstream additions)
Rejected:
- `org.gradle.unsafe.isolated-projects=true` (incubating Gradle perf feature; risk to custom plugins)
- `ksp.project.isolation.enabled=true` (companion to above)
- `voice.includeProprietaryLibraries=true` (used by upstream's signing flavor system we don't have)

**Watch for in build:** if you ever opt into Gradle project isolation later, do it as a separate commit so the failure (if any) is bisectable.

### `gradle/libs.versions.toml` — 3 conflict zones + 1 manual override

1. **Versions section**: combined.
   - Bumped: `metro = "0.10.2"` → `"0.13.2"` (3-minor jump, watch for API breaks)
   - Bumped: `navigation3 = "1.0.0"` → `"1.1.1"`
   - Kept: `aboutlibraries = "11.2.3"` (upstream removed this key but other parts of the file still reference it)
2. **Libraries section**: bumped serialization-json `1.10.0` → `1.11.0`. Skipped 4 firebase libs (`firebase-bom`, `firebase-crashlytics`, `firebase-analytics`, `firebase-remoteconfig`).
3. **Plugins section**: skipped `crashlytics` and `googleServices` plugin entries (Firebase ecosystem).
4. **Manual override (post-merge edit)**: `jvm-toolchain = "25"` → `"21"` to match VoicePlus's CI JDK.

**Auto-merged from upstream — flag for awareness:**
- `jvm-bytecode = "17"` (VoicePlus previously hardcoded JVM 11 in `baseSetup.kt`)
- `jvm-toolchain = "25"` was the auto-merged value before our manual override to 21
- Kotlin 2.3.0 → 2.3.21
- Media3 1.9.1 → 1.10.0
- AGP 9.0.0 → 9.2.0
- Various library version bumps (lifecycle, compose-bom, ktest, paging, etc.)

### `plugins/build.gradle.kts` — took upstream (catalog reference)
Replaced hardcoded `JavaLanguageVersion.of(21)` with `JavaLanguageVersion.of(libs.versions.jvm.toolchain.get().toInt())`. Same effective value (21 via catalog) but using the modern catalog pattern.

### `plugins/src/main/kotlin/baseSetup.kt` — took upstream (catalog reference)
Same pattern as plugins/build.gradle.kts: catalog reference instead of hardcoded `21`.

### `core/featureflag/build.gradle.kts` — kept ours (empty deps)
Rejected upstream's added deps (`core.data.api`, `core.remoteconfig.api`, `datastore`, `serialization.json`).
- `remoteconfig.api` doesn't exist in VoicePlus.
- The other deps aren't needed by VoicePlus's simplified `FeatureFlagFactory`.

**Watch for in build:** if `FeatureFlagFactory.kt` resolution (next phase) accepts upstream's behavior, we'll need to add `datastore` + `serialization.json` here.

### `core/playback/build.gradle.kts` — combined
- Added: `implementation(projects.core.featureflag)` (from upstream — needed for new `LivePlaybackState` and other feature-flag-gated playback code)
- Kept: `implementation(projects.core.sleepTimer.api)` with **capital T** (VoicePlus directory naming; upstream's lowercase `sleeptimer` doesn't match).

### `features/settings/build.gradle.kts` — kept ours, then corrected post-build
Initial decision: kept both `libs.material` and `libs.aboutlibraries.compose.m3`.
**Build failed** on first compile attempt: `Unresolved reference 'material'` — the auto-merged `gradle/libs.versions.toml` no longer defines `material` (upstream commit #3480 removed Material 2 entirely). Verified `features/settings/` has zero `com.google.android.material` imports → dep was unused. Removed `implementation(libs.material)` line. Kept `aboutlibraries.compose.m3` (used by `OpenSourceLicensesScreen.kt`).

---

## Phase 5 — Group C real code conflicts (13 files)

### Data layer (3 files)

**`core/data/api/src/main/kotlin/voice/core/data/store/StoreQualifiers.kt`** — kept VoicePlus's qualifiers (`NotStartedExpandedStore`, `FinishedExpandedStore`, `MediaButtonDoubleClickHandlerStore`, `MediaButtonTripleClickHandlerStore`); rejected upstream's `DeveloperMenuUnlockedStore` and `FeatureFlagOverridesStore` (dev-menu).

**`core/data/impl/src/main/kotlin/voice/core/data/folders/AudiobookFoldersImpl.kt`** — accepted `persistedUriPermissions: PersistedUriPermissions` (needed by auto-merged "hide unpersisted folders" code); rejected `analytics: Analytics` (telemetry).

**`core/data/impl/src/main/kotlin/voice/core/data/store/StoreModule.kt`** — kept `SetSerializer` import; rejected `MapSerializer` import + `FeatureFlagOverride` import + entire `featureFlagOverrides()` provider (dev-menu).

### bookOverview (4 files)

**`features/bookOverview/.../deleteBook/DeleteBookViewModel.kt`** — combined imports (`Inject` + `SingleIn` both needed for class annotations).

**`features/bookOverview/.../overview/BookOverviewViewModel.kt`** — combined: kept VoicePlus's `NotStartedExpandedStore`/`FinishedExpandedStore` params + accepted upstream's `ExperimentalPlaybackPersistenceQualifier` param.

**`features/bookOverview/.../views/GridBooks.kt`** and **`ListBooks.kt`** — combined function signatures: used upstream's `Map<BookOverviewCategory, Map<BookId, State<BookOverviewItemViewState>>>` shape (required by auto-merged ViewModel); kept your `categoryExpanded` + `onCategoryToggle` callbacks. Combined items rendering: wrapped upstream's `State`-unwrapping items block in your `if (expanded) {}` collapsible-section logic. **Fixed apparent upstream bug**: changed `items = books.toList()` to `items = sectionBooks.toList()` (upstream was iterating all sections in each section's loop).

### Playback (2 files)

**`core/featureflag/src/main/kotlin/voice/core/featureflag/FeatureFlagFactory.kt`** — kept VoicePlus's simplified factory (no remote config, no override storage). **Behavior change to support new interface**: factory now returns `MemoryFeatureFlag` instances (in-memory only, defaults always returned) instead of plain anonymous `object : FeatureFlag<…>`. Required because the auto-merged `FeatureFlag` interface has more methods (`flow`, `type`, `overwrite`, etc.) than VoicePlus's old anonymous-object impls satisfied. `MemoryFeatureFlag` is auto-merged into production from upstream and provides all interface methods. Phase 2 will replace the `MemoryFeatureFlag` for the experimental playback flag with a DataStore-backed impl.

**`core/playback/src/main/kotlin/voice/core/playback/playstate/PositionUpdater.kt`** (Option B) — took upstream's structure: `var player: Player?` + `updateJob: Job?` (cleaner than `lateinit`), removed `lastWrittenPosition` redundancy guard (already covered by `flushPositionNow()`'s `takeIf { it >= 0 }`). Used the experimental flag for delay choice but **changed the "flag off" default from `400.milliseconds` (upstream) to `1.seconds`** to preserve VoicePlus's v1.18 battery improvement. Imports: dropped `milliseconds`, added `seconds`. Kept `minutes` (used for the flag-on path).

### PlaybackScreen (2 files — high judgment)

**`features/playbackScreen/src/main/kotlin/voice/features/playbackScreen/BookPlayViewModel.kt`** (7 conflict zones) — combined; verified "no auto-pause when navigating to non-current book" behavior is preserved.
- Took upstream's `persistedBook` rename + `remember(bookId)` key.
- **Rejected upstream's `init { player.pauseIfCurrentBookDifferentFrom(bookId); currentBookStoreId.updateData { bookId } }`** (would force-switch the current book on navigation — the exact behavior VoicePlus avoids).
- Combined `viewState()` body to compute both upstream's `book = persistedBook.overlay(livePlaybackState)` + `isPlaying` AND VoicePlus's `currentStoreBookId` + `playbackControlsEnabled` + `characterCount`.
- `playing = isPlaying && playbackControlsEnabled` (combined).
- `playedTime = positionInCurrentMark.milliseconds` (upstream's clamp), `playbackControlsEnabled = playbackControlsEnabled` (yours).
- For `onChapterClick`, `seekTo`, `toggleSkipSilence`: kept VoicePlus's explicit `if (currentBookStoreId.data.first() != bookId) return@launch` guards rather than upstream's `currentBook()` helper. Consistent with the `playbackControlsEnabled` design.

**`features/playbackScreen/src/test/kotlin/voice/features/playbackScreen/BookPlayViewModelTest.kt`** — combined imports (test uses `BookCharacterRepo`, `MemoryFeatureFlag`, `CurrentBookResolver`, `LivePlaybackState`); took upstream's `playStateManager = playStateManager` (uses shared mock var).
- **Fixed an auto-merge bug**: removed the duplicate `private val currentBookStoreId = MemoryDataStore<BookId?>(null)` declaration (would have caused a duplicate-val compile error). Kept `MemoryDataStore<BookId?>(book.id)` so `playbackControlsEnabled` is true in existing tests.

### Settings (2 files)

**`features/settings/src/main/kotlin/voice/features/settings/SettingsViewModel.kt`** — kept yours (the conflict marker straddled VoicePlus's `setAutoSleepTimerDuration` body and upstream's analytics-consent / dev-menu-unlock code). Rejected upstream additions referenced fields VoicePlus doesn't have (`analyticsConsentStore`, `developerMenuUnlockedStore`, `appVersionTapCount`, `SettingsViewEffect.DeveloperMenuUnlocked`).

**`features/settings/src/test/kotlin/voice/features/settings/SettingsViewModelTest.kt`** — kept yours (rejected upstream's dev-menu test scaffolding).
- **Fixed auto-merge gap**: added missing `mediaButtonDoubleClickHandlerStore` and `mediaButtonTripleClickHandlerStore` to the test's `SettingsViewModel(...)` constructor call. The production `SettingsViewModel` requires these (VoicePlus media-button feature) but the auto-merged test instantiation didn't pass them — would have failed to compile. Added test-side `MemoryDataStore` instances initialised to `MediaButtonClickAction.SKIP_FORWARD` and `SKIP_BACKWARD` (matching production defaults).

---

## Auto-merged changes worth flagging

Git's 3-way merge auto-resolved 80+ files where both sides agreed, or one side modified and the other didn't. These were not reviewed individually. Notable ones:

- **JVM bytecode bumped 11 → 17** (`baseSetup.kt` line 21 used to be `JvmTarget.JVM_11`, now reads from `jvm-bytecode = "17"` catalog key).
- **`core/sleepTimer/impl/build.gradle.kts`** modified by upstream — auto-merge applied. Capital-T directory preserved.
- **Multiple translation files** (`core/strings/src/main/res/values-*/strings.xml`) auto-merged from upstream — new strings added.
- **Drawable removals** auto-merged: `core/playback/src/main/res/drawable/ic_bedtime.xml`, `ic_bedtime_off.xml`.
- **`core/ui/src/main/kotlin/voice/core/ui/ViewModel.kt`** auto-deleted (upstream removed; refactored elsewhere).

---

## Things to verify in build

1. `app/build.gradle.kts` lint config — does VoicePlus's `lint.xml` still apply via the convention plugin?
2. Metro 0.13.2 — any compile errors from the 3-minor-version bump?
3. `core/featureflag` — empty deps; does `FeatureFlagFactory.kt` resolution still work?
4. `features/settings` material + aboutlibraries deps — still referenced by auto-merged code?
5. `gradle-wrapper.jar` — was binary in patch but git's merge handled it via blob hash; verify the wrapper still works (`./gradlew --version`).
6. JVM bytecode 17 — APK still installs/runs on min SDK 28 (Android 9).

---

## Operations summary (for reference)

```text
git fetch upstream                                                  # downloaded ~128MB upstream history
git config remote.upstream.tagOpt --no-tags                         # future fetches skip tags
git replace --graft ae391bb7a17508c9e630c47e283c9e37d2cc1dae \      # local-only graft connecting
                    f2374fbf85580ec245436166a5d7e0a122809736        # our root → v26.2.4
git merge upstream/main                                             # standard 3-way merge using v26.2.4 as base
# (resolution work — this log)
# git commit                                                          # not yet done
# git replace -d ae391bb7a17508c9e630c47e283c9e37d2cc1dae             # not yet done
```
