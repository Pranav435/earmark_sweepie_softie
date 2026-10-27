# Urgent group messages, and your name from your contact card

In v0.1.11, a group message opened a loop only if its last message contained "@" followed by the name the chat app uses for you. That almost never matched, so groups were effectively ignored, even "Dad's in the hospital, call me" in the family group.

Pranav chose what makes a group message urgent for him: it mentions him, it uses urgent words, or it's in a group whose notifications he taps often. For his name, he chose to have Earmark read his contact card. Testing on the A34 then showed the card isn't enough on its own (see **Your name**), so names typed in Settings are added to it.

**A group message is a loop when it's for you:**

- **It mentions you:** your name, its first word or a nickname from your contact card or from Settings, as a whole word, with or without "@". WhatsApp writes a mention as "@⁨Pranav Savla⁩", with the name between invisible direction marks, and that counts too. So does the app's own @name for you, such as "@You". The plain word "you" never counts, and nor does a name shorter than two letters.
- **The app says it mentions you:** on the A34, WhatsApp gave a group message that mentioned its owner the sender "📣 Mentioned you", and moved who wrote it to the front of the text ("Riya: @⁨…⁩ see this"). Earmark takes the real sender back out of the text and counts the message as a mention, whether or not it knows your name. Before this, the chip read "Here to reply to 📣 Mentioned you in Sweepie Softie?".
- **It's urgent:** the same word lists as a direct message (ADR 0027).
- **It asks something in a group you usually open:** a question or a request, in a group whose notifications you've opened at least three times and at least 60% of the time (smoothed, so 3 opens and 0 swipes counts, but 3 opens and 2 swipes doesn't).

Everything else in a group is chatter, which opens no loop. A group whose messages are only acknowledgements opens no loop either.

The loop quotes the message that's for you, and is as old as that message. WhatsApp adds the waiting count to a group's name ("Family (2 messages)") and "~ " before the name of someone you haven't saved; both are left out, as are the direction marks and tabs around a mentioned name, which showed on the chip as a gap. The chatter after it isn't someone writing to you again, so it doesn't renew the loop's age, take over as a new arrival, or bring back a pill that already showed (ADR 0021). Its task names the group, "Reply to Mom in Family", and so does the chip: "Here to reply to Mom in Family?". The reply strip names the group as who the replies go to, because a reply in a group goes to everyone in it. A group with no history of its own is owed a little less than a person (`who` × 0.8).

**Learning which groups you open.** A tap on a conversation's notification counts as opened, and a swipe counts as swiped. Two counts are kept under the conversation's salted hash, in `contact_stats`. For chatter, Earmark keeps only the notification's key and the hashed conversation in memory, so it can learn from a tap or swipe. The text and intents are forgotten at once, as for any skipped notification. A tap on a bundle of several chats opens only the app (ADR 0026), so it teaches nothing. The same counts give `who` for a direct chat you open but have never replied to through Earmark.

**Your name.** The new optional permission is `READ_CONTACTS`. `ProfileReader` queries only `ContactsContract.Profile`, your own "Me" card: its given name and nicknames, and its display name when that comes from a name or nickname. An empty card still has a display name, a placeholder, email or number, and that would match the wrong messages. It never queries your other contacts, though Android's permission would allow it. The card is re-read at every unlock, so allowing the permission or editing the card takes effect without a restart, and what it gives stays in memory.

On the A34, a group message mentioning its owner, Dhiraj, was treated as chatter: the phone's Me card had only a four-letter placeholder, with no name in it. Asked what to do, Pranav chose to keep the card and add **Settings › Your name**, a field for the names you go by, comma-separated. Typed names are kept in Earmark's private preferences on the phone (backups are off) and added to the card's. The log shows only how many names there are ("1 names for mentions"), never the names. With neither, only the app's own @name for you counts as a mention.

**Consequences:**

- Group messages addressed to you, or urgent ones, now get chips and pills like direct messages. A group you tap often gets them for questions too.
- `contact_stats` gains `opened` and `swiped` (Room version 4).
- Onboarding gains an optional "Your name" step, which `scripts/setup-phone.sh` grants, and Settings gains a "Your name" field.
- A name that's also a common word, such as "Sunny" or "Hope", will mark some messages as mentions that aren't.
- Group mentions that the app shows as a phone number can't be matched.
