# Earmark (Android)

Functional core, imperative shell. `:core` is pure Kotlin and owns every product decision behind `Engine.handle(event, now): List<Effect>`. `:app` is adapters that turn Android callbacks into `Event`s and carry out `Effect`s. Use the terms in `GLOSSARY.md` for code, tests and UI copy; `docs/adr/` records why things are the way they are.

## Build and test

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21        # JDK 21 is not on PATH
./gradlew :core:test                                   # fast loop: all product logic lives here
./gradlew :core:test :app:assembleRelease lint         # ship gate: tests, minified APK, lint 0 errors
```

The release APK lands in `app/build/outputs/apk/release/`. Copy it to `dist/earmark-v0.1.apk`, which is committed so testers can download it from GitHub, then check `aapt2 dump permissions` shows no `INTERNET`.

## Where changes go

- Behaviour (classifying, scoring, drift, closing, chip and pill copy) goes in `:core`, test-first. Scenario tests in `ScenarioTest.kt` replay the four demo stories; their copy is the spec. Add a `Script` test before changing any rule.
- An Android call that can throw is wrapped in its adapter, logged to `DebugLog`, then falls back or skips. The engine only ever sees events.
- `DebugLog` lines carry ids, scores and reasons, never message text.

## Gotchas found building v0.1

- **Release-only crashes:** R8 strips ML Kit's reflectively loaded registrars. The keep rules are in `app/proguard-rules.pro`. Always smoke-test the release APK, because a debug build hides this.
- **Unlock:** the Android 16 emulator doesn't deliver `USER_PRESENT` to runtime receivers. `SystemReceivers` watches the keyguard after `SCREEN_ON` instead (ADR 0013). On the emulator, run `adb shell locksettings set-disabled false` so there is a lock screen to dismiss.
- **compileSdk 37, targetSdk 36:** Compose 1.12 requires compileSdk 37; the test emulator is API 36.
- **Slow downloads here:** single connections to GitHub and dl.google.com crawl, so fetch large files with parallel `curl -r` ranges.
- **Overlays:** uiautomator can't see them, so tap by coordinates. The chip ✓ sits at about (960, 379) and the pill button at about (893, 376) on the 1080×2400 emulator. Injected touches bypass TalkBack, so check accessibility through the node tree (`uiautomator dump`).
- **Shell quoting:** the YouTube launcher activity has a `$` in its name: `adb shell "am start -n 'com.google.android.youtube/.app.honeycomb.Shell\$HomeActivity'"`.
- **Device setup:** `scripts/setup-phone.sh` installs the APK and grants every permission adb can grant, on a phone or the emulator.
- **Some project-folder files are iCloud placeholders** (`ls -lO` shows `dataless`), and reading one hangs.
