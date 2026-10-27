# Quick replies when Smart Reply can't answer, and taps on a group of chats

The tester sent WhatsApp messages to the A34, and no suggested replies appeared. The decision log had the reason: "smart reply: 0 suggestions (not English)". ML Kit's Smart Reply only answers English, and it calls very short messages such as "hi", "ok" or "lol" not English, because it can't tell what language they are. Short messages are exactly what people send when they test, and what friends often send. The strip simply didn't show.

When Smart Reply answers "not English", fails, or can't run, the strip now offers three quick replies. `QuickReplies` in the core works them out from the message's words alone:

- **A question with a time** ("free at 5?"): Works for me / Can't, sorry / Let me check.
- **Another question** (a question mark in any script, or a question word at the start): Yes / No / Let me check.
- **A greeting**: Hey! / Hi! What's up? / Give me a minute.
- **Thanks**: You're welcome! / Anytime / Of course.
- **Anything else**: Got it / Will reply soon / Can I call you?, which suit any message, bad news included.

When Smart Reply says it has no confident reply, the strip still stays hidden. That answer is how ML Kit holds back on sensitive topics, and a quick "👍" under bad news would be worse than nothing. The log says which happened: "smart reply: 0 suggestions (not English); quick replies instead".

Testing the strip on the emulator turned up a second fault. Tapping a collapsed group of chat notifications removes the group's summary as a click, and Android then removes every chat in the group, saying only that its summary went. Since v0.1.3, each chat took the summary's reason, so a tap on the group looked like a tap on every chat in it. Earmark "opened" each chat in turn, marked each done ("moved to another chat") and showed a reply strip for whichever came last, while the app was on its chat list. WhatsApp groups its notifications the same way on the A34.

`GroupRemovals` in the core now counts the chats showing in each group. A tap on a group of several chats only opens the app, so those chats count as cleared by their app. The guessed chat then waits up to 3 seconds for its app to come to the front, and opens with its strip; the others close as done without a strip. A tap on a group with one chat is a tap on that chat. A group cleared by its app, or swiped away, still passes that reason on, as before.

Checked on the emulator with real SMS through Google Messages: "yo" got a strip of quick replies, a tap on a group of three chats opened only the guessed one with its strip, and a tap on a single chat still opened it. On the A34, a real WhatsApp "hi" opened from its notification got the quick-reply strip, drawn above the message box.
