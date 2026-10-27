# Earmark v0.1 build log

Start: Tue Oct 27 11:27:46 IST 2026 (budget 3 h, feature freeze 2:30 → 13:57, end 14:27)

- 11:35 Phase 0 gate: grill answered (14 decisions), GLOSSARY.md + 10 ADRs written. Cut: nothing.
- 12:05 Phase 1 gate (+8 min over: Gradle and Google Maven downloads ran at ~0.1–0.2 MB/s per connection, so I fetched the Gradle zip with parallel range requests; Compose 1.12 needed compileSdk 37). Works: :core:test, minified release APK installs and launches on the API 36 emulator. Tagged scaffold. Cut: nothing.
- 12:11 Phase 2 gate: 28 :core tests green, including the four scenario tests (Reply, Ride, Bedtime, Later); :core has no android imports. Cut: nothing.
- 12:37 Phase 3 gate: Reply story end to end on the API 36 emulator in demo mode (chip 144 ms after unlock → ✓ dot → YouTube → pill at drift → Do it now opens demo chat → Smart Reply sent via RemoteInput → loop closed → recap 'Done.' with 'brought back'), plus once with a real SMS through Google Messages (Messages clears its notification on open, so the loop closes there and the reply strip steps aside). Fixed: Android 16 build doesn't deliver USER_PRESENT to runtime receivers (keyguard watch after SCREEN_ON); unlock-into-open-feed race. Cut: nothing.
- 12:42 Phase 4 gate: four screens (onboarding with restricted-settings help, recap, settings, decision log) and four overlays match the demo frames (dist/screens/demo-vs-build.png). TalkBack: accessibility tree verified with TalkBack on (labels, reading order, 48 dp targets, pane titles, long-press as the drag alternative); adb can't inject TalkBack gestures, so a by-hand swipe-and-double-tap walk was not possible. 200% font scale checked on chip and recap and fixed. Cut: nothing.
- 12:48 Phase 5 gate: all four Tier 2 sources wired (Payment via MoneyParser, Alarm routine via NEXT_ALARM_CLOCK_CHANGED + weeknight history, Leave-by via CalendarContract.Instances, Unfinished flow via usage events); 32 :core tests including real-notification wording. On the emulator in demo mode: Bedtime (faded chip still tracked → pill → Set it → Clock prefilled 6:30 → loop closed) and Later (pill swiped → carry-over chip → Pay now → paid → closed) both pass; recap 'All 2 done.' Ride and Leave-by run in scenario tests only (no ride app on the emulator). Cut: nothing.
- 12:55 Phase 6 gate: code-review against `scaffold` (Standards + Spec, 2 subagents). Fixed every correctness and spec finding:
  - Closed Leave-by and Routine loops no longer reopen.
  - The recap uses today's guess time.
  - A notification tap isn't counted as a reply.
  - A chip tap counts as a hold.
  - Closing a loop hides its chip and forgets its message text.
  - Message text of skipped notifications is forgotten at once.
  - Loop ids are hashed before Room, and payment ids come from the hashed conversation key.
  - The 24 h label trim runs at every unlock and can't be undone by a late write.
  - Glossary terms are used in UI copy.
  - Public functions have explicit types.
  - The Store guard rethrows cancellation.
  - The settings fallback launch is wrapped.
  - Stats snapshots no longer overwrite newer engine counts.
  Spec judgement calls are recorded in ADRs 0014 and 0015. break-ui fixes:
  - Arabic and full-width question marks count as questions.
  - Bare clock times count as time words.
  - Amounts keep thousands separators.
  - Long quotes are clamped so the chip shows its age.
  - 200% font scale works on the chip and recap.
  unslop: no findings in strings.xml, GLOSSARY.md or the ADRs. Lint: 0 errors. :core tests: 42 green.
  Skipped:
  - review-animations: user-invocable only; run /review-animations.
  - Standards style smells not fixed, with reasons:
    - "Primitive Obsession" on LoopRow kind/outcome strings: Room columns, mapped once in Store.
    - "Middle Man" on WayBackRegistry: it is the memory-only boundary ADR 0004 names, and a seam for Forget.
    - "Duplicated Glyph": the overlay and app modules style it differently.
    - "Feature Envy" on recapRows and stateless-screen purity in MainActivity.Root: the screens themselves are stateless; Root is the one state holder, so a ViewModel layer isn't worth it in v0.1.
    - Fade and strip close decided in the overlay: a fade is deliberately not an engine event (ADR 0007).
    - "ReplySuggester" vs the glossary's avoided "suggestions": architecture.md names the module ReplySuggester.
    - Exported launcher activity: a launcher entry must be exported; nothing else is.
  Cut: nothing.
