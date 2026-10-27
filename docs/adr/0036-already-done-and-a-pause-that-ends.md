# Already done, and a pause that ends

Two small fixes Pranav picked from a list of polish.

**Already done.** Something dealt with where Earmark can't see, such as a reply from a laptop, a bill paid at the counter or a call from another phone, stayed open until its notification went, and Earmark kept guessing it. Pressing and holding a waiting row on the home screen, or "Already done" in TalkBack's actions, sends `WaitingDone`. The loop closes as done, not brought back, and nothing is learned about its kind, as with a tap on the row (ADR 0032). Chat apps post a waiting notification again unchanged when something else changes, so the engine remembers the message it was marked at and skips reposts with nothing newer. A new message opens it again. The mark is kept in memory only, so a restart that rebuilds loops from the shade (after an update or a reboot) offers it again.

ADR 0032 rejected an "already done" action on the chip, because Earmark should notice the evidence itself and the note keeps one button. This one is only on the home screen's rows, out of the note's way, for the cases where there is no evidence to notice.

**A pause that ends.** Pause had no end, so it was easy to forget it on for days. Settings › Pause Earmark offers For an hour, Until 7:00 AM (the next one) and Until I resume. `UserSettings.pausedUntil` stores the end in epoch milliseconds, or `Long.MAX_VALUE` for until you resume. A pause saved by an older version becomes until you resume. The engine gets `Settings.pausedUntil` and checks `paused(now)` at each decision, so a pause ends on time without another event. While paused, the running notification says until when and has Resume (ADR 0034). The home screen's banner names the time, and goes when the pause ends.

Rejected:

- **An undo after Already done:** the gesture is deliberate (a long press or a TalkBack action), and a new message reopens the loop anyway.
- **Clearing the app's notification on Already done:** a listener can, but the shade is yours, and the notification may still be useful.
- **Storing the marks so they outlast a restart:** one more table for a case that only follows an update or a reboot.
