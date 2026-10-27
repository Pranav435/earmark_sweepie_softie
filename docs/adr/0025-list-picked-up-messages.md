# List picked-up messages as they arrive

Twice, the tester sent messages to the A34, saw nothing on Earmark's home screen, and concluded nothing worked. Earmark had picked up every message, and the Log showed each one becoming the guess. But the home screen listed only loops that had shown a chip or a pill. A message that arrives while the phone is in use gets no chip (ADR 0020), and gets a pill only after a drift into a feed. In the last test no feed was opened, so there was nothing to list. The wording fix in v0.1.9 didn't help. Asked again, the tester chose to list picked-up messages and to keep pills for feed apps only.

Each Reply or Payment loop now goes on today's list the moment it opens, marked as waiting, and is updated with its outcome when it closes: done, dismissed or expired, or brought back. A second message in the same chat is the same row. Flows, alarm routines and leave-by loops still join the list only when they show a chip or pill, as before, because they open on their own and aren't something that came in.

The engine decides what is listed (`LoopEntry.listed`), and the store keeps it in a new `listed` column. Room moves to version 3 with a migration that adds the column and marks every row that was already on the list. The list's headline counts things ("2 things today"), no longer "You came for…", because a message that came in isn't always what you came for. A swiped chip for a message stays on the list, still waiting, because the message is still unanswered.

The label, such as "Reply to Avani", is now stored for every picked-up message, not only for those that got a chip or pill. It is still cleared after 24 hours, and message text is never stored.
