# Testing VoicePlus

Use the smallest lane that matches the change. Emulator and uninstall/restore checks are not part of the normal edit loop.

## During development

Run the changed module, for example:

```bash
./gradlew :core:playback:testDebugUnitTest
./gradlew :core:data:impl:testDebugUnitTest
./gradlew :core:scanner:testDebugUnitTest
```

Before pushing a change that can affect a core user journey, run:

```bash
./gradlew criticalTest
```

`criticalTest` covers playback, the playback screen, persistence and backup compatibility, scanning, and the sleep timer. Its target is under two minutes from a clean build and under one minute when warm.

## Pull requests

CI runs these jobs in parallel:

- JVM tests, Kotlin lint, and a debug APK build.
- Android lint.
- Eight managed-device smoke journeys on API 33: app launch; library scroll restoration in list/grid, Settings, and activity recreation; play, seek, pause, and Previous metadata; timed sleep; end-of-chapter sleep.

The instrumentation command intentionally runs only `AppLaunchSmokeTest` and `SleepTimerIntegrationTest` to keep this lane short. A new critical instrumentation class is not covered until its class name is added to both `.github/workflows/ci.yml` and the command below.

To reproduce the device lane locally:

```bash
./gradlew :app:voiceDeviceLibreDebugAndroidTest \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  -Pandroid.testInstrumentationRunnerArguments.class=voice.app.AppLaunchSmokeTest,voice.app.SleepTimerIntegrationTest
```

The first run may download an Android system image. Warm runs should complete in roughly one minute.

## Persistence compatibility rule

Files under `core/data/impl/src/test/resources/backups/` are immutable released-format inputs. Tests must read these bytes directly; they must not recreate an old backup with the current serializer.

Any change to a persisted DTO, Room schema, backup envelope, checksum, or restored setting must include:

1. A fixture representing the previous readable format.
2. Assertions for the user data affected by the change.
3. A mutation check showing the test fails when the compatibility behavior is removed.

Fixtures must contain synthetic or anonymised data only.

## Nightly and release checks

Nightly CI repeats the full JVM/build lane and runs the critical Android journeys on API 28 and API 35. These slower checks do not block the local edit loop.

Before publishing a release candidate:

1. Install it over a populated previous release and verify books, progress, settings, statistics, covers, and bookmarks.
2. Restore a previous-format external backup into an empty debug installation.
3. Run `scripts/test-backup-restore.sh` on a dedicated test device or emulator. This intentionally uninstalls the selected package.
4. On a physical device, play, pause, seek, use Previous and Next from the app and lockscreen, and verify chapter names and progress.
5. Leave the release as a draft until the physical-device check passes.

## Regression rule

For every reported bug: reproduce it, add a test that fails for the same reason, demonstrate the failure, restore/fix production code, then run `criticalTest`. A test that still passes with the bug deliberately reintroduced does not protect the behavior.
