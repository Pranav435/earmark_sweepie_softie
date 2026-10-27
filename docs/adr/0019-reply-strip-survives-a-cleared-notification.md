# The reply strip stays when a chat app clears its notification

WhatsApp and Google Messages clear a chat's notification the moment the chat opens. Earmark treated that as evidence that the Reply loop was done. It closed the loop, hid the reply strip and dropped the notification's reply action and message text. So after a hand-back into WhatsApp, the suggested replies never appeared.

Now, when the guessed Reply loop's notification is removed because you opened its chat, the loop stays open in a "chat open" state. The removal counts as opening the chat in three cases:

- after a hand-back from the chip or pill, until you leave the chat app;
- after a tap on the notification itself;
- when its app clears it while that app is in front, or comes to the front within 3 seconds. Chat apps clear it the moment they open, often before the once-a-second foreground check has seen them.

The loop keeps its reply action and message text, so the strip can show and send. If you opened the chat yourself, Earmark asks for replies at that point too. The loop closes as done when you send a reply, leave the chat app, or turn the screen off. If the app clears the notification while you are somewhere else, for example because you read it on another device, the loop closes as done once those 3 seconds pass.

A cleared notification doesn't say which chat is open. WhatsApp clears every notification when it opens to the chat list, and the strip stays while you move between chats in the app. So the strip names who the replies go to, on screen and in each send button's TalkBack label.

The listener no longer forgets a removed notification's intents on its own. The engine emits `Forget` for every removal it isn't holding on to, so nothing outlives its loop (ADR 0004). When an app removes a group summary, Android removes its children with `REASON_GROUP_SUMMARY_CANCELED`; the listener gives each child the reason the summary went with.

**Consequences:** sending relies on the cleared notification's reply `PendingIntent` still being valid. Chat apps rarely cancel those, and the demo chat, which now clears its notification on open like WhatsApp, shows that the send works. If an app does cancel it, the send fails, the strip hides and the failure is logged.