- 13:05 Phase 7 gate (shipped):
  - dist/earmark-v0.1.apk is a minified release (R8) signed with the debug key, 37 MB. Permissions: PACKAGE_USAGE_STATS, SYSTEM_ALERT_WINDOW, READ_CALENDAR, POST_NOTIFICATIONS and SET_ALARM; no INTERNET.
  - A fresh install launches into onboarding. The Reply story was re-run on the release APK (screens in dist/screens/final/).
  - Release-only bugs caught and fixed:
    - R8 stripped ML Kit's registrars, so the listener crashed at connect. Fixed with keep rules and a lazy, guarded client.
    - With animations off, the chip faded instantly. The timer now uses real time.
  - With only notification access, the app runs and logs which adapters are idle.
  - 42 :core tests green; lint 0 errors. No message text in logcat.
  - README.md, CLAUDE.md and scripts/setup-phone.sh (smoke-run against the emulator) written.
  - Known issues:
    - Tested only on an API 36 emulator.
    - TalkBack was checked through the node tree; gestures weren't walked by hand.
    - Ride and Leave-by run only as scenario tests.
    - The reply strip steps aside for apps that clear their notification when the chat opens.
  - Cut: nothing.
- 13:15 After ship, at the user's request:
  - Applied all ten /review-animations findings:
    - The chip timer pauses while pressed, dragged or TalkBack-focused (verified: a 10 s press past the 8 s timer, then held).
    - The flick threshold is in dp (110 dp/s); a 220 px flick dismisses on velocity alone (verified).
    - A flick leaves in the direction it was thrown.
    - The pill also dismisses with a swipe up (verified).
    - The chip and pill enter in 220 ms.
    - Every fade uses the ease-out curve.
    - On ✓, the chip shrinks toward the top-left dot, and the dot follows 120 ms later.
    - Reply-strip buttons have press feedback.
    - The chip only fades when a pill replaces it.
  - dist/earmark-v0.1.apk is now committed to the repo.
- 13:24 Real-phone install was blocked, so release builds are now signed with a dedicated Sweepie Softie RSA 4096 key, using signature schemes v1, v2 and v3 (ADR 0016 supersedes 0010).
  - The keystore is ~/.android/earmark-release.jks; keystore.properties is git-ignored.
  - Verified with apksigner. Reinstalled on the emulator with setup-phone.sh, and the chip still shows (21 ms after unlock).
  - The README now covers the debug-key conflict, Play Protect, and the sensitive-permission block on browser and file-manager installs.
- 13:55 v0.1.1, after the first real-phone test (Samsung Galaxy A34): a WhatsApp message brought no chip, and the home screen said "Nothing guessed yet today."
  - Cause, reproduced on the emulator with a WhatsApp-shaped notification: a message with no "?" and no time word could score at most 0.70, and the chip needs more than 0.70. A question cleared the bar only within about 60 seconds of arriving. The log line was "unlock: no chip, best reply:… scored 0.70 … needs > 0.7".
  - Fix (ADR 0017): a Reply's `due` starts at 0.5, and the +0.15 boost goes to the first unlock after the message, however long after. A plain "hey" two minutes old now scores 0.80 and shows a chip. 45 :core tests green (3 new), including the four scenario tests with their copy unchanged.
  - The home screen now says how many loops Earmark is watching and that it guesses at unlock. If notification access is on but Android hasn't connected the listener, it shows a banner and asks Android to rebind.
  - Dark mode: the screens follow the system theme through `Tone`; the overlays stay ink with a hairline edge in dark mode. Checked on the emulator with `cmd uimode night yes`.
  - dist/earmark-v0.1.1.apk (versionCode 2), signed with the Sweepie Softie key, installs over v0.1. No INTERNET permission; lint 0 errors.
