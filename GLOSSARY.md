# Earmark

Earmark guesses the one thing you unlocked your phone to do and, if you drift into a feed, hands that task back in one tap. It never blocks an app and never counts minutes.

## Loops

**Loop**:
Something you may owe, opened from a signal on the phone and closed by evidence that it was dealt with.
_Avoid_: task, todo, item, reminder

**Kind**:
The type of a loop: Reply, Call back, Email, Reminder, Payment, Flow, Routine, Leave-by or Own.
_Avoid_: type, category

**Reply**:
A loop for a person-to-person message that is still unanswered.

**Call back**:
A loop for a missed call from someone you haven't called back.
_Avoid_: missed call loop

**Email**:
A loop for an email from a person that asks you something or is urgent, still unanswered.

**Reminder**:
A loop for a reminder you set yourself, in a reminder, to-do or calendar app, from when it goes off until you mark it done or dismiss it.
_Avoid_: alert, task

**Payment**:
A loop for a request for money that you have not paid yet, or a bill that is due.

**Bill**:
A Payment for an amount a business says is due, from a text or an app: "Pay your Airtel bill ₹499".

**Text from a business**:
A text from a sender ID such as AX-AIRTEL or a short code. It is never a Reply; it can be a bill.

**Flow**:
A loop for something you started in a ride, food or shopping app and left before it was booked or ordered.
_Avoid_: checkout, unfinished task

**Routine**:
A loop for something you usually do at this hour and have not done today, such as setting your weekday alarm.

**Leave-by**:
A loop for an upcoming event with a place or a meeting link that you need to set off for or join.
_Avoid_: calendar loop, event loop

**Own**:
A loop you make with the "Earmark this" tile: the app in front, held so that drift brings you back to it. It closes once you've gone back to that app for a while and left, or when you tap the tile again.

**Earmark this**:
The Quick Settings tile that makes an Own loop from the app in front, or lets go of the one it holds.
_Avoid_: bookmark, save for later

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
How strongly a loop is guessed, from 0 to about 1.15; a chip appears only above 0.70. It weighs how recent the loop is, who it's with, how due it is and how often you said yes to its kind.

**Arrival**:
A message or request that comes in while you are using the phone. Its heads-up already showed, so it gets no chip, but it can quietly become the guess.
_Avoid_: mid-session message

**Urgent**:
A message that says it can't wait, such as "call me asap", "hospital" or "jaldi", unless it says it isn't ("koi jaldi nahi"), is over, or is a joke. It is fully due; at unlock an urgent loop above the bar comes first, and as an arrival it can take over a guess you came for, which comes back after.
_Avoid_: important, priority

**Owed**:
A loop still owed hours later: a call back, a payment or bill, a reminder, or a message or email that asks or is urgent. At the first unlock after it came in it fades over six hours instead of half an hour. Chatter is never owed.

**Message model**:
The small text model that reads each message beside the word lists, for urgent, asks and acknowledgement. It learned from labelled messages, runs on the phone, and only overrules the lists when it is sure.
_Avoid_: AI, classifier

**Acknowledgement**:
A message that only answers or reacts, such as "ok", "thanks", 👍 or a reaction. It owes nothing, so it opens no loop.

**Next up**:
The loop that quietly becomes the guess once you've dealt with the one before it, while you're still using the phone. It gets no chip.

**For you**:
A group message that mentions you, is urgent, or asks something in a group whose notifications you usually open. Only these open loops.
_Avoid_: relevant

**Chatter**:
Group messages that aren't for you. They open no loop; only a tap or swipe on their notification is learned.

**Mention**:
Your name, first name or a nickname from your own contact card in a group message, in Latin letters or an Indian script ("प्रणव"), or the app's @name for you.

**Note**:
How the chip, the pill and a waiting row look: a paper card, as from a friend, with who it's from (their photo or initial, or an app's icon), their own words, why it's here, and one verb such as Reply or Pay.
_Avoid_: card, bubble

**Gist**:
The few words of a message a note quotes: all of a short one, or from a long one the sentence that is urgent or asks.
_Avoid_: summary, preview

**Chip**:
The note that shows the guess right after unlock: "Rahul · “free at 5?” · Here to reply? · Reply". Its one button hands the loop back and says "yes, this one".
_Avoid_: banner, toast, popup

**Fade**:
A chip running out its timer without a tap. The loop is still watched, and the dot shows while it stays the guess.

**Ignore**:
Swiping a chip away, "not this". Three in a row mute that chat, or the kind when nobody is behind it.

**Mute**:
The state of a chat, or of a kind with nobody behind it, that Earmark no longer guesses because you said "not this" to it three times in a row. A muted chat is still listed as waiting.

**Dot**:
The small marigold mark under the status bar that shows Earmark is holding a guess whose chip faded.

**Held guess**:
The guess Earmark is holding this session, until it's handed back, waved off, or the phone is put down. The "Earmark is on" notification names it, with its verb, so it can be read and acted on without seeing the dot.

## Drift and hand-back

**Feed app**:
An app built for endless scrolling or watching, such as Instagram or YouTube.

**Drift**:
Being in a feed app for longer than the drift timer while the guessed loop is still open.
_Avoid_: distraction, doomscroll, relapse

**Drift timer**:
How long a feed app can be in front before it counts as drift. Right away by default: 3 seconds, long enough to tell you're not just passing through. Smart, in Settings, is your usual visit to that feed app, sooner when the loop is urgent or due soon, between 15 seconds and 2 minutes. A fixed time can be picked in Settings too.

**Usual visit**:
How long you usually stay in a feed app, learned on the phone from your visits, for the Smart drift timer. A visit a pill cut short doesn't count.

**Pause**:
A natural break in the feed: media paused or stopped, the next reel starting, or an app switch.

**Blocker**:
Something that holds the pill back: a call, active navigation, Do Not Disturb or media still playing.

**Pill**:
The note shown at a pause during drift: "Rahul · “free at 5?” · You came here to reply · Reply". For an arrival its small line reads "Before you forget", and for a next up "Next up".
_Avoid_: nudge, interruption, alert

**Hand-back**:
Opening a loop's way back from the pill.
_Avoid_: redirect, block

**Brought back**:
A loop that closed as done after a hand-back.

**Reply strip**:
Up to three one-tap replies that Earmark shows above the chat's input area, labelled with who they go to.
_Avoid_: quick replies bar, suggestions

## Recap

**Waiting**:
An open loop on the home screen, in the order Earmark would guess it. Tapping it opens its way back; pressing and holding it marks it already done.

**Already done**:
Closing a waiting row by hand, for something dealt with where Earmark can't see. Done, not brought back.

**Recap**:
Today's list of things and how each one ended. The home screen leads with who's waiting and folds the rest into "Earlier today".
_Avoid_: report, stats, dashboard

**Thing**:
A loop on today's recap: a message or request Earmark picked up today, listed as it arrives, or a loop it showed as a chip or pill. The recap counts things.

**Demo mode**:
A setting where Earmark posts its own sample notifications and uses a 10-second drift timer, so every story runs on any phone.
