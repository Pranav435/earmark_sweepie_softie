# Details for Routine and Leave-by loops beyond architecture.md

These are small choices the spec left open, recorded so they aren't "fixed" back by accident:

- `recent` is 1.0 for Routine and Leave-by loops, because they are about now, not about when Earmark noticed them. The +0.15 unlock boost only applies to notification loops (Reply and Payment), where "unlocked right after it arrived" means something.
- A Leave-by loop opens when its leave-by time is within 2 h. That time is the start minus 30 min, minus 150 min for a flight or airport, or minus 5 min for a meeting link. The calendar is read 4 h ahead so a morning flight is caught in time.
- With no ride app installed, the way back for an event with a place is a `geo:` Maps search ("Directions"), not nothing.
- Opening a meeting app closes a meeting Leave-by loop, because joining is the evidence.
- Payment `due` is 1.0 from the start: a direct request for money is owed now, and request age is already part of `recent`.
- Closed Routine and Leave-by loops are remembered for the session, so the next unlock or calendar read doesn't reopen them.
- Reply statistics (`who`) only count real replies (your own message in the conversation, or a reply sent from the reply strip), not a tap on the notification.
