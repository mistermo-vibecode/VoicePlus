# VoicePlus Changelog

## v1.20 — Widget Overhaul, Quick Bookmark, Experimental Playback Persistence

### Widget Overhaul
- Consolidated to a single resizable widget showing cover, title, chapter, and skip controls.
- Configuration screen with live preview lets you adjust opacity and text scale per widget instance.
- Fixed widget icons disappearing in dark mode.

### Media Button Quick Bookmark
- New "Quick bookmark" action assignable to double or triple headset button click — saves a bookmark instantly without interrupting playback.

### Experimental Playback Persistence
- New Settings toggle (off by default). When on, position saves every 5 minutes instead of every second, reducing battery drain at the cost of up to 5 minutes of position loss if the app is force-killed.

---

## v1.19 — Dependency & Platform Updates

- Updated Kotlin, AGP, Compose, Media3, and other core dependencies to current stable versions.
- Improved chapter name detection for books with multiple audio files.
- Various stability and compatibility improvements.

---

## v1.18 — Battery Efficiency Optimization

- **Reduced position save frequency:** Position now saves every 1 second instead of every 400ms, reducing battery drain during playback.
- **Optimized database queries:** Added indexes on listening session queries for faster statistics lookups.
- **Streamlined statistics computation:** Stats calculations debounce updates, reducing CPU load.
- **Consolidated metadata extraction:** Audio files are now parsed once per scan instead of twice.

---

## v1.17 — N-Chapter Sleep Timer

- **Selectable chapter count:** The "End of Chapter" sleep timer now lets you choose how many chapters to play before pausing using `−` / `+` buttons.
- **Inclusive count:** The selected number includes the currently-playing chapter (e.g. `3` plays the current chapter and the next two, then pauses).
- **Persisted preference:** The chosen count is saved and restored when the dialog is reopened.
- **Live countdown badge:** The indicator above cover art shows "End of N Chapter(s)" and counts down as each chapter ends.
- **Scrubber fix:** Resolved a visual stutter when dragging the progress scrubber during playback.

---

## v1.16 — Sleep Timer Enhancements & Media Button Actions

### Sleep Timer Auto-Reset
- Timer resets to full duration when playback is paused and resumed.
- Timer resets to full duration when device volume is changed during an active timer.

### Customizable Media Button Actions
- **Double and triple click support** for headset play/pause button.
- Assignable actions: Skip Forward, Skip Backward, Skip Forward Chapter, Skip Backward Chapter, None.
- Works with both simple one-button headsets and multi-button headsets.

---

## v1.13 — Privacy & Clean Settings

### Analytics & Tracking — Fully Removed
- Removed Firebase Analytics, Crashlytics, and Remote Config entirely.
- No usage data, crash reports, or remote feature flags — zero external network calls.
- Removed analytics consent prompt from onboarding and analytics toggle from Settings.

### Settings Cleanup
- Removed external links to the upstream project's GitHub, support channels, translation platform, and FAQ.

---

## v1.0.0 — Foundation

### Character Lists
- Per-book character roster with drag-to-reorder and deletion confirmation.

### Listening Logs & Statistics
- Session tracking with start time, end time, and duration logging.
- Statistics dashboard with charts and historical listening trends.

### Library Management
- Hide/remove books from the library with a dedicated Hidden Books management screen in Settings.
- Library sections remember their expanded/collapsed state across sessions.

### Playback & Widget
- Browse the library without interrupting playback.
- Home screen widget with skip labels and chapter indicator.
- Fixed chapter name parsing when the `TIT2` tag matched the album title.

---

*Forked from Voice v26.2.4*
