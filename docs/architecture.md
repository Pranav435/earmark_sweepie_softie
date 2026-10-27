# Earmark v0.1: architecture

Earmark is an Android app (Kotlin, Compose, minSdk 29, targetSdk 36) that turns waiting notifications into **open loops**, guesses at unlock which one you came for, and hands it back in one tap if you drift into a feed. Everything runs on the phone: no server, no account, no AccessibilityService and no `INTERNET` permission.

## System

```
 ADAPTERS IN (:app)                          FUNCTIONAL CORE (:core, pure Kotlin)        ADAPTERS OUT (:app)
 NotificationReader  posted / removed ───┐                                          ┌─▶ OverlayHost   chip · dot · pill · reply strip
 ForegroundWatcher   unlock / foreground ┤   Engine.handle(event, now): List<Effect> ├─▶ HandBack      PendingIntent.send · RemoteInput
 PauseSensor         media / call / DND ─┼─▶  Classifier · LoopBook · Ranker         ├─▶ ReplySuggester ML Kit Smart Reply (bundled)
 SystemReceivers     alarm / screen ─────┤    DriftMachine · CloseRules              ├─▶ Store         Room, no message text
 OverlayHost         taps / swipes ──────┘                                          └─▶ DebugLog      ring buffer of decisions
```

- **One process, one anchor.** `EarmarkListener` (a `NotificationListenerService`) hosts everything. The system keeps it bound while notification access is on. `KeepAliveService`, a foreground service that does no work, holds the same process at foreground priority, because One UI freezes background apps without one (ADR 0018). The listener registers the runtime-only broadcasts (`USER_PRESENT`, `SCREEN_ON/OFF`, `NEXT_ALARM_CLOCK_CHANGED`), owns the overlay windows and calls `requestRebind` if it is disconnected.
- **Functional core, imperative shell.** Adapters translate Android callbacks into plain `Event`s. The engine returns `Effect`s, and adapters carry them out. `:core` is a Kotlin JVM module with no Android imports, so the module boundary enforces the seam and every behaviour is testable on the JVM with a fake clock.
- **Single-threaded engine.** All events are processed one at a time on a single dispatcher, so the engine needs no locks. Adapters do their I/O on `Dispatchers.IO`, post results back as events, and never block the main thread.

## Engine contract

```
Events in    NotificationPosted · NotificationRemoved(reason) · Unlocked · ScreenOff · ForegroundChanged(pkg)
             PlaybackChanged · BlockerChanged(call | nav | dnd) · AlarmChanged · CalendarSoon
             ChipHeld · ChipDismissed · ChipActed · PillTapped · PillDismissed · ReplyChosen · ReplySent
             KindUnmuted · SettingsChanged · StatsLoaded · Tick
Effects out  ShowChip(card) · ShowDot · ShowPill(card) · HideOverlay(which) · OpenWayBack(loopId, route)
             SuggestReplies(loopId) · SendReply(loopId, text) · RecordLoop · RecordKind · RecordContact
             RecordAlarm · Log(decision)
```

## Domain

| Term | Meaning |
|---|---|
| Loop | Something you may owe: `Reply`, `CallBack` (missed call), `Email`, `Reminder` (one you set), `Payment` (a request or a bill), `Flow` (unfinished ride, order or checkout), `Routine` (alarm), `LeaveBy`, `Own`. Fields: id, kind, label, package, conversation key, openedAt, expiresAt, state |
| WayBack | The exact route back: the notification's `contentIntent`, its reply `Action` (`RemoteInput`), or a launch or `ACTION_SET_ALARM` intent. Lives in adapter memory only, keyed by loop id |
| Chip | Unlock-time guess ("Here to reply to Rahul?"). ✓ holds it; ignoring it lets it fade |
| Drift | A feed app has been in front longer than the drift timer while the guessed loop is open and the loop's own app is not in front. Smart by default: your usual visit to that app, sooner for urgent loops or a held chip, 15 s to 2 min (ADR 0023); 10 s in demo mode |
| Pause | Media paused or stopped, the audio player changing (a reel boundary), or an app switch |
| Pill | The drift prompt: "You came here to · Reply to Rahul · **Do it now**", "Before you forget · …" for a message that arrived while the phone was in use, or "Next up · …" for the loop waiting after one you dealt with |

## The guess (at every unlock)

```
score = 0.35·recent + 0.25·who + 0.25·due + 0.15·history  (+0.15 for a message or request that came in while the phone was put down)
recent = exp(-age / 30 min)
who    = your replies to them; else how often you open their notifications; else how you deal with
         messages in their app; else 0.5 (× 0.8 for a group with no history of its own)
due    = 0.5 for a message to you, +0.25 if it asks (a "?" or a request), +0.25 for a time word,
         +0.25 if they keep writing; 1.0 if urgent; no loop at all for only "ok" or 👍; event start; request age
history = confirm rate for this kind (default 0.5)
chip only if score > 0.70 · 3 ignores in a row mute that kind
```

ADR 0017 changed `due` and the boost after the first real-phone test, where a plain "hey" could never clear the bar. ADR 0020 gives the boost only to messages that came in while the phone was put down. A message that arrives while the phone is in use gets no chip; it is scored the same way as it arrives and, above the bar, quietly becomes the guess if it scores higher than an earlier arrival. An urgent one takes over even a guess you came for, which comes back after it. When the guess is dealt with, the strongest loop left above the bar becomes **next up** (ADR 0027).

