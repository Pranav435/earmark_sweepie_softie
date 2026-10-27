# Build Earmark v0.1 for Android

You are building the first real version of **Earmark**, an Android app that guesses the one thing you unlocked your phone to do and, if you drift into a feed, hands that exact task back in one tap. It never blocks an app and never counts minutes. You have **3 hours of wall-clock time**, and the run ends with a signed APK that installs and works on a real Android phone. Build a small product that works end to end, with every visible surface finished. Hide any feature that isn't working rather than ship it half-built.

## Read first, in this order

1. `architecture.md`: the engineering design, which is authoritative for structure, names and constraints. If you find a better design, record it as an ADR and update `architecture.md` instead of departing from it silently.
2. `research/HOW_IT_WORKS.md`: product behaviour, loop types, scoring, drift and privacy rules.
3. Page 2 of `Earmark - One Pager.pdf`: how each loop opens, where "Do it now" goes, and what closes it.
4. `earmarkdemo/*.mp4`: the four demo stories (01 Reply, 02 Ride, 03 Bedtime, 04 Later) are the acceptance tests. Pull frames with `ffmpeg -i <file> -vf fps=1/2,scale=360:-1,tile=6x2 -frames:v 1 sheet.jpg` and read them.
5. Brand: colour tokens in `videos/shared/kit.css` (paper `#FFF8EC`, ink `#17150F`, marigold `#FFB224`, deep `#E08E00`). Use Instrument Serif for display, Instrument Sans for UI and JetBrains Mono for small labels. All three are OFL fonts, so download static TTFs from `github.com/google/fonts`. This folder syncs with iCloud, and some local font files (for example `onepager/fonts/InstrumentSerif-Italic.woff2`) are placeholders that hang on read.

## Ground rules

- **Timebox.** Run `date` at the start and write the start time into `PROGRESS.md`. At each phase gate, compare the clock with the budget below. If a phase overruns, cut scope from the tier lists instead of working past the gate. **Feature freeze at 2:30**: after that, only fixes, polish and shipping.
- **Install freely.** You may install whatever the build needs (Homebrew packages, `sdkmanager` packages, emulator images, pip tools, fonts, Gradle versions) without asking.
- **One builder.** Write all the code in the main thread so the codebase keeps one voice. Your subagent budget for the whole run is **3**: the `code-review` skill uses 2, and you have 1 spare for an independent lookup.
- **Autonomy.** The user answers the grill in Phase 0. After that, run to the end without questions: choose the recommended option, record it in an ADR, and keep going.
- **Commits.** `git init` in `android/`, commit at every phase gate, and tag the Phase 1 commit `scaffold` (it is the fixed point for code review).
- **Progress log.** Append one line to `PROGRESS.md` per gate: time, what works, what was cut.

## Engineering standards (apply throughout)

- **Design:** functional core, imperative shell, as laid out in `architecture.md`. Build deep modules with small interfaces, and use the names from `GLOSSARY.md` in code, tests and UI copy.
- **State:** one engine thread owns all product state. Screens are stateless Compose functions of a `StateFlow` UI state.
- **Failure:** every Android call that can throw (`PendingIntent.send`, `WindowManager.addView`, `queryEvents`, Room) is wrapped at the adapter edge and logged to `DebugLog`, then either takes the fallback `architecture.md` names or skips that effect. The core only ever sees events.
- **Security:** only the listener service is exported, guarded by `BIND_NOTIFICATION_LISTENER_SERVICE`. Every `PendingIntent` Earmark creates is `FLAG_IMMUTABLE`, and contact keys are hashed before they reach Room.
- **Performance:** usage polling runs only while the screen is on and unlocked, with no wakelocks and no exact alarms. Overlay windows are reused, and the chip appears within 300 ms of unlock (measure it with `DebugLog` timestamps).
- **Tests:** test at the agreed seams. Expected values come from the demo copy and worked examples.
- **Code:** idiomatic Kotlin, explicit types on public interfaces, and comments that explain why. Shipped code paths are finished, with no TODO stubs.

## Environment (verified on this Mac)

