# A message to you is half due, and the first unlock after it gets the boost

Changes the starting weights from architecture.md. On the first real-phone test (a Samsung Galaxy A34), a tester sent the phone a WhatsApp message and no chip appeared. With the v0.1 weights, a message with no question mark and no time word, such as "hey", had `due` 0, so its best possible score was 0.35 + 0.125 + 0 + 0.075 + 0.15 = 0.70. The chip needs more than 0.70, so a message like that could never show one. A question only cleared the bar if the phone was unlocked within 60 seconds, or within about 10 minutes if it also had a time word. People usually pick up a phone some minutes after it buzzes, so most real messages fell below the bar.

Two changes:

- **`due` for a Reply starts at 0.5.** Someone writing to you directly, or mentioning you in a group, is owed a reply even without a question. A question mark adds 0.25, and a time word adds 0.25, so "free at 5?" is still fully due.
- **The +0.15 boost goes to the first unlock after the message or request arrives**, however long after, instead of to unlocks within 60 seconds of it. The engine remembers the previous unlock. A loop updated since then gets the boost; if the engine hasn't seen an unlock since it started, every loop does.

With no reply history, at the first unlock after it arrives a plain message chips if it is under about 13 minutes old, a question under about 23 minutes, and a question with a time word under about 37 minutes. At later unlocks only questions chip again: within about 3 minutes, or 10 with a time word. A plain message never chips twice, so an ignored "hey" doesn't come back at every unlock. Old chatter stays below the bar ("haha nice" from two hours ago scores 0.48).

**Consequences:** more chips for plain messages, which the three-ignores mute still limits per kind. `recent` still uses the message's own timestamp, so a message delivered late (the phone was offline) counts as old.
