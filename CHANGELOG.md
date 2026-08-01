# VoicePlus Changelog

## v1.23 — Backup & Restore, Reliable Listening Log

### Backup & Restore
- **Back up to a folder you choose:** Pick any folder (SD card, cloud-synced, USB drive) and VoicePlus keeps timestamped backup files there — positions, bookmarks, listening history and stats, character lists, chapter-name fixes, hidden books, and your settings. Choosing a folder is all it takes: the first backup is written immediately.
- **Automatic and manual saves:** An automatic save is written about once a day (the newest 7 are kept); "Back up now" creates a manual save point that is never cleaned up.
- **Restore from a list:** Every save in the folder is listed with its date — restore the one you want, or delete saves you no longer need. Backups are never overwritten, so a failed write can't damage an existing good save.
- **Survives reinstall:** After an uninstall or a new phone, re-grant your audiobook folders and restore — books are matched back to their files, with anything unmatchable clearly listed instead of guessed.
- **Safety rails:** Every backup is verified after writing; unreadable files are skipped at restore time; backups from a newer app version are refused rather than half-read.

### Listening Log Reliability
- **Sessions survive being killed:** The in-flight session is checkpointed every 30 seconds, so a crash, force-stop, or reboot mid-listen now records the session (with an "Interrupted" badge) instead of losing it entirely.
- **Fixed listening not being recorded after swiping the app away** — the playback service could end up in a state where nothing was logged until the next reboot.
- **Fixed stuttering playback fragmenting sessions** into pieces small enough to be discarded.
- **Fixed sleep-timer stops rewinding twice** and leaving a stray "position set" entry in the log; a sleep stop that never played also no longer mislabels the next session.
- **Switching books mid-play** now ends the previous book's session correctly instead of billing the time to the new book.
- **Chapter and bookmark jumps are labeled:** picking a chapter or opening a bookmark now shows as "Went to chapter" with the chapter name, instead of an anonymous "Jumped" entry — so the log no longer shows seeks you don't remember making.
- **"Resumed after sleep" marker:** a session started within an hour of a sleep-timer stop is chipped in the log, so in the morning you can tell where the timer stopped you (the Sleep entry) from listening that happened while dozing — and tap either to jump back.
- Listening history, stats, and end-reason badges are now included in backups and survive restore without duplicating.

### Bookmarks
- Fixed a crash when a bookmark pointed at a chapter that no longer exists; selecting such a bookmark now reports it as unavailable instead of seeking into nothing.
- Deleting a bookmark now offers Undo.
- Named bookmarks show their chapter and position underneath, so they stay locatable.
- Blank bookmark names are rejected, and the media-button quick bookmark confirms with a toast.

---

## v1.22 — Chapter Name Editor

### Chapter Name Editor
- **One-step number fix:** New "Edit chapter names" entry in the playback screen's overflow menu. A single offset control shifts every chapter number at once, for when an intro or prologue throws the count off by one, or a conversion tool renumbered the chapters.
- **Digits and words:** Recognizes both numerals ("Chapter 5") and English number words ("Chapter Five"), from one to one hundred.
- **Per-chapter overrides:** Rename any individual chapter by hand. Manual names take precedence over the offset.
- **Non-destructive and persistent:** Corrections are stored separately from your files, survive library re-scans, and never modify the audio. Reset everything or clear a single override at any time.
- **Applied everywhere:** Corrected names appear across the playback screen, bookmarks, the listening log, the home-screen widget, and Android Auto.
- Available for books that have chapter marks.

### Use Folder Names Instead of File Tags (Experimental)
- Toggling this option now re-scans your library and updates existing books immediately, instead of only applying to newly added books.
- Tidied up the settings row layout and confirmation dialog wording.

---

## v1.21 — Bug fixes

- **Open Source Licenses screen:** Fixed crash that prevented the screen from opening on release builds.
- **Sleep Timer skip-reset:** Skipping forward or backward now resets the sleep timer when auto-reset is enabled, matching the existing behavior for volume changes and resuming from pause.

---

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