- JDK 21 is at `/opt/homebrew/opt/openjdk@21` but is not on `PATH`. Export `JAVA_HOME` to it.
- The Android SDK is at `/opt/homebrew/share/android-commandlinetools` (platform `android-36`, build-tools 35 and 36, platform-tools, and `adb` on `PATH`). Put it in `local.properties` as `sdk.dir`.
- Gradle 8.14.1 is cached at `~/.gradle/wrapper/dists/gradle-8.14.1-bin/*/gradle-8.14.1/bin/gradle`. Use it once to generate the wrapper, and let the wrapper move to whatever version the Android Gradle Plugin requires.
- A debug keystore exists at `~/.android/debug.keystore`.
- When this prompt was written, no device was attached and no emulator was installed. **At minute 0**, start a background job that installs `emulator` and `system-images;android-36;google_apis;arm64-v8a` and creates an AVD, so it is ready by Phase 3. If `adb devices` shows a real phone at any point, test on the phone first.
- Look up current stable versions (AGP, Kotlin, Compose BOM, Room, KSP, ML Kit `smart-reply`) from maven.google.com and the Gradle plugin portal instead of from memory, and pin them in `gradle/libs.versions.toml`.

## Skills

Invoke each skill with the Skill tool at the phase listed here. The design skills are written for the web, so apply their principles to Compose.

| Phase | Skill | Use |
|---|---|---|
| 0 | `mattpocock-skills:grilling` | Stress-test the plan with the user |
| 0 | `mattpocock-skills:domain-modeling` | `GLOSSARY.md` and ADRs in `docs/adr/` |
| 2 | `mattpocock-skills:tdd`, `mattpocock-skills:codebase-design` | Red→green slices at agreed seams; deep-module vocabulary |
| 4 | `frontend-design`, `emil-design-eng`, `unslop-ui` | Visual direction, polish, anti-slop UI |
| 4 | `accessibility` | WCAG 2.2 mapped to TalkBack and Compose semantics |
| 6 | `mattpocock-skills:code-review` | Standards and spec review since `scaffold` |
| 6 | `break-ui`, `review-animations` | Worst-case data and motion review |
| any | `mattpocock-skills:diagnosing-bugs` | Any bug that survives a first fix attempt |
| 6 | `unslop` | All user-facing copy and docs |
| 7 | `mattpocock-skills:wizard`, `mattpocock-skills:writing-for-agents` | Phone setup script; `CLAUDE.md` |

## Phase 0: Grill (0:00–0:15)

Run `mattpocock-skills:grilling` on the build plan. Ask everything in one round (two at most), give a recommended answer for each question, and seed the round with:

1. Which test phone (model, Android version, OEM skin), unless `adb devices` already shows it? Is USB debugging on?
2. The status dot: a small overlay dot at alpha 0.8 just below the status bar (recommended), or a silent notification icon?
3. When a chip fades without a tap, should Earmark still watch for drift on that loop? Recommended: yes, and an explicit swipe stops tracking.
4. Tier 2 order: Payment → Alarm routine → Leave-by → Unfinished flow (recommended). The Quick Settings tile and Gemini Nano wait for v0.2.
5. Demo mode: Earmark posts real `MessagingStyle` notifications (Rahul: "free at 5?"; Aisha requested $12.00) through the real pipeline, opens a small built-in demo chat, and uses a 10 s drift timer (recommended).
6. APK: a minified release build signed with the debug key (recommended), falling back to a debug build if R8 can't be fixed within 10 minutes.
7. Test seams for TDD: `Engine.handle`, `Classifier`, `Ranker` and `DriftMachine` (recommended).
8. Recap labels such as "Pay Aisha back $12" are kept for 24 h, then reduced to kind and time (recommended).
9. Default feed apps: Instagram, YouTube, TikTok, X, Reddit, Facebook and Snapchat, plus any app in `CATEGORY_VIDEO`, `CATEGORY_SOCIAL` or `CATEGORY_GAME`, minus messaging apps. Users can toggle each one in Settings.

