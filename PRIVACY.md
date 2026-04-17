# Privacy Policy — VoicePlus

**Effective date:** 2026-04-17

VoicePlus is an open-source Android audiobook player maintained by Mistermo. This policy describes what data the app handles, what (almost nothing) leaves your device, and what permissions the app uses and why.

---

## What data we collect

**Almost none.** VoicePlus is designed as a fully offline, local-first app. It does not have an account system, does not sync to any cloud service, and does not send usage data anywhere.

The only data the app stores is what you put into it:

- Your audiobook library (titles, authors, chapter positions, cover art)
- Listening history (when you started and stopped listening, and for how long — stored locally for the in-app Statistics screen)
- Character lists you create for individual books
- Bookmarks
- App preferences: playback speed, skip distance, sleep timer settings, media button mappings, library layout

**All of this is stored only on your device**, in a local database (`autoBookDB`) and the Android DataStore. Nothing is uploaded, synchronized, or backed up externally by this app.

---

## What leaves your device

**Only one thing:** a book title/author search query sent to DuckDuckGo when you explicitly ask the app to search for cover art online.

**Details:**

- This only happens when you open the "Search for cover online" screen and initiate a search. It never runs in the background.
- The search query is the book's title and author as you have them stored in the app — for example, `"Harry Potter by J.K. Rowling audiobook cover"`.
- The query is sent to DuckDuckGo's image search API (`https://duckduckgo.com/`).
- When you tap a result, the selected image is downloaded directly from the image host serving that result.
- DuckDuckGo does not track searches, does not build user profiles, and does not share queries with advertisers. Their privacy policy is at https://duckduckgo.com/privacy.

No device identifiers, account tokens, or persistent identifiers are attached to these requests beyond what any standard HTTPS request carries (your IP address as seen by DuckDuckGo's servers).

**Everything else stays on your device.** There is no telemetry, no crash reporting, no analytics, and no advertising network.

---

## What stays on your device (examples)

- Your entire audiobook library and file paths
- Listening session log (for the Statistics screen)
- Character lists
- Bookmarks
- All playback preferences and settings
- Cover art images you select or set manually

All of this remains on your device and is never transmitted anywhere unless you manually export it using standard Android features (e.g., backup apps).

---

## Permissions and why

| Permission | Why the app needs it |
|------------|---------------------|
| `INTERNET` | Required to fetch cover art images from DuckDuckGo when you use the online cover search feature. |
| `ACCESS_NETWORK_STATE` | Lets the app check whether a network connection is available before attempting a cover art search, so it can show you a useful error instead of silently failing. |
| `FOREGROUND_SERVICE` | Required by Android to keep the playback service running when the app is in the background. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Declares the type of the foreground service to Android (audio playback), which is required by Android 14+. |
| `WAKE_LOCK` | Prevents the CPU from sleeping while audio is actively playing, which would cause playback to stop. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Allows you to exempt VoicePlus from Android's battery optimization so playback is not interrupted during long listening sessions. You are prompted to grant this; it is not assumed. |

**Storage access** is handled through Android's Storage Access Framework (SAF). When you add an audiobook folder, Android's system file picker grants the app permission to read that specific folder. The app does not request broad storage permissions (`READ_EXTERNAL_STORAGE`) and cannot access files outside the folders you explicitly share with it.

**Advertising ID (`AD_ID`):** This permission is explicitly removed in the app's manifest. The app has no advertising network and will never request this permission.

**Location:** The app does not use, request, or have any need for your location. No location permission of any kind is declared.

---

## Third-party services

The only external service VoicePlus communicates with is **DuckDuckGo**, used exclusively for the optional cover art image search feature described above. DuckDuckGo's privacy policy is at https://duckduckgo.com/privacy.

There are no analytics SDKs, no crash-reporting services, no advertising networks, and no Google/Firebase services included in this app.

---

## No analytics, no tracking, no ads

VoicePlus contains no analytics code, no tracking SDKs, and no advertising networks. It does not transmit usage statistics, error reports, or any behavioral data to any server. The listening log and statistics features are stored entirely on your device and are not shared with anyone.

---

## Children's privacy

VoicePlus does not knowingly collect personal information from anyone, including children under the age of 13. The app has no account system, no registration, and no user-facing data collection. If you are a parent or guardian and have concerns, you can contact us using the details below.

---

## Changes to this policy

If this policy changes in a future version of VoicePlus, the updated policy will be committed to the public repository alongside the source code that motivated the change. The effective date at the top of this document will be updated. Continued use of the app after a policy update constitutes acceptance of the revised policy.

You can always view the current policy at:
https://github.com/Mistermo/VoicePlus/blob/main/PRIVACY.md

---

## Contact

If you have questions about this privacy policy, please open an issue on the public repository:
https://github.com/Mistermo/VoicePlus/issues


---

*VoicePlus is a fork of the [Voice Audiobook Player](https://github.com/PaulWoitaschek/Voice) by Paul Woitaschek, licensed under the GNU General Public License v3.0.*
