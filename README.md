# Earmark v0.1.13

Earmark guesses the one thing you unlocked your phone to do. If you drift into a feed, it hands that exact task back in one tap. It never blocks an app and never counts minutes. Everything runs on the phone: the APK has no internet permission.

This build is for testers. It runs on Android 10 or later and was tested on an Android 16 (API 36) Pixel 7 emulator and a Samsung Galaxy A34 (One UI 8, Android 16).

## Install

The APK is `dist/earmark-v0.1.13.apk` in this repository. On GitHub, open that file and use **Download raw file**.

- **From a computer:** connect the phone with USB debugging on, then run `adb install -r dist/earmark-v0.1.13.apk`.
- **On the phone:** copy the file to the phone, open it from Files, and allow installs from that app when Android asks.

`scripts/setup-phone.sh` installs the APK and grants every permission that adb can grant, in one go. Then `scripts/check-pill.sh`, with the phone unlocked, checks the whole path on the phone in about 30 seconds: it posts a test message, opens Instagram or YouTube, and checks that the pill is shown and actually drawn. It leaves a "Reply to Test" row on today's list.

### If the install is blocked

- **"App not installed" or "conflicts with an existing package":** an older Earmark signed with a different key is on the phone. Builds from before 27 October 2026 were signed with a debug key. Uninstall Earmark, then install again.
- **Play Protect says the app is unsafe or unrecognised:** tap **More details › Install anyway**. Earmark isn't from the Play Store, and it asks for notification access, which Play Protect treats as sensitive.
- **Blocked with a message about sensitive permissions, and no way past it:** in some countries Google blocks apps that ask for notification access when they're installed from a browser, a messaging app or a file manager. Install over USB with `adb install -r dist/earmark-v0.1.13.apk` or `scripts/setup-phone.sh`, which that check doesn't cover.

The APK is signed with the Sweepie Softie release key. To check it, run `apksigner verify --print-certs dist/earmark-v0.1.13.apk`; it should show `CN=Sweepie Softie, OU=Earmark, O=Sweepie Softie`.

## Allow restricted settings (Android 13 and later)

Android blocks notification access for apps installed from a file until you allow it:

1. Open Earmark once.
2. Go to Settings › Apps › Earmark (or long-press the Earmark icon and choose App info).
3. Tap the three-dot menu at the top right and choose **Allow restricted settings**. Confirm with your PIN or fingerprint.

Earmark's first screen shows the same steps, with a button that opens App info.

## Grant the permissions

Earmark's first screen lists each permission with a button. It checks them again every time you come back to it.

| Permission | Why | On the phone | Over adb |
|---|---|---|---|
| Notification access | See messages, missed calls, reminders, emails and bills, and open the exact one | Open settings › Earmark › Allow | `adb shell cmd notification allow_listener com.sweepiesoftie.earmark/com.sweepiesoftie.earmark.listener.EarmarkListener` |
| Usage access | Know which app is in front, only while the screen is on | Open settings › Earmark › Permit usage access | `adb shell appops set com.sweepiesoftie.earmark GET_USAGE_STATS allow` |
| Display over other apps | Show the chip and the pill on top of any app | Open settings › Allow display over other apps | `adb shell appops set com.sweepiesoftie.earmark SYSTEM_ALERT_WINDOW allow` |
| Calendar (optional) | Leave-by loops for events with a place or a meeting link | Allow | `adb shell pm grant com.sweepiesoftie.earmark android.permission.READ_CALENDAR` |
| Your name (optional) | Reads only your own contact card, so a group message that says your name counts. Your other contacts are never read | Allow | `adb shell pm grant com.sweepiesoftie.earmark android.permission.READ_CONTACTS` |
| Battery: unrestricted | So the phone doesn't pause Earmark while other apps are in front. Samsung phones need it | Allow | `adb shell dumpsys deviceidle whitelist +com.sweepiesoftie.earmark` |
| Notifications (optional) | Shows the "Earmark is on" notification, and lets demo mode post its sample messages | Allow | `adb shell pm grant com.sweepiesoftie.earmark android.permission.POST_NOTIFICATIONS` |

If a permission is missing, Earmark keeps running and does less. The Log screen (top right of the home screen) says which part is idle and why. Its **Copy** button copies the whole log, which never contains message text, so you can paste it into a bug report.

