# VoicePlus

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![F-Droid](https://img.shields.io/f-droid/v/com.github.mistermo_vibecode.voiceplus.svg?logo=f-droid)](https://f-droid.org/packages/com.github.mistermo_vibecode.voiceplus/)
[![Downloads](https://img.shields.io/github/downloads/Mistermo-vibecode/VoicePlus/total.svg)](https://github.com/Mistermo-vibecode/VoicePlus/releases)
[![CI](https://github.com/Mistermo-vibecode/VoicePlus/actions/workflows/ci.yml/badge.svg)](https://github.com/Mistermo-vibecode/VoicePlus/actions/workflows/ci.yml)

A fork of [Voice](https://github.com/PaulWoitaschek/Voice) by Paul Woitaschek — a genuinely great audiobook app that I enjoyed but wanted something a bit different for myself. 

This started as a personal learning project by someone who had no idea what they were doing (and still isn't entirely sure). If you find it useful, great. Updates may happen. No promises.

---

## Why download this instead of Voice?

Honestly? You probably shouldn't. Voice is polished, actively maintained, and built by someone who knows what they're doing. Download that first.

But you can try both. If the features below add value to your listining experience then great. If not then no worries.

---

## What's different from Voice

- Listening log and statistics
- Character lists per book
- Resizable widget with configurable opacity and text scale
- Customizable media button actions (double/triple press)
- Sleep timer auto-reset on interaction, N-chapter countdown
- Hide/restore books from library
- Firebase removed entirely — zero outbound network calls

---

## Download

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="60">](https://f-droid.org/packages/com.github.mistermo_vibecode.voiceplus/)

Or grab and sideload the APK from the [Releases](https://github.com/Mistermo-vibecode/VoicePlus/releases) page.

---

## Build from source

Requires JDK 21 and Android SDK. See `gradle/libs.versions.toml` for exact versions.

```bash
git clone https://github.com/Mistermo-vibecode/VoicePlus.git
cd VoicePlus
./gradlew :app:assembleLibreRelease
```

---

## License

GPL v3 — see [LICENSE.md](LICENSE.md).
Upstream Voice copyright Paul Woitaschek and contributors. VoicePlus modifications copyright Mistermo.
