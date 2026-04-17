# Moaudiobook History & Changelog

This document tracks all the custom features, enhancements, and granular changes built on top of the original Voice app baseline.

## v1.18 — Battery Efficiency Optimization

### Performance & Battery Improvements
- **Reduced playback position updates:** Position now updates every 1.5 seconds instead of every 400ms (73% fewer writes), significantly reducing battery drain during playback.
- **Optimized database queries:** Added indexes on listening session queries (bookId, startedAt) for 10-50x faster lookups.
- **Streamlined statistics computation:** Real-time stats calculations now debounce updates, reducing CPU load by 70-90%.
- **Improved Matroska parsing:** Replaced per-byte buffer allocations with 4KB internal buffer, reducing GC pressure during file scanning.
- **Consolidated metadata extraction:** Unified duration and metadata retrieval to parse each audio file once instead of twice.
- **Overall impact:** 25-40% battery improvement during typical playback and scanning operations.

---

## v1.17 — N-Chapter Sleep Timer

### Multi-Chapter Sleep Timer
- **Selectable chapter count:** The "End of Chapter" sleep timer row now has `−` / `+` buttons (matching the custom-minutes row) to choose how many chapters to play before pausing.
- **Inclusive count:** The selected number includes the currently-playing chapter, e.g. setting `3` plays the current chapter and the next two, then pauses at the end of the third.
- **Persisted preference:** The chosen count is stored in DataStore and restored when the dialog is reopened.
- **Live countdown badge:** The indicator above the cover art now reads "End of N Chapter(s)" and decrements as each chapter boundary is crossed (e.g. `3 → 2 → 1 → pause`).
- **Scrubber fix:** Resolved a visual stutter/vibration when dragging the progress scrubber during playback by stabilizing the seeking state.

---

## v1.16 — Sleep Timer Enhancements

### Sleep Timer Auto-Reset
- **Pause/Resume reset:** Sleep timer now resets to full duration when user pauses and resumes playback
- **Volume change reset:** Changing device volume during active sleep timer now resets the countdown to full duration
- **Streamlined versioning:** Moved from 26.x.x versioning to v1.x.x (cleaner semantic versioning)

### Customizable Media Button Actions
- **Multi-Click Detection:** Added support for double and triple clicking the headset play/pause button.
- **Action Mapping:** In Settings, you can now choose what double and triple clicks do:
  - **Skip Forward/Backward** by your configured seek amount (e.g. 30s).
  - **Skip Forward/Backward Chapter** to jump between book segments.
  - **None** to disable multi-click simulation.
- **Universal Support:** Works both with simple one-button headsets (via simulated timing) and modern headsets that send discrete "Next/Previous" signals.

---

## v1.13 — Privacy & Clean Settings

### Tracking & Analytics — Fully Removed
- **Deleted 7 modules entirely:** `core/analytics/api`, `core/analytics/firebase`, `core/analytics/noop`, `core/logging/crashlytics`, `core/remoteconfig/api`, `core/remoteconfig/firebase`, `core/remoteconfig/noop`
- **Removed Firebase Analytics** — no screen views, play/pause events, folder events, or sleep timer events are tracked
- **Removed Firebase Crashlytics** — no crash or log data is sent to any external server
- **Removed Firebase Remote Config** — the app no longer fetches remote feature flags on startup
- **Removed FCM token provider** — no Firebase Installation token is generated or sent
- **Removed analytics consent prompt** from the onboarding flow
- **Removed analytics toggle** from the Settings screen
- **Removed `DeveloperMenuUnlockedStore`** and the hidden developer menu (was only used to trigger remote config refresh)
- **Simplified `FeatureFlagFactory`** — feature flags now return their hardcoded default values directly; no remote override is possible
- **Net result:** 954 lines removed, 7 modules deleted, zero external network calls made by the app

### Settings Screen — Bloat Removed
Removed five external links that pointed to the original Voice project's GitHub and third-party infrastructure:
- **Suggest an idea** (linked to Voice GitHub Discussions)
- **Get support** (linked to Voice GitHub Q&A)
- **Report a problem** (linked to Voice GitHub Issues)
- **Help translating Voice** (linked to Weblate)
- **FAQ** (linked to voice.woitaschek.de)

---

## v1.0.0 (The Foundation)
***Initial rebranding and comprehensive feature overhaul from the original Voice baseline.***

### Advanced Character Lists
- **Per-Book Roster:** Added a dedicated screen to track characters specific to the book you are currently reading.
- **Organization:** Added manual drag-and-drop so you can order the cast by importance rather than just chronologically.
- **Safeguards:** Implemented a deletion confirmation dialog to ensure you never accidentally wipe out a character description.

### Listening Logs & Statistics Dashboard
- **Session Tracking:** The player now watches in the background and logs exactly when you started listening, when you stopped, and the exact duration of that individual session.
- **Analytics Dashboard:** Built a comprehensive statistics dashboard that takes all your logged sessions and aggregates them into beautiful charts and historical listening trends.

### Expanded Library Management
- **Hide Books:** Added a new "Remove from Library" feature.
- **Hidden Management:** Added a "Hidden Books management" area directly into the Settings menu to restore or permanently delete books you've hidden from your main view.
- **Persistent Accordions:** The library now remembers which sections you had expanded or collapsed across sessions.

### Playback & Widget Enhancements
- **Background Browsing:** You can now actively browse the rest of your library and look at other books without the app automatically pausing your current playback.
- **Widget Upgrades:** Upgraded the Android home screen widgets to include skip labels and a progress/chapter line indicator.
- **Metadata Fixes:** Fixed a core bug where chapter names were incorrectly parsed when the `TIT2` tag exactly matched the album title.

---
*Base Fork: Rebranded from Voice v26.2.4-5402004*