## What you'll see

- **Chip:** after you unlock, a dark capsule at the top makes its guess, for example "Here to reply to Rahul?". Tap ✓ to keep it, and it shrinks into a small marigold dot under the status bar. Tap the chip itself to go there now. Swipe it sideways (or long-press it) for "not this". If you ignore it, it fades, and Earmark still watches.
- **Pill:** if you open a feed app such as YouTube or Instagram while that loop is open, Earmark shows it a few seconds in, or, if a video is playing, at the next reel, short or pause: "You came here to · Reply to Rahul · **Do it now**". For a message that arrived while you were using the phone, it reads "Before you forget · Reply to Priya · **Do it now**". Swipe it away (or long-press it) for "not now", and it comes back as a chip at your next unlock.
- **Reply strip:** after Do it now on a message, up to three one-tap replies appear above the chat's input. They come from Smart Reply, or, when it can't answer (another language, or a very short message such as "hi"), from simple quick replies that fit the message: "Yes / No / Let me check" for a question, "Hey!" for a greeting, "Got it / Will reply soon / Can I call you?" otherwise. They also appear when you open any message's chat from its notification. They stay when WhatsApp or Messages clears the notification as the chat opens, and go away when you reply or leave the app. The strip names who the replies go to, since it stays while you move between chats.
- **"Earmark is on":** a silent notification, with a small icon in the status bar, while Earmark runs. It keeps the phone from pausing Earmark in the background, which Samsung phones otherwise do.
- **Recap (home screen):** today's list. Each message or request Earmark picks up appears the moment it arrives, with a marigold ring while it's waiting, alongside anything Earmark showed you as a chip or pill. Each row shows how it ended, and the footer says how many times Earmark brought you back.

**Which message wins.** When several messages are waiting, from any apps, Earmark picks the one that asks most of you: "call me asap" beats "that was fun", and a question beats a plain message. "ok", "thanks", 👍 and reactions don't count as waiting at all. Between people you've never answered through Earmark, the app you usually deal with sooner wins, once Earmark has seen five of that app's messages close. When you've dealt with the guess, the strongest message still waiting becomes **next up**, with no chip: if you then drift into a feed, its pill reads "Next up · Reply to Zoya · Do it now". An urgent message that arrives while you're on the phone takes over even a guess you came for, which comes back after it. An Instagram DM is watched while you scroll Instagram itself, until you open the chat.

**Not just messages.** Earmark also picks up a **missed call** ("Call Mom back"; Do it now opens the call log, so a stray tap never rings anyone), a **reminder** you set in Keep, Google Tasks, Samsung Reminder, Microsoft To Do or your calendar (the chip is the reminder itself: "Submit lab report?"), an **email** from a person that asks you something or is urgent ("Reply to Prof. Mehta's email"), and a **bill** that's due, from a text or an app ("Pay your Airtel bill ₹499"). These sit alongside payment requests, a ride or order you left unfinished, your weeknight alarm, and calendar events with a place or meeting link. Texts from businesses, with sender IDs such as AX-AIRTEL, are never treated as messages to answer.

**Groups.** A group message counts when it's for you: it says your name or @You (your name comes from your contact card, if you allow it, and from **Settings › Your name**), it uses urgent words such as "urgent", "hospital" or "jaldi", or it asks a question in a group whose notifications you usually tap. The chip names the group ("Here to reply to Mom in Family?"), and the reply strip replies to the group. Other group chatter is ignored, except that Earmark counts whether you tap or swipe away each group's notifications.

No chip appears when a message arrives while you're using the phone, because its heads-up already showed. Earmark makes it the guess quietly instead, and lists it on the home screen. If you drift into a feed, the pill brings it back. Messages that arrive while the phone is locked are guessed when you unlock, and only those get a chip. The dot only appears after you tap ✓ on a chip. While today's list is empty, the home screen says how many loops Earmark is watching. Earmark follows the phone's light or dark theme.

With TalkBack, the chip and pill announce themselves when they appear. Every control is labelled. The "not this" and "not now" swipes are also in the TalkBack actions menu.

## Test scripts