`due` is read from every message since your last one, with English, Hinglish and Hindi word lists (`Urgency`). A group message is a loop only when it's for you: it mentions you, it's urgent, or it asks something in a group whose notifications you usually open (ADR 0028). The loop quotes that message and keeps its age, so the chatter after it doesn't count as a new message.

## Drift state machine

`Idle → Guessed → InFeed → Drifting → PillShown → HandedBack | Dismissed`. The pill appears at the first **pause** after drift starts. Blockers hold it back: a call (`AudioManager` mode), active navigation, Do Not Disturb, or media still playing. Limits: one pill per message (ADR 0021), a 10-minute cooldown between pills, and swiping a pill away keeps Earmark quiet until the next unlock, when the loop comes back as a carry-over chip.

## Hand-back and close

- **Do it now** calls `wayBack.send()` with `ActivityOptions` that allow the background start (`MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS` on API 36, `..._ALLOWED` on 34–35). It must run while the pill window is still attached: since Android 15, an overlay app may only start activities from the background while one of its overlays is visible. If the intent was cancelled or is missing, Earmark opens the app's launch intent instead.
- **Reply strip:** Smart Reply reads the last ≤ 10 `MessagingStyle` messages and offers up to 3 replies. A tap sends through `RemoteInput.addResultsToIntent` + `action.actionIntent.send(...)`. If the chat app clears its notification as the chat opens, the loop stays open until you reply, leave the app or turn the screen off, so the strip can still send (ADR 0019).
- **Loops close on evidence:** the app clears or updates the conversation with a message from you, the dialer clears a missed call, the reminder is marked done, `NEXT_ALARM_CLOCK_CHANGED` fires, a "booked/ordered" notification arrives, or the payment request is removed. Each outcome is logged as done, dismissed or expired.

## Persistence and privacy

Message text, `PendingIntent`s and Smart Reply input stay in memory until the loop closes. Room stores `loop_log` (kind, times, outcome, brought-back flag, and a label kept for 24 h for the recap), `contact_stats` (a hashed conversation key, reply count, median reply latency, and how often its notifications were opened or swiped away), `app_stats` (per package: your usual visit to a feed app, and how many of a chat app's messages you dealt with or left and how soon) and `kind_stats` (confirms, ignores, muted). Your name from your own contact card stays in memory; names typed in Settings are kept in Earmark's private preferences. After a process restart, `onListenerConnected` rebuilds loops from `getActiveNotifications()` with fresh intents. Permissions: the notification listener binding, `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `READ_CALENDAR` (optional), `READ_CONTACTS` (optional, only your own contact card is read), `POST_NOTIFICATIONS` (the "Earmark is on" notification and demo mode), `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and `SET_ALARM` (a normal permission that `ACTION_SET_ALARM` requires for the alarm routine's way back).

## Android constraints designed for

- Overlays: `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`, `WRAP_CONTENT`, placed below the status-bar inset. A `ComposeView` in a service window needs `ViewTreeLifecycleOwner` and `SavedStateRegistryOwner` set by hand.
- Android 12+ blocks touches that pass through another app's overlay unless its alpha is ≤ 0.8, and that limit applies to the non-touchable status dot.
- The foreground app comes from `UsageStatsManager.queryEvents` (`ACTIVITY_RESUMED`), polled every 1 s only while the screen is on and unlocked. Media state comes from `MediaSessionManager.getActiveSessions(listenerComponent)` and `AudioManager` playback callbacks.
- Package visibility comes from a `<queries>` block for launcher apps and `ACTION_SET_ALARM` handlers, not from `QUERY_ALL_PACKAGES`.
- On Android 13+, a sideloaded APK needs **Allow restricted settings** (App info → three-dot menu) before notification access can be turned on.
- TalkBack: the chip and pill announce themselves through a pane title, every control has a label and a 48 dp touch target, and fade timers use `AccessibilityManager.getRecommendedTimeoutMillis`.

## Notes from the v0.1 build

- Chip and pill copy is built in the core (`Copy`), so scenario tests can check it against the demo copy (ADR 0011).
- Unlock is detected by watching the keyguard after `SCREEN_ON`, because `USER_PRESENT` doesn't always reach runtime receivers (ADR 0013).
- Demo mode drops the bedtime window and the pill cooldown (ADR 0006 addenda), and its reply PendingIntent is the one mutable intent (ADR 0012).
- `compileSdk` is 37, because Compose 1.12 requires it; `targetSdk` stays 36.

## Build

Gradle Kotlin DSL with a version catalog. `:core` is a Kotlin JVM module, and `:app` uses Compose Material 3 and Room via KSP. Dependencies are wired by hand in one `AppGraph`. The release build is minified with R8, signed with the Sweepie Softie release key (ADR 0016) and checked with `aapt2 dump permissions`.

## Verification and limits

`:core` tests cover the Classifier, Ranker and DriftMachine, plus four scenario tests that replay the demo stories (Reply, Ride, Bedtime, Later) through `Engine.handle`. Demo mode posts real `MessagingStyle` notifications from Earmark into the same pipeline, so the whole flow runs on any phone. Limits: Earmark cannot see inside other apps (so flows are inferred), reel boundaries are best-effort, stored intents are lost on reboot, Smart Reply works in English only, and there is no iOS build.
