# The held guess in the running notification

Amends ADR 0003 and ADR 0018. The marigold dot was the only sign that Earmark was still holding a guess after its chip faded, and it is decorative: TalkBack skips it, and its window takes no touches. Earmark's first tester uses TalkBack, so for them nothing was held at all. Asked what should change, Pranav chose to put the held guess in the "Earmark is on" notification, which is already there (ADR 0018).

**What it says.** `Engine.held()` is the guess while Earmark is holding it: from the moment it becomes the guess (at an unlock, as an arrival, as next up, or from the tile) until it's handed back, waved off, or the phone is put down. That's the phases Guessed, InFeed, Drifting and PillShown. The listener publishes it with the waiting list after every event, and `KeepAliveService` updates its notification only when it changes:

- Title: "Holding: Rahul". Text: the note's words, "“free at 5?”", else the task.
- One action, the note's verb ("Reply"). Tapping the notification does the same.
- Nothing held: "Earmark is on", as before. Paused: "Earmark is paused · Until 4:30 PM" with Resume (ADR 0036).

The notification is private, and its public version, which the lock screen shows, says only "Earmark is on". The channel stays at minimum importance and the update is silent, so nothing new buzzes or peeks.

**Handing it back.** Android doesn't let a notification start an activity through a receiver, so the notification opens `HandBackActivity`, an invisible activity in its own task. It sends `HeldOpened` and finishes as soon as it stops; if nothing opens in 2 seconds, it finishes anyway rather than sit in front eating touches. While it's in front, Earmark may start the way back over it. The engine treats `HeldOpened` like the chip's button: hand it back, hide the chip, pill and dot, and learn the yes (ADR 0032, 0033). If the guess changed before the tap landed, the loop still opens, as a detour like a waiting row.

**Text in the shade.** The note's words now sit in Earmark's own notification while the guess is held. They are the words the sender's app already shows in its own notification, and they leave when the guess does (ADR 0004).

Rejected:

- **A TalkBack announcement as the chip fades:** spoken once, with nothing to come back to later.
- **A second notification just for the held guess:** a new channel and a second row in the shade, where the one that's always there will do.
- **Making the dot itself focusable:** it would put a TalkBack stop in front of every app, all the time.
