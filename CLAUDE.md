# Earmark (Android)

Functional core, imperative shell. `:core` is pure Kotlin and owns every product decision behind `Engine.handle(event, now): List<Effect>`. `:app` is adapters that turn Android callbacks into `Event`s and carry out `Effect`s. Use the terms in `GLOSSARY.md` for code, tests and UI copy; `docs/adr/` records why things are the way they are.

## Build and test

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21        # JDK 21 is not on PATH
./gradlew :core:test                                   # fast loop: all product logic lives here
./gradlew :core:test :app:assembleRelease lint         # ship gate: tests, minified APK, lint 0 errors
./gradlew :core:benchmark                              # accuracy on labelled cases; compare benchmark/results/results.json
```

The release APK lands in `app/build/outputs/apk/release/`. Copy it to `dist/earmark-v<versionName>.apk`, which is committed so testers can download it from GitHub, and `git rm` the previous one. Then check that `aapt2 dump permissions` shows no `INTERNET`, and that `apksigner verify --print-certs` shows the Sweepie Softie key. Bump `versionCode` for every release so it installs as an update, and update the APK name in README.md and `scripts/setup-phone.sh`.

## Where changes go

- Behaviour (classifying, scoring, drift, closing, chip and pill copy) goes in `:core`, test-first. Scenario tests in `ScenarioTest.kt` replay the four demo stories; their copy is the spec. Add a `Script` test before changing any rule.
- An Android call that can throw is wrapped in its adapter, logged to `DebugLog`, then falls back or skips. The engine only ever sees events.
- `DebugLog` lines carry ids, scores and reasons, never message text.

## Gotchas found building v0.1

- **Signing:** release builds use the Sweepie Softie key named in the git-ignored `keystore.properties` (the keystore is `~/.android/earmark-release.jks` on Pranav's Mac; see ADR 0016). If that file is missing, Gradle falls back to the debug key with a warning. Don't ship that APK: it can't update an installed Earmark.

- **Scoring on real phones:** a chip needs a score above 0.70, and most real messages arrive minutes before the phone is picked up. ADR 0017 tuned `due` and the unlock boost after a plain WhatsApp "hey" never showed a chip on a Galaxy A34. Check a scoring change against a message that is a few minutes old, not just 60 seconds.
- **Frozen in the background:** One UI freezes apps with no foreground service, so `KeepAliveService` keeps the process at foreground priority (ADR 0018). The emulator never freezes a bound listener; reproduce it with `adb shell am freeze --sticky com.sweepiesoftie.earmark`, and check `dumpsys activity processes com.sweepiesoftie.earmark` shows adj 200.
- **Overlays stuck on their first frame:** on the A34, with another app in front, the frames that drew an overlay skipped every frame callback, Compose's included, so the window sat at `Requested w=1080 h=42` in `adb shell dumpsys window windows` with nothing drawn. `OverlayHost.keepFramesComing()` posts a fresh frame callback from a handler every 50 ms for a second after every overlay change (ADR 0022). A self-reposting frame callback doesn't work, because it gets stuck too. The emulator never shows this. On the A34, test with Earmark's screen opened and then left, which v0.1.6 missed, not only straight after an install.
- **Foreground poll:** `ForegroundWatcher` reads the last 10 seconds of app switches every second, not just the time since the previous poll, because a switch can be stamped behind it and then never seen (ADR 0024). The Log's "in front:" lines show each switch and how late it was seen.
- **Room migrations:** `exportSchema` is off and there are no auto-migrations. A schema change bumps the version in `Store.kt` and adds a `Migration` whose SQL matches the entity exactly (column types and `NOT NULL`), as `ADD_APP_STATS` does for version 2. Install the new APK over the previous one on a phone and check the log for `Store.load stats failed`.
- **Smart Reply and short messages:** ML Kit calls "hi", "ok" and "lol" not English, so the strip falls back to `QuickReplies` (ADR 0026). Test the strip with a short message as well as "free at 5?". A shell notification has no reply action, so use `adb emu sms send` on the emulator, or a real WhatsApp message on the A34.
- **Group taps:** tapping a collapsed group of chat notifications removes every chat in it with `REASON_GROUP_SUMMARY_CANCELED`. `GroupRemovals` decides what that means (ADR 0026); don't map a group's chats to the summary's reason directly.
- **Chat apps clear their notification on open:** WhatsApp and Messages do, and the demo chat now does too. The guessed Reply loop stays open in a chat-open state so the reply strip can send (ADR 0019). Test a strip change with the demo chat, not just a scenario test.
- **Release-only crashes:** R8 strips ML Kit's reflectively loaded registrars. The keep rules are in `app/proguard-rules.pro`. Always smoke-test the release APK, because a debug build hides this.
- **Unlock:** the Android 16 emulator doesn't deliver `USER_PRESENT` to runtime receivers. `SystemReceivers` watches the keyguard after `SCREEN_ON` instead (ADR 0013). On the emulator, run `adb shell locksettings set-disabled false` so there is a lock screen to dismiss.
- **compileSdk 37, targetSdk 36:** Compose 1.12 requires compileSdk 37; the test emulator is API 36.
- **Slow downloads here:** single connections to GitHub and dl.google.com crawl, so fetch large files with parallel `curl -r` ranges.
- **Dark mode:** screens take colours from `Tone` (Components.kt), which follows the system theme; `Brand` is for the overlays and marigold fills, which look the same in both themes. Check both with `adb shell cmd uimode night yes|no`.
- **Overlays:** uiautomator can't see them, so tap by coordinates. The chip ✓ sits at about (960, 379) and the pill button at about (893, 376) on the 1080×2400 emulator. Injected touches bypass TalkBack, so check accessibility through the node tree (`uiautomator dump`).
- **Shell quoting:** the YouTube launcher activity has a `$` in its name: `adb shell "am start -n 'com.google.android.youtube/.app.honeycomb.Shell\$HomeActivity'"`.
- **Real chat notifications on the emulator:** `adb emu sms send 5551234 "free at 5?"` makes Google Messages post a MessagingStyle notification with a reply action that clears itself when the chat opens, like WhatsApp. Use it to test arrivals, taps and the reply strip without demo mode.
- **Missed calls and business texts on the emulator:** `adb emu gsm call 5559876`, then `adb emu gsm cancel 5559876`, makes Google's dialer post a real missed call (a MessagingStyle "Missed call" in the `missed_call` category). `adb emu sms send AX-AIRTEL "…"` sends a text from a business sender ID. Shell notifications can't set a category, so emails and reminders run only in core tests (ADR 0029).
- **Device setup:** `scripts/setup-phone.sh` installs the APK and grants every permission adb can grant, on a phone or the emulator.
- **On-device check:** `scripts/check-pill.sh` (phone unlocked; `ANDROID_SERIAL` when the emulator is also attached) runs a message during use through a feed to a drawn pill and fails with the step that broke. Run it on the A34 after any change to the listener, the foreground poll, drift or the overlays. A scenario test can't catch an overlay that is attached but never drawn.
- **Some project-folder files are iCloud placeholders** (`ls -lO` shows `dataless`), and reading one hangs.