**Gate:** the user has confirmed shared understanding, and `GLOSSARY.md` plus an ADR for each hard-to-reverse decision are written with `domain-modeling`.

## Phase 1: Tracer bullet (to 0:30)

Scaffold `android/` with the two modules from `architecture.md`: `:core` (Kotlin JVM) and `:app` (Compose, Material 3). Use Gradle Kotlin DSL and a version catalog. Wire dependencies by hand in one `AppGraph` object, because a DI framework costs setup time this build doesn't have. Put the font TTFs in `res/font` and build the theme from the brand tokens. Write the manifest with the notification listener service and the usage-access and overlay permissions, and remove the merged `INTERNET` permission with `tools:node="remove"`.

**Gate:** `./gradlew :core:test :app:assembleRelease` passes, and the APK installs and launches on the emulator or phone.

## Phase 2: Core by TDD (to 1:10)

Load `tdd` and `codebase-design`, then build `:core` in red→green vertical slices: `Classifier` (signal → loop or skip), `LoopBook` (open, close, expire, carry over), `Ranker`, `DriftMachine` and `CloseRules`, all behind `Engine.handle(event, now): List<Effect>`. Classifier skip cases: groups without a mention, one-time codes, bots, group summaries, ongoing notifications, a last message sent by the user, and Earmark's own notifications unless demo mode is on.

Write the four demo stories as **scenario tests**: scripts of events with expected effects, run on a fake clock. The core models every loop kind these scenarios use (Reply, Payment, Routine, LeaveBy, Flow); Phase 5 only wires up their Android sources. Use the exact copy from the demos:

- Reply: chip "Here to reply to Rahul?", sub-line "“free at 5?” · 1 min ago", pill "You came here to · Reply to Rahul · Do it now".
- Ride: chip "Here to book your ride?", pill button "Finish".
- Bedtime: chip "Set your 6:30 alarm?" with the sub-line "You usually do on weeknights", and the pill held back until the video pauses, button "Set it".
- Later: the pill is swiped away, and at the next unlock the carry-over chip reads "Pay Aisha back $12?" / "Still on your list from 1:18 PM" with a "Pay now" button.

**Gate:** every test is green, the four scenarios pass, and `:core` imports nothing from `android.*`.

## Phase 3: Android shell (to 1:55)

Build the adapters from `architecture.md`: `EarmarkListener` as the anchor that owns the engine dispatcher, `NotificationReader` (with an in-memory `WayBack` registry), `ForegroundWatcher`, `PauseSensor`, `SystemReceivers`, `OverlayHost` (chip, dot, pill and reply strip), `HandBack`, `ReplySuggester` and `Store` (Room via KSP). Every adapter checks its permission; when it is missing, the adapter stays idle and logs why. Every engine decision writes one line to `DebugLog` with the score breakdown and the reason a chip or pill was or wasn't shown, and never includes message text. To speed up device testing, grant permissions over adb: `cmd notification allow_listener <pkg>/<listener>`, `appops set <pkg> GET_USAGE_STATS allow` and `appops set <pkg> SYSTEM_ALERT_WINDOW allow`. Drive the device with `input` events, read `uiautomator dump`, and look at `adb exec-out screencap -p` screenshots.

**Gate:** in demo mode, the Reply story runs end to end on the device: unlock → chip → open a feed → pill at the first pause → **Do it now** opens the demo chat → a Smart Reply sends through `RemoteInput` → the loop closes → the recap lists it with "brought back". If WhatsApp or Google Messages is installed, run the story once with a real message too.

## Phase 4: Screens and design (to 2:20)

Load `frontend-design`, `emil-design-eng` and `unslop-ui`, then `accessibility`. Build four screens:

- **Onboarding:** a permission checklist that re-checks each permission on resume, with the "Allow restricted settings" steps for sideloaded installs on Android 13+.
- **Recap (home):** the serif headline "You came for 5 things." with an italic marigold second line "All 5 done.", rows with a green check and a "brought back" tag, and the footer "Brought you back 3 times today. Never blocked an app. Never counted a minute."
- **Settings:** feed apps, drift timer, demo mode and pause Earmark.
- **Debug log.**

