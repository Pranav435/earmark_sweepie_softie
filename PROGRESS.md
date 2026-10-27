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
