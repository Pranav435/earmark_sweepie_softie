# Earmark this: Own loops from a Quick Settings tile

Earmark only guesses what reaches the phone as a notification, a calendar event, a habit or an app you left early. If you unlock to look up a train time and end up in Instagram, it can't help: nothing told it why you unlocked. The glossary had the Own kind waiting for a Quick Settings tile in v0.2. Pranav chose the tile over a share target and over leaving it out.

**The tile.** "Earmark this" is a toggle tile, so TalkBack reads it as a switch, on or off. Its subtitle names the app it holds or would hold ("Chrome"). Tapped with an app in front, it opens an Own loop for that app (`Situations.own`): "Get back to Chrome", fully due, with the app's launch intent as its way back and the app's icon on the note. The loop becomes the guess at once, so drift brings it back like any other guess; the guess it took over comes back once it closes. Tapped again, it lets go. The tile is unavailable on the home screen, in a feed app, in Earmark itself, on a locked phone, and while Earmark isn't connected. One Own loop at a time. Settings has "Add the tile", which on Android 13 and later asks in a system dialog; below 13 it says where to add it.

**What it says.** "Chrome · You earmarked it · Open", "Here to get back to it?" on a chip, and "You came here to get back to it" on a pill. It's listed on the home screen as you make it.

**When it closes.** Going back to the app for 20 seconds or more, from the pill or on your own, is doing it, once you leave: it closes as done, and as brought back after a hand-back. The visit you earmarked it in never counts. You earmark something to come back to, and may keep reading for a while before drifting; the first build counted that time and closed the loop before any pill. "Not this" on its chip lets it go and never mutes the kind. It expires after 6 hours.

**Scoring.** An Own loop is owed (ADR 0031) and fades over six hours at every unlock, not only the first after it: you said you'd come back to it, so a chip offers it for about two hours.

Rejected:

- **Share to Earmark:** more precise (a link or some text), but you have to share first, and it needs a screen to show what was shared.
- **Closing on any time spent in the app after the tap:** it closed loops the moment you left them, which is when the tile matters most.
- **Several Own loops at once:** one tile can only say one thing, and a list of earmarks would become a to-do app.