Turn on **Settings › Demo mode** first for the demo scripts. Demo mode posts real notifications from Earmark that go through the same pipeline as any other app's. It always uses the Right away drift timer, and gives you a weeknight 6:30 alarm habit.

"Lock and unlock" means: press power, wake the phone, and unlock it.

### 1. The Reply

**Demo mode:**

1. On the home screen, tap **Send Rahul's message**.
2. Lock and unlock. The chip reads "Here to reply to Rahul?" with "“free at 5?” · just now". Tap ✓.
3. Open YouTube. A few seconds in, the pill reads "You came here to · Reply to Rahul · Do it now".
4. Tap **Do it now**. The demo chat with Rahul opens, with reply buttons above the message box.
5. Tap the first reply. It sends, Rahul answers, and the strip and the dot go away.
6. Open Earmark. The recap shows "Reply to Rahul" with a green check and "brought back".

**With real apps:** lock the phone, then ask a friend to send you a WhatsApp or Google Messages message. Any message works, and a question such as "free at 5?" scores highest. Leave it unread, unlock within about 10 minutes, then follow steps 2 to 6. With Right away, the default drift timer, the pill comes about 3 seconds after your feed app opens. In Settings you can pick Smart instead, which waits for how long you usually stay in that app, or a fixed time. The Log screen says when the pill will come, for example "pill after 3 s (right away)". WhatsApp and Messages clear their notification as soon as the chat opens; the reply strip stays anyway and sends through it.

**A message while you're on the phone:** unlock, open YouTube or Instagram, and have a friend message you. No chip appears. After the drift timer, at a pause, the pill reads "Before you forget · Reply to …". Or tap the message's notification instead: the chat opens with the reply strip. Lock and unlock afterwards, and that message doesn't come back as a chip.

### 2. The Ride

The Ride story needs a ride app (Uber, Ola, Lyft, Bolt, Rapido or Grab) and the calendar permission. It has no demo version.

1. Add a calendar event starting about 2½ hours from now, titled "Flight to Lisbon", with location "Airport · Terminal 1".
2. Lock and unlock. The chip reads "Here to book your ride?" with "Airport · <start> · leave by <time>". Tap ✓.
3. Open the ride app, start a booking, and leave within 2 minutes without confirming.
4. Open a feed app. At the first pause, the pill reads "You came here to · Book your airport ride · Finish".
5. Tap **Finish**. The ride app opens where you left it. When its "booked" or "driver is on the way" notification arrives, the loop closes.

### 3. Bedtime

**Demo mode:** make sure no alarm is set for tomorrow morning.

1. Lock and unlock. The chip reads "Set your 6:30 alarm?" with "You usually do on weeknights". Let it fade.
2. Open YouTube and play a video. While it plays, Earmark waits. Pause it, and the pill reads "You came here to · Set your 6:30 alarm · Set it".
3. Tap **Set it**. The Clock app opens with 6:30 filled in. Save it, and the loop closes.

**With real apps:** set the same weekday alarm on at least three weeknights. On a weeknight after 22:30, with no alarm set for the next morning, follow steps 1 to 3.

### 4. Later, not never

**Demo mode:**

1. Tap **Send Aisha's request**. Lock and unlock. The chip reads "Here to pay Aisha back?". Tap ✓.
2. Open YouTube. The pill reads "Pay Aisha back $12 · Pay now". Swipe it away.
3. Lock and unlock. The carry-over chip reads "Pay Aisha back $12?" with "Still on your list from <time>" and a **Pay now** button.
4. Tap **Pay now**. The demo payment screen opens. Tap **Pay $12.00**, and the loop closes as brought back.

**With real apps:** ask someone to request money from you in Google Pay, PhonePe, PayPal, Venmo or Cash App, then follow steps 1 to 4.

## If no chip shows up

1. Open Earmark. If it says Android hasn't connected it, turn Earmark's notification access off and on again in the settings page it opens.
2. Open **Log** (top right of the home screen) and look at the newest lines:
   - No "opened reply:… from com.whatsapp" line means Earmark never saw the message. Check notification access, and that WhatsApp's own notifications are on.
   - "unlock: no chip, best … scored 0.62 …, needs > 0.7" means Earmark saw it but wasn't sure enough. A message gets the most weight at the first unlock after it arrives, and less as it gets older.
   - No "unlock" line means Earmark didn't notice the unlock. Lock the phone, wait a few seconds, and unlock it again.
