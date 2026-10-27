# Mute the chat, not the kind

Amends the mute rule in architecture.md ("3 ignores in a row mute that kind"). Three "not this" in a row on any reply chips muted Reply as a whole: no chips, no pills and no arrivals for any message, from anyone. Those three could be three different people, each waved off because you'd unlocked for something else. Earmark then looked broken, with the only way back under Settings › Muted guesses. Asked what should change, Pranav chose to mute the chat instead.

**The rule.** For loops with a person at the other end (`LoopKind.withPeople`: Reply, Call back, Email), "not this" on a chip adds one to that conversation's `ContactStat.ignoreStreak`, under its hashed key. Three in a row mute just that chat: it gets no chip and no pill, and doesn't become the guess or next up. Its messages are still listed on the home screen, where a tap still opens them. Saying yes to that chat's chip, or to the held guess on the running notification (ADR 0034), starts its count again. The kind's history still learns every "not this", so `history` in the score works as before.

Loops with nobody behind them (Payment, Flow, Routine, Leave-by, Reminder) mute their kind as before. A Payment from a person has no conversation key, so it stays with its kind. Own loops never mute (ADR 0035). A person kind's old streak in `kind_stats` no longer counts, so updating unmutes replies, missed calls and emails that an older version had muted.

**Storage.** `contact_stats` gets an `ignoreStreak` column; Room goes to version 5 with `MUTE_CHATS`. Settings › Muted guesses shows "2 chats" with one Unmute (`ChatsUnmuted`). Only salted hashes are stored, so it can't say whose chats they are, and Earmark doesn't keep names to say it.

Rejected:

- **Ask on the third swipe ("Stop guessing replies?"):** it keeps a rule that is too coarse, and puts a question on a card meant for a glance.
- **Mute by sender for bills too:** a bill's sender is a business, and the choice was offered for kinds with a person behind them.
- **List muted chats by name:** it would mean keeping names, which Earmark never stores.
