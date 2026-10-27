# Ranking across apps: what a message asks, who it's from, and next up

Pranav asked how Earmark decides between two people writing at once, say Rahul on WhatsApp and Zoya on Instagram. In v0.1.11 it barely decided at all:

- **The app made no difference.** Both became Reply loops with the same score formula. With no reply history, the newer message won unless the older one had a question mark or a time word.
- **`due` read only "?" and time words.** "call me, it's urgent" scored the same as "that was fun", and "ok 👍" was owed just as much as a question.
- **During use, the newest arrival won.** A second message replaced the first as the guess, even when the first was urgent.
- **Nothing came after the guess.** Once you replied to Rahul, Zoya's DM was no longer watched, even if you then drifted into Reels.

Pranav chose from options: show the strongest, then the next one once you deal with it; and learn each app's weight from how you treat its messages, rather than a fixed order or no app weight at all.

**What a message asks (`Urgency`).** Earmark reads every message they sent since your last one, which chat apps keep in the notification until you open the chat:

- **Base:** a message to you is half due, as ADR 0017 set.
- **Asks:** a question mark in any script, or a request ("can you", "please", "let me know", "send me", "batao", "bhej do"), adds a quarter.
- **Time:** a time word adds a quarter.
- **Keeps writing:** three messages within 10 minutes, in a direct chat, adds a quarter.
- **Urgent:** words such as "urgent", "asap", "emergency", "hospital", "pick up your phone", "where are you", "jaldi", "kahan ho" or "तुरंत" make it fully due and mark the loop urgent.
- **Only acknowledgements:** if every message is "ok", "thanks", laughter, emoji, a sticker or a reaction, nothing is owed, so no loop opens ("nothing to answer"). If the app reposts a chat that already has an open loop, that loop and its way back stay.

The word lists are English, Hinglish and Hindi. They are rules, not a model. The Galaxy A34 has no on-device language model, rules run the same on every phone, and the log can name the rule that fired. No text leaves the phone or reaches the log: the "opened" line lists only the reasons, such as `due 1.00: urgent, asks, time`.

**Who it's from, across apps (`Affinity`).** `who` comes from the best evidence there is, in this order:

1. **Replies:** your replies to this conversation, as before.
2. **Opens:** with three or more taps or swipes on its notifications, 0.3 + 0.4 × the share you opened (ADR 0028).
3. **The app:** once five of its messages have closed, 0.3 + 0.2 × the share you dealt with + 0.2 × e^(−usual minutes ÷ 60). An app whose messages you open within minutes reaches about 0.66. An app you mostly leave unanswered, and only reach hours later, falls to about 0.34.
4. **Nothing:** 0.5.

A group gets 0.8 × that unless it has history of its own. The log says where `who` came from, as in `who 0.66 from app`. Each closed Reply loop updates its app's counts: done adds one and moves the running median of how soon; swiped away or expired adds one to left. Demo mode and Earmark's own notifications teach nothing.

**Arrivals compete on score.** A message that arrives during use replaces an earlier arrival, or a next up, only if it scores higher now. An urgent one is the exception: it takes over a guess you came for, unless that guess is urgent too. The guess it took over comes back once the urgent one closes, as a detour does (ADR 0020).

**Next up.** When the guess closes, as done or dismissed, with no detour to go back to and the phone still in use, the strongest loop still waiting becomes the guess. It needs the same score a chip needs (above 0.70) and gets no chip. Its pill reads "Next up · Reply to Zoya · Do it now". Nothing is lined up after an expiry, after the screen goes off, or when you move from one open chat to another.

**A feed app is never a loop's own app.** Instagram is both a feed and where an Instagram DM lives, so before this change, scrolling Instagram never counted as drift for a DM there. Now it does, until you open the chat and Instagram clears the notification (ADR 0019).

**Consequences:**

- Urgent messages and requests outrank chatter, whichever app they came on, and "ok" no longer opens a loop.
- For two plain messages from new people, the app you usually answer sooner wins once Earmark has seen five closed messages there. Until then, the newer message wins, as before.
- Room's `app_stats` gains `done`, `left` and `medianDoneMinutes` (version 4). That is a package name and three numbers, nothing about the messages.
- The word lists will miss some urgent messages and flag some that aren't. A false "urgent" costs one early pill; the three-ignores mute and the one-pill-per-message limit still apply.
