# A smart drift timer

The drift timer was one number for everything, 90 seconds by default. On the Galaxy A34 the tester's visits to Instagram were 10 to 16 seconds long, so no pill ever came, and 90 seconds was too long anyway. The tester asked for a timer that is smart about how the phone is used and what is waiting. They chose this design over two others, and asked for the fixed times to stay as overrides.

Smart is now the default. When a guessed loop is open and a feed app comes to the front, the timer is:

- **Your usual visit to that feed app.** Every visit to a feed app teaches a running median of how long you stay, kept as one number per app: the package name, the number of visits and the median. Until five visits have been seen, it is 45 seconds. The median moves at most 10 seconds a visit, and visits count up to 10 minutes, so one long session doesn't drag it.
- **Halved for something urgent** (a due of 1.0: a question with a time such as "free at 5?", a money request, a leave-by that's close). **Three quarters for something due soon** (a question, or a time word).
- **Three quarters again if you held its chip.** ✓ says this is what you came for.
- **Kept between 15 seconds and 2 minutes.**

A plain "hey" waits 45 seconds while Earmark is learning, and "free at 5?" waits 23. If you usually stay 20 seconds in Instagram, a plain message comes back after 20 seconds.

Some visits don't count. A visit shorter than 3 seconds is passing through. A visit a pill cut short would teach the timer to be shorter still. Visits in demo mode or while Earmark is paused aren't yours to learn from. Demo mode keeps its fixed 10 seconds, so every story runs the same on any phone.

The decision log shows the timer and why when a feed comes to the front with a guess open, for example "pill after 23 s (usual visit 45 s, still learning · urgent ×0.5)". It also shows each learned visit: "usual visit to com.instagram.android: 40 s after 6 visits". Neither line carries message text.

The usual visits are stored in a new Room table, `app_stats`, so the database moves to version 2 with a migration that only adds that table. Picking a fixed time in Settings turns Smart off. Updating to this version turns Smart on, even for someone who had picked a fixed time before.

Rejected:

- **Urgency rules with no learning:** easy to predict, but the same 45 seconds for someone who checks Instagram for 10 seconds and someone who stays for 5 minutes.
- **Learning from pills instead** (shorter when you tap Do it now, longer when you swipe the pill away): it learns whether the pill helps, not how you use the feed. Pill outcomes could tune the timer later, on top of this.
