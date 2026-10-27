# The pill comes right away

Changes ADR 0023's default and drops the 10-minute gap from architecture.md. On the Galaxy A34 the tester opened Instagram with an email loop as the guess, and the pill came 3 min 15 s later. The smart timer, still learning, waited 34 seconds (45 × 0.75 for a question). A pill for another loop had shown 6 min 45 s earlier, so the 10-minute gap held this one for another 160 seconds. The tester said the pill should work out as soon as they're scrolling a feed, and show then.

**Right away is the new default.** Drift starts once a feed app has been in front for 3 seconds, the same time that tells a visit from passing through on the way somewhere else (`SmartDrift.shortestVisit`). The pause rule is unchanged: with nothing playing, as in Instagram on the A34, the pill shows when drift starts. With media playing, it waits for the next reel or short, a pause or an app switch, so scrolling to the next reel brings it. The pill comes about 3 to 4 seconds after the feed opens, since the foreground poll sees the switch up to a second late.

Smart and the fixed times stay in Settings. The choice is stored under a new key, so updating turns Right away on even for someone who picked Smart or a fixed time. Demo mode uses Right away instead of its 10 seconds. Usual visits are still learned for Smart, but a pill now cuts most visits with a guess short, so they're learned less often.

**No gap between pills.** These limits on how often pills show stay: one pill per message (ADR 0021), a swiped pill keeps Earmark quiet until the next unlock, and three ignores mute a kind. Back-to-back pills only come from a next up (ADR 0027) or a new arrival after a hand-back, which are the pills those features exist for.

The decision log says "pill after 3 s (right away)" when a feed comes to the front with a guess open.

Rejected:

- **Detecting scrolling itself:** only an accessibility service sees scroll events. It needs a setting that Android restricts for sideloaded apps, and a much broader grant than Earmark's other permissions. Three seconds in a feed app is close enough to "scrolling".
- **Showing the pill the moment a feed app opens:** an app switch through Instagram, or opening it for a second, would get a pill.
