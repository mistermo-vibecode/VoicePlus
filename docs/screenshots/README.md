# Screenshot refresh — v1.28

Approved dark-mode set. These are real app captures with fictional demo data, not mock UI.
The screenshot build includes the current v1.28 working-tree changes; its version
label remains `1.27-debug` until release preparation updates it.

## Capture set

- Library, including completed-book badges
- Playback
- Listening log
- Bookmarks
- Listening statistics
- Character list
- Sleep timer
- Playback toolbar customization
- Playback, media-button and lock-screen settings

Nine phone masters live in `phone/`. Five captures each live in `tablet-7/` and
`tablet-10/`. Keep plain captures for F-Droid and export framed derivatives for
the README. Do not stretch phone captures to produce tablet images.

All captures use dark mode, 09:41, hidden notification/network icons and a full
battery. The app renders at each display size before capture:

| Set | Pixels | Emulator density |
| --- | --- | --- |
| Phone | 1080 × 2400 | 420 dpi |
| 7-inch class | 800 × 1280 | 213 dpi |
| 10-inch class | 1600 × 2560 | 320 dpi |

These use display/density overrides on the disposable Pixel AVD, not separate
tablet hardware profiles. Phone display defaults are restored after the run.
The root README uses nine framed PNGs, while Fastlane contains the plain captures.
The local `preview.html` includes a "Make it yours" section with toolbar, settings
and sleep-timer captures. These two additional customization shots are intended
for the F-Droid phone set as well as the README presentation.

## Export

Run `python3 docs/screenshots/export.py` from the repository root with
`agent-browser` and its Chromium installed. The exporter uses a private browser
session, renders the approved HTML/CSS frames at 2× scale, then copies the plain
masters into Fastlane. All framed PNGs are 698 × 1544 for aligned README rows.
No generative editing is applied to the screenshots. The browser-export workflow
preserves the approved frame styling and leaves raw captures unchanged.

The README links each framed card to its full-resolution plain capture. No
JavaScript or custom CSS is required on GitHub. No tag, release, push or external
publication is performed by the exporter.

## Demo content

Six classic titles use covers supplied by [Standard Ebooks](https://standardebooks.org/about),
which dedicates its work to the public domain. The source editions are:

- [Alice’s Adventures in Wonderland](https://standardebooks.org/ebooks/lewis-carroll/alices-adventures-in-wonderland/john-tenniel)
- [Pride and Prejudice](https://standardebooks.org/ebooks/jane-austen/pride-and-prejudice)
- [Treasure Island](https://standardebooks.org/ebooks/robert-louis-stevenson/treasure-island)
- [The Secret Garden](https://standardebooks.org/ebooks/frances-hodgson-burnett/the-secret-garden)
- [The Adventures of Sherlock Holmes](https://standardebooks.org/ebooks/arthur-conan-doyle/the-adventures-of-sherlock-holmes)
- [The Time Machine](https://standardebooks.org/ebooks/h-g-wells/the-time-machine)

The audio is a locally generated silent fixture, not a recording from those
editions. Bookmarks, character descriptions and listening activity are synthetic.
No phone database or personal history was copied.
Alice uses the edition's original `images/cover.source.jpg` artwork and the demo
title "Alice in Wonderland" to avoid cropped lettering and a scrolling title.

## F-Droid replacement

The existing phone directory includes four legacy `N_en-US.png` names in addition
to the eight feature-named screenshots. Keep those legacy slots populated with
fresh images: an earlier repository change documents that deletion did not remove
orphaned screenshots from the F-Droid index.

The live listing also references `sevenInchScreenshots/1_en-US.png` through
`4_en-US.png` and the corresponding four `tenInchScreenshots` files. Those exact
paths now contain current tablet-sized captures, with a fifth bookmarks shot
added in each size. Verify the live
listing after F-Droid refreshes; repository changes alone do not prove cleanup.

The 12 phone slots contain nine unique captures; library, playback and toolbar
are repeated only to overwrite retained legacy slots. `5_edit_book.png` now
showcases toolbar customization; `3_en-US.png` showcases bookmarks. The complete
filename mapping is in `export.py`. Existing images remain recoverable in Git.

## Local session recovery

The disposable `VoicePlusRelease127` AVD contains the demo library. It is currently
managed by Android Studio, so discover its emulator serial rather than assuming
a fixed port. Never target the connected physical phone.

- Pre-change emulator backup: `/private/tmp/voiceplus-screenshot-emulator-before.tar`
- Fictional seed generator: `/private/tmp/voiceplus-screenshot-demo/seed.py`
- Portable seeded archive: `/private/tmp/voiceplus-screenshot-demo-portable.tar`
- Build log: `/private/tmp/voiceplus-screenshots-build.log`
- Capture script: `/private/tmp/voiceplus-capture-set.py`

When transferring archives to Android, use `COPYFILE_DISABLE=1 tar --format=ustar`.
The default macOS extended-header archive produced empty files with Android's tar;
the portable transfer was checked and corrected before captures.