3. If the home screen asks to let Earmark run in the background, tap **Allow**. Without it, Samsung phones pause Earmark while other apps are in front, so chips and pills only show once you open Earmark. You can also set it in Settings › Apps › Earmark › Battery › **Unrestricted**.

The Log is kept in memory, so it starts empty after Earmark restarts.

## Privacy

- No internet permission, no account and no server. Smart Reply runs on the phone with a bundled model.
- Message text and the notifications' tap and reply actions stay in memory until the loop closes. Notifications Earmark skips are forgotten straight away.
- For each message or request Earmark picks up, and each chip or pill, the database stores the loop's kind, times, outcome, and a short label such as "Pay Aisha back $12". After 24 hours the label is cleared, and the recap shows only the kind and time. Contact statistics are keyed by a salted hash, never a name or number.
- For the smart drift timer, the database keeps one running number per feed app: how long you usually stay in it. Nothing about what you did there.
- For ranking, the database keeps per chat app how many of its messages you dealt with or left, and how soon you usually got to them, and per conversation (by salted hash) how often you opened or swiped away its notifications. Counts only.
- Urgency and mentions are worked out on the phone from the words, with word lists, and never logged. With the optional contacts permission, Earmark reads only your own contact card and keeps what it finds in memory. Names you type in Settings › Your name stay in Earmark's private storage on the phone. Neither is ever logged.
- The Log screen shows why each chip or pill was or wasn't shown. It never shows message text.

## Known limits in v0.1.13

- Earmark can't see inside other apps, so unfinished bookings are inferred from how long you spent in a ride, food or shopping app.
- Reel boundaries are best-effort and depend on whether the feed app publishes media sessions. A feed with nothing playing gets the pill as soon as drift starts.
- Stored tap actions are lost on reboot; Earmark then opens the app instead of the exact chat.
- Smart Reply is English only, and calls very short messages such as "hi" or "ok" not English. For those the strip offers quick replies, which are English and simple. When Smart Reply holds back because it isn't confident, which includes sensitive topics, the strip stays hidden. The Log screen says which. Chip and pill copy is English only.
- The reply strip sits just above the chat's input, so it can cover the newest message until you reply or close it.
- On the Galaxy A34, the pill was checked over Instagram with Earmark in the background and its screen opened and left: in demo mode, with a fixed 30 s timer and with the smart timer. The reply strip was checked with a real WhatsApp "hi" opened from its notification. The chip at unlock uses the same overlay fix but wasn't re-checked there.
- The Smart drift timer, an option in Settings, starts at 45 seconds and needs five visits to a feed app before it goes by your usual visit there. With Right away, the default, a pill cuts most visits short, so Smart learns slowly.
- Urgency comes from word lists in English, Hinglish and Hindi, so it misses urgent messages that use none of those words, and a name that's also a common word can count as a mention. A group mention the app shows as a phone number isn't recognised.
- Missed calls close when the dialer clears them. Google's dialer clears every missed call once you call anyone back or look at Recents, so they all close as done.
- Opening an email closes its loop as done; Earmark can't see whether you replied. An email that asks nothing isn't picked up, but a newsletter worded as a question can be. Snoozing a reminder closes it until it goes off again.
- Bills are recognised from English wording ("is due", "due on", "overdue") with an amount. Opening the bill's text closes it as done, though it may not be paid.
- Missed calls and texted bills were run end to end on the emulator, and a real Outlook email was picked up on the A34. Reminders and email chips are checked in tests only, because the adb shell can't post a notification with a category.
- On some Android 16 builds the system never tells apps about an unlock (`USER_PRESENT`), so Earmark watches the lock screen briefly after the screen turns on instead (ADR 0013).

## For developers

- Build: `./gradlew :core:test :app:assembleRelease` (JDK 21, Android SDK with platform 37).
- The domain language is in `GLOSSARY.md`, and decisions are recorded in `docs/adr/`.
- `docs/architecture.md` is the engineering design, and `PROGRESS.md` is the build log.
- `CLAUDE.md` has the build commands and the gotchas found while building v0.1.
