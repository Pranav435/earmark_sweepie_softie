# A note from a friend: the chip, pill and home screen redesigned

Amends ADR 0003 (the dot), ADR 0011 (the copy) and ADR 0023 (the smart timer), and changes how the recap leads. A design review of 0.1.15 on the emulator found that the chip and pill had two marigold circles of the same size: the ↶ glyph, which read as a Back button, and the button itself. ✓ meant "keep this", but looked like "done", and nothing said that tapping the words went there now. The home screen led with "31 things today. 20 of 31 done.": a score, in an app that promises never to count. Waiting and finished rows were mixed together and out of order, and you couldn't act on any of them. Pranav said the app felt generic and the home screen felt like a tracker, and chose from options in a grilling: "a friend's tap" as the feel; a note from a friend as the look; the person's own words first; one button; who's waiting first on the home screen; setup one step at a time; and a soft shoulder tap when the pill arrives.

**The note.** The chip and the pill are one card, `Note` in the core and `NoteCard` on screen. In light mode it is paper; in dark mode it is a warm dark card, so it doesn't glare over a dark feed at night. In order, it shows:

- **Face:** the photo the sender's app put in the notification, else their initial in Instrument Serif on marigold; a bill, an alarm, a ride or a reminder shows its app's icon.
- **Who** in the serif: "Rahul", "Mom in Family", "Airtel postpaid bill", "6:30 alarm".
- **Their words:** "“free at 5?”".
- **Why it's here**, in italic serif marigold: "Here to reply?" on the chip; "You came here to reply", "Before you forget" or "Next up" on the pill.
- **One verb:** Reply, Call back, Pay, Set it, Finish, Book, Join or Open, never "Do it now".

The photo is read from the way back held in memory, so it goes when the loop does, like the message text (ADR 0004). A name that is only a phone number is set in Instrument Sans, because the serif's 1 reads as an l. `task` ("Reply to Rahul") stays on every card, for TalkBack and the recap. The note sits just under the status bar, where a message's heads-up shows. Before, overlays were laid out below the status bar and then moved down by its height again.

**The gist.** A short message is quoted whole: up to 12 words and 72 characters. From a longer one, `Gist` takes the sentence that is urgent, else the last one ending in "?", else the last one that asks by its words, else the start. It trims at a word and marks a sentence picked from the middle with "…". Summarising was looked into first. Gemini Nano through ML Kit GenAI runs only on newer flagships (not the A34 or a Pixel 7), refuses to run unless the app is in front, and its libraries bring in `INTERNET`. A bundled small model would add about 300 MB, so it was ruled out.

**One button, no hold.** ✓ is gone. Tapping the chip or its verb hands the loop back, and is the "yes, this one" that `KindStat.holds` counts. Swipe or long-press still means "not this". A chip left alone fades as before (ADR 0007). Now the engine hears it (`ChipFaded`), and while the loop is still the guess, the dot shows that Earmark is holding it. That fade is the only thing that shows the dot now, where before only ✓ did. The pill hides the dot, which would peek out from under its corner. With no hold, the smart timer loses its "held ×0.75"; urgent and due-soon still shorten it.

**Feel.** The pill arrives with two soft ticks, sent as a notification vibration, so a phone on silent stays still. The chip stays silent, because it shows at most unlocks. Pressing the verb gives a confirm click, and a swipe ticks as it crosses the point where letting go dismisses. The note is one TalkBack stop. It announces "Earmark: Rahul. “free at 5?”. You came here to reply", acts with "Reply to Rahul", and has "Not now" or "Not this" in its actions.

**Home leads with who's waiting.** `Engine.waiting(now)` lists the open loops on today's list in the order an unlock would guess them. The listener publishes the list with the open-loop count. The headline is a sentence about people: "Rahul and 2 others are waiting.", or "Nobody's waiting on you." Under it, in italic marigold, comes "Brought you back 3 times today." Each row is a note with its verb; tapping it sends `WaitingOpened`, which hands the loop back as a detour from the guess, like a chat opened from its notification. It isn't a guess you confirmed, so nothing is learned about its kind. The rest of today folds into "Earlier today", collapsed. "20 of 31 done" is gone.

**Setup, one step at a time.** First comes a sample note, then one screen each for notification access, usage access, display over other apps and battery. Each screen has one line on why, the button at the bottom, and "Not now". The optional calendar, your name and notifications share a last screen. "Fix it" on the home screen and Permissions in Settings open the first step still missing. Settings puts tester tools last: Pause, When the pill comes, Feed apps, Your name, Muted guesses, then For testers (Demo mode, Permissions, Decision log).

Rejected:

- **Keep the dark capsule and fix only the confusing parts:** it fixed the confusion but left the look Pranav called generic.
- **A folded-corner ("dog-ear") motif:** on-brand, but Pranav preferred the warmer note from a friend.
- **"Now" and "Later" buttons on the chip:** clearer than ✓, but two choices on a card meant for a glance. Ignoring the chip already means "later".
- **An "already done" action:** Pranav would rather Earmark notice the evidence itself than ask.
- **The pill at the bottom of the screen:** easier for a thumb, but it covers a reel's caption and sits where people swipe.
