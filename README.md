# VoicePlus

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![F-Droid](https://img.shields.io/f-droid/v/com.github.mistermo_vibecode.voiceplus.svg?logo=f-droid)](https://f-droid.org/packages/com.github.mistermo_vibecode.voiceplus/)

**A privacy-focused Android audiobook player with enhanced listening tools.**

> VoicePlus is a fork of the excellent [Voice](https://github.com/PaulWoitaschek/Voice) audiobook player by Paul Woitaschek. Full credit and thanks to Paul and all Voice contributors — see [CREDITS.md](CREDITS.md).

---

## Download

<!-- F-Droid badge and link — fill in once listed -->
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](#)

_F-Droid listing pending. Check the [Releases](https://github.com/Mistermo/VoicePlus/releases) page for direct APK downloads._

---

## What VoicePlus adds

VoicePlus builds on Voice's clean, minimalist foundation and adds:

### Listening Log and Statistics
The app records every listening session automatically — when you started, stopped, and for how long. A statistics screen aggregates this data into charts showing your daily and weekly listening trends.

### Character Lists
Track the cast of characters for every book individually. A dedicated per-book screen lets you add characters with descriptions and reorder them by importance using drag-and-drop.

### Enhanced Home Screen Widgets
The Android widget has been updated with skip-distance labels and a progress bar that shows your position relative to the entire book, not just the current chapter.

### Customizable Media Button Actions
Map double-click and triple-click headset button presses to specific actions: skip forward or backward by a configurable interval, jump to the next/previous chapter, or jump to a bookmark. Works with wired and Bluetooth controllers.

### Intelligent Sleep Timer
The sleep timer resets to its full duration whenever you pause, resume, or adjust volume — keeping it active during intentional breaks. "End of chapter" mode supports an N-chapter countdown displayed as a live badge.

### Library Management
- Hide books without deleting them; restore from a hidden books screen at any time.
- Library sections remember their expanded/collapsed state across restarts.
- Browse and manage your library without interrupting playback.

### Chapter Name Fix
Fixes a bug in the upstream metadata parser where chapter titles were replaced by the album title when certain ID3 tags collide.

### Full Telemetry Removal
All Firebase modules (Analytics, Crashlytics, Remote Config) have been removed. The app makes zero outbound network calls.

---

## Core Voice features retained

- Clean, distraction-free interface built with Jetpack Compose and Material 3
- Plays local audiobook folders (MP3, M4B, M4A, FLAC, OGG, and more)
- Sleep timer, bookmarks, playback speed control, skip-silence
- Covers loaded from embedded tags or folder images
- No accounts, no cloud, no ads

---

## Build from source

<!-- MP-3 will fill in exact JDK/SDK/NDK versions and any special flags needed -->

**Prerequisites:**
- JDK 17 (Android Studio Hedgehog or newer ships a suitable JBR)
- Android SDK with build-tools matching `gradle/libs.versions.toml`

```bash
git clone https://github.com/Mistermo/VoicePlus.git
cd VoicePlus
./gradlew :app:assembleLibreRelease
```

Output APK: `app/build/outputs/apk/libre/release/app-libre-release-unsigned.apk`

> Note: F-Droid performs its own signing. You do not need a keystore to build from source.

Full build instructions (exact SDK versions, signing setup): _see `docs/development.md`_ <!-- placeholder until MP-3 lands -->

---

## Contributing

Issues and pull requests are welcome. Please open an Issue first for significant changes.

- Bug reports: use the [Bug Report template](.github/ISSUE_TEMPLATE/bug.yml)
- Code style: enforced by ktlint — run `./gradlew lintKotlin` before opening a PR

---

## License

VoicePlus is free software, licensed under the [GNU General Public License v3.0](LICENSE.md).

Copyright for the upstream Voice codebase belongs to Paul Woitaschek and the Voice contributors.
Modifications in VoicePlus are copyright Mistermo.

---

## Acknowledgements

This project would not exist without the work of Paul Woitaschek and all contributors to [Voice](https://github.com/PaulWoitaschek/Voice). See [CREDITS.md](CREDITS.md) for the full acknowledgement.
