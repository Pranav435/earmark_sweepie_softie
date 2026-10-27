# Missed calls, reminders, emails and bills

Up to v0.1.12, the loops a tester saw were nearly all replies. Payment, Flow, Routine and Leave-by need a payment request, a ride or food app left early, a weeknight bedtime or a calendar event with a place, so a phone used mostly for chat only ever showed "Reply to …". Pranav asked what else people unlock the phone to do and lose to a feed, and chose four that the notifications Earmark already receives can show: a missed call, a reminder you set, an email that asks you something, and a bill that's due.

Two things were wrong before this, not just missing. Google's dialer posts a missed call as a MessagingStyle chat from the caller whose message reads "Missed call", so v0.1.12 opened "Reply to 555-1234" for it (seen on the API 36 emulator). And a text from a business sender ID such as AX-AIRTEL or JM-HDFCBK went down the same path, so a bank alert could become "Reply to JM-HDFCBK".

**Call back** (`LoopKind.CallBack`): "Call Mom back".

- A notification in the `missed_call` category, or one whose title, text or last message is a whole "Missed call" line ("Missed voice call", "2 missed calls", "Missed call from Mom"), so WhatsApp, Telegram and Samsung's dialer count without the category. A chat message such as "sorry I missed your call" doesn't. This check runs before messages are classified.
- The caller is the chat's sender, the line that isn't "Missed call", or the name after "from". A count such as "(2)" is left off the name.
- Three quarters due: they wanted you then. Someone who called twice or more is urgent and fully due.
- Do it now replays the notification's tap action, which opens the call log or chat. It doesn't use the notification's Call back button, because a pill is tapped mid-scroll, and a stray tap that rings someone can't be taken back. Calling is one more tap from there.
- It closes when the dialer clears the notification. Google's dialer clears every missed call once you call anyone back or look at Recents, so all of them close as done together.

**Reminder** (`LoopKind.Reminder`): the reminder's own words are the task, "Submit lab report".

- A notification in the `reminder` or `event` category from any app, or any uncategorised notification from a reminder or to-do app: Keep, Google Tasks, Samsung Reminder, Microsoft To Do, TickTick, Todoist and Any.do. A notification from those apps in another category ("Rahul shared a note") isn't a reminder.
- A title that only says "Reminder", "Upcoming" or "Tasks" gives way to the text.
- Fully due as it fires: you set it for when you meant to do it. The chip is the reminder itself, "Submit lab report?", over "Reminder in Keep · just now".
- A calendar's notification for an event Earmark already holds as a Leave-by opens nothing more. The Leave-by says where and when to leave. Without calendar access there is no Leave-by, so the calendar's own notification becomes a reminder.
- Tapping it or marking it done closes it as done, and swiping it away dismisses it. Snoozing also closes it as done, and it opens again when it fires again.

**Email** (`LoopKind.Email`): "Reply to Prof. Mehta's email".

- A notification in the `email` category (Gmail and Outlook both use it; on the A34, Outlook posts the sender as the title and the subject on the text's first line, in big text).
- Only an email that asks something (a question mark or a request such as "can you") or is urgent, by the word lists from ADR 0027. A time on its own isn't enough, because newsletters are full of "today" and "tonight". A sender name with "noreply", "notifications", "newsletter", "alerts", "support", "team" or "updates" in it is skipped. One-time codes are skipped as everywhere else.
- Its own kind rather than a Reply, so three swiped email chips mute email and not chats. The chip quotes the subject: "Here to reply to Prof. Mehta's email?" over "“Lab report” · 5 min ago". It waits 24 hours, not 12, since email is answered more slowly.
- Opening the email in its app clears the notification and closes the loop as done. Earmark can't see whether you replied.

**Bill** (a `Payment` with `bill = true`): "Pay your Airtel postpaid bill ₹499".

- An amount, a bill word (bill, statement, EMI, premium, dues, rent, invoice) and a due phrase ("is due", "due on", "overdue", "pay by", "last date", "amount due"). A receipt for one ("received", "successful", "thank you for your payment", "has been paid") isn't a bill. "Ignore if already paid" doesn't count as a receipt, because the bill may still be owed.
- The biller comes from "your … bill", else the sender ID without its prefix (AX-AIRTEL → AIRTEL), else the app's name. The detail is the amount and the due date when the text gives one: "₹499.00 · due 30-Oct".
- Three quarters due, or fully due when it says today, tomorrow, tonight, overdue or immediately. It stays a Payment, so it closes like a payment request, and Pay now is its verb.

**Texts from a business are never replies.** A sender that is an Indian sender ID (two letters, a dash, then 3–9 letters or digits, sometimes with a one-letter suffix) or a 3–6 digit short code opens a bill or nothing: "text from a business" in the log. Google Messages can show a verified business by its brand name instead, and that still reads as a person.

**Shared.** All four are listed on today's list as they arrive (ADR 0025) and get the first-unlock boost, like messages and requests (`LoopKind.cameIn`). How you deal with calls and emails teaches Earmark about their app, as messages do (ADR 0027). The listener now passes on a notification's big text, and keys an email or a non-chat missed call by its sender, so a second email from the same person updates one loop.

**Consequences:**

- On the emulator, a real missed call (`adb emu gsm call` then `cancel`) during YouTube became the guess at 0.89 and its pill read "Before you forget · Call 555-9876 back · Do it now". Do it now opened Recents, and calling back closed it as done and brought back. A texted Airtel bill opened "Pay your Airtel postpaid bill ₹499", and a bank debit alert opened nothing.
- On the A34, the Outlook email waiting in the shade opened an Email loop when v0.1.13 connected (it asks something and names a time). Nobody read it to check that it really wants a reply.
- Reminders and the email chip run only in core tests: the adb shell can't post a notification with a category.
- Bill wording and the business sender pattern are English and Indian; elsewhere bills arrive mostly by email or app, where only the wording rule applies.
- A missed call that was waiting when v0.1.13 was installed was already on today's list as "Reply to …"; that row stays waiting until midnight, next to its new "Call … back" row.
