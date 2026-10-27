# Earmark

Earmark guesses the one thing you unlocked your phone to do and, if you drift into a feed, hands that task back in one tap. It never blocks an app and never counts minutes.

## Loops

**Loop**:
Something you may owe, opened from a signal on the phone and closed by evidence that it was dealt with.
_Avoid_: task, todo, item, reminder

**Kind**:
The type of a loop: Reply, Payment, Flow, Routine, Leave-by or Own.
_Avoid_: type, category

**Reply**:
A loop for a person-to-person message that is still unanswered.

**Payment**:
A loop for a request for money that you have not paid yet.

**Flow**:
A loop for something you started in a ride, food or shopping app and left before it was booked or ordered.
_Avoid_: checkout, unfinished task

**Routine**:
A loop for something you usually do at this hour and have not done today, such as setting your weekday alarm.

**Leave-by**:
A loop for an upcoming event with a place or a meeting link that you need to set off for or join.
_Avoid_: calendar loop, event loop

**Own**:
A loop you name yourself. Not in v0.1.

**Way back**:
The exact route back into a loop: the notification's tap action, its reply action, or an app or alarm intent.
_Avoid_: deep link, shortcut, intent

**Evidence**:
A signal that shows a loop was dealt with, such as a sent reply, a new alarm or a "booked" notification.

**Outcome**:
How a loop ended: done, dismissed or expired.

**Carry-over**:
A loop whose pill was swiped away, offered again as a chip at the next unlock.

## The guess

**Guess**:
Earmark's pick, at unlock, of the one loop you most likely came for.
_Avoid_: prediction, suggestion

**Score**:
How strongly a loop is guessed, from 0 to about 1.15; a chip appears only above 0.70.

**Chip**:
The small card that shows the guess right after unlock.
_Avoid_: banner, toast, popup

**Hold**:
Tapping ✓ on a chip to say "yes, this one"; the chip shrinks into the dot.
_Avoid_: confirm, accept

**Fade**:
A chip running out its timer without a tap. The loop is still watched.

**Ignore**:
Swiping a chip away. Three ignores in a row mute that kind.

**Mute**:
The state of a kind that Earmark no longer guesses because you ignored it three times in a row.

**Dot**:
The small marigold mark under the status bar that shows a held loop is being kept.

## Drift and hand-back

**Feed app**:
An app built for endless scrolling or watching, such as Instagram or YouTube.

**Drift**:
Being in a feed app for longer than the drift timer while the guessed loop is still open.
_Avoid_: distraction, doomscroll, relapse

**Pause**:
A natural break in the feed: media paused or stopped, the next reel starting, or an app switch.

**Blocker**:
Something that holds the pill back: a call, active navigation, Do Not Disturb or media still playing.

**Pill**:
The prompt shown at a pause during drift: "You came here to · Reply to Rahul · Do it now".
_Avoid_: nudge, interruption, alert

**Hand-back**:
Opening a loop's way back from the pill.
_Avoid_: redirect, block

**Brought back**:
A loop that closed as done after a hand-back.

**Reply strip**:
Up to three one-tap replies that Earmark shows above the chat's input area.
_Avoid_: quick replies bar, suggestions

## Recap

**Recap**:
Today's list of things and how each one ended.
_Avoid_: report, stats, dashboard

**Thing**:
A loop that Earmark showed as a chip today or that you reached from a pill. The recap counts things.

**Demo mode**:
A setting where Earmark posts its own sample notifications and uses a 10-second drift timer, so every story runs on any phone.