Match the overlays to the demo frames:

- **Chip:** an ink capsule with a marigold round glyph (↶) on the left, a title and sub-line, a marigold ✓ on the right, and a thin line that shows the fade timer.
- **Pill:** pinned to the top, with the small label "You came here to", the task in bold, and a marigold action button whose verb fits the loop kind.
- **Reply strip:** sits above the chat's input area; the first suggestion is marigold, the others are outlined.

Motion is 200–300 ms ease-out, collapses to fades when animations are turned off, and never makes a sound or vibrates. The first tester uses **TalkBack**, so every surface must be fully usable by swipe and double-tap: labelled controls, 48 dp targets, pane-title announcements and correct reading order. At 200 % font scale, no text clips or overlaps.

**Gate:** every screen and overlay passes a TalkBack walk-through on the device and matches the demo frames in a side-by-side screenshot.

## Phase 5: Tier 2 sources (to 2:30, feature freeze)

Wire up the real Android source for each remaining kind in this order. Start each one with a failing Classifier test built from a real notification's fields:

1. **Payment:** a payment notification with an amount and "requested" (use a regex parser behind a `MoneyParser` seam). Its way back is the notification's tap action.
2. **Alarm routine:** after 22:30 on a weeknight with no alarm set for the next morning, given alarm history. Its way back is `ACTION_SET_ALARM` prefilled with the usual time, and it closes on `NEXT_ALARM_CLOCK_CHANGED`. Demo mode seeds the history.
3. **Leave-by:** `CalendarContract.Instances` in the next 2 h that have a location or a meeting link, using the optional calendar permission. Its way back is the ride app or the meeting link.
4. **Unfinished flow:** under 2 minutes in a ride, food or shopping app from a curated package list, then leaving with no "booked/ordered" notification.

Stop wherever 2:30 finds you. Hide any loop kind whose source isn't wired.

## Phase 6: Harden (to 2:50)

1. Run `code-review` with fixed point `scaffold`, and pass `prompt.md` and `architecture.md` as the spec path so it doesn't stop to ask about an issue tracker. Fix every correctness and spec finding; record any style finding you skip, with the reason, in `PROGRESS.md`.
2. Run `break-ui` on the chip, pill and recap: long names, emoji, right-to-left text, 200 % font, zero loops and 30 loops. Then run `review-animations` on the overlays.
3. Run `unslop` over `strings.xml`, `README.md`, `GLOSSARY.md` and the ADRs.
4. Run `./gradlew lint` and fix every error.

## Phase 7: Ship (to 3:00)

1. `./gradlew :core:test :app:assembleRelease`, signed with the debug key. Check the permissions with `aapt2 dump permissions`: the APK has no `INTERNET` permission and holds only the permissions that `architecture.md` lists.
2. Copy the APK to `../dist/earmark-v0.1.apk`, install it on the device or emulator, and run the Reply story one last time. Save the screenshots in `../dist/screens/`.
3. Write `README.md`: install steps (`adb install -r` or opening the file on the phone), restricted settings, granting each permission, and one test script per demo story, both in demo mode and with real apps.
4. If at least 10 minutes remain, use `wizard` to generate `scripts/setup-phone.sh`, which installs the APK and grants every permission it can over adb. Then use `writing-for-agents` to write a short `CLAUDE.md` with the build commands and the gotchas you hit.
5. Finish with a report: the APK path, what works, what was cut, test counts, and known issues.

## Definition of done

- `dist/earmark-v0.1.apk` exists at the project root, installs on Android 10+ and launches into onboarding.
- `:core` tests, including the four scenario tests, are green, and `lint` has zero errors.
- The Reply story works end to end on a device or emulator, with screenshots saved as evidence.
- Message text exists only in memory, the APK has no `INTERNET` permission, and logs carry no message content.
- Every visible control works with TalkBack, and the app runs without crashing when any permission is denied.
- `README.md`, `GLOSSARY.md`, `docs/adr/`, `PROGRESS.md` and `CLAUDE.md` describe what was built.
