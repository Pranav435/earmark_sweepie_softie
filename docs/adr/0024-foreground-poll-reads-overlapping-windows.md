# The foreground poll reads overlapping windows

The tester's A34 got a WhatsApp message while the phone was in use, and it became the guess. Then 34 seconds in Instagram went unnoticed: the log had no drift line when Instagram came to the front, and no learned visit when it left. Earmark's poll was running, because the same message had been marked "came in during use", which needs the poll's ticks. The system's usage log recorded the switch to Instagram. Across 19 later switches on the same phone, every one was seen, so the miss didn't reproduce.

The poll read `ACTIVITY_RESUMED` events in back-to-back windows: from the previous poll to now. That loses any switch whose timestamp falls inside a window the poll has already read. Two things can cause that. The system records a switch a moment after it happens. It also stamps it from a clock snapshot that it corrects only once it is more than 2 seconds off, so a switch can carry a time a little in the past. Either one can put a switch behind the poll. This is the likely cause of the miss, but it wasn't proven.

Each poll now reads from 10 seconds back to 5 seconds ahead, and acts on the newest switch in that window. Events come in time order, and a switch the poll has already acted on is the same app, so nothing is reported twice. The first poll after an unlock still looks back an hour, to find the app already in front.

Each switch is now logged with how late it was seen: "in front: com.instagram.android, seen 799 ms after its switch". On the A34, switches were seen 0.3 to 0.8 seconds after their time. A large or negative number in a tester's log would point back here.
