# Labelling guide for the Earmark benchmark

This guide says what each benchmark case looks like and what the right answer is. Label every case by what is right for the person who owns the phone, not by what you think a program would decide. Some cases should be hard. That's the point: the benchmark is meant to find where Earmark is wrong.

## The phone and its owner

- The phone belongs to Pranav Savla, a student in Mumbai. His contact card says "Pranav Savla"; friends call him Pranav.
- He writes and receives English, Hinglish (Hindi in Latin letters, such as "kal milte hain") and some Hindi in Devanagari. A few friends and relatives write in Marathi, Gujarati or Tamil, and some friends abroad write in other languages.
- It's an Android phone. His apps include WhatsApp, Google Messages (SMS), Gmail, Outlook, Instagram, Telegram, Slack, Google Pay, PhonePe, Paytm, Venmo, PayPal, the Google and Samsung phone apps, Google Keep, Google Tasks, Samsung Reminder, Microsoft To Do, Google Calendar, Uber, Ola, Rapido, Swiggy, Zomato, Amazon, Flipkart, his bank's app, YouTube, Spotify and Google Maps.

## What Earmark is for

Earmark watches notifications for loops: things the person may owe, such as a message to answer or money to pay back. When he unlocks his phone, it guesses the one loop he most likely came to deal with and shows it as a small card (the chip). Most notifications are not loops: codes, adverts, receipts, news, chatter that isn't for him.

## Labels for one notification

Each notification gets exactly one label.

| Label | Use it when |
|---|---|
| `reply` | A person wrote to him and the conversation is waiting on him: a direct chat, or a group message meant for him. |
| `callback` | He missed a call from someone. |
| `email` | An email from a person that asks him something, asks him to do something, or is urgent. |
| `reminder` | A reminder, to-do or calendar alert he set up himself is going off. |
| `payment_request` | A person asks him to pay them money, directly or through a payment app. |
| `bill` | A company says a bill, EMI, premium, rent or other amount is due from him and not yet paid. |
| `paid` | Confirmation that he just paid or sent someone money. |
| `booked` | Confirmation that a ride, food order, purchase or booking he made just went through: placed, confirmed, driver assigned. |
| `replied` | A chat notification whose latest message is his own, for example a reply he sent from his laptop. |
| `none` | Anything else: one-time codes, adverts and offers, newsletters, receipts, delivery tracking, news, app alerts, bots, "ok" or "thanks" or a 👍, texts from businesses that aren't bills, and group chatter that isn't for him. |

Details that decide close calls:

- **Groups.** A group message is for him only when it names him (Pranav, Pranav Savla, @Pranav) or the app marks it as mentioning him, or it's an emergency anyone would answer. A question to the whole group that doesn't name him ("who's coming tonight?") is `none`: in this benchmark Earmark knows nothing yet about which groups he reads.
- **Nothing to answer.** If everything waiting from a person is an acknowledgement, a reaction, laughter or an emoji, it's `none`.
- **Businesses.** A text from a bank, telco, shop or service (sender IDs such as `AX-HDFCBK` or `VM-AIRTEL`, or short codes such as `56767`) is never `reply`. It can be a `bill`.
- **Emails.** A newsletter, receipt, notification, invitation to a webinar or "FYI" email is `none`, even when it ends with a question in marketing copy. An email from a no-reply or team address is `none`.
- **Bills.** A bill that has already been paid, a statement that says nothing is due, or a payment receipt is `none` (or `paid` if it confirms he just paid someone).
- **Payment requests.** A request through a payment app ("Aisha requested ₹500") or a friend's chat message asking for money: the chat message is still a `reply` (he owes an answer), the payment-app request is a `payment_request`.

When the label is `reply`, `callback`, `email` or `payment_request`, also give `who`: the name the chip should show, as the notification shows it (for example "Rahul", "Mom", "Prof. Mehta", "Aisha"). When it's `payment_request` or `bill`, also give `amount`: the amount owed, as a currency code (`INR`, `USD`, `EUR`, `GBP`) and a number.

## How a notification is written down

Write each notification the way Android hands it to a notification listener. Use what you know of how each real app fills its notifications. Fields left out are null, empty or false.

```json
{
  "app": "com.whatsapp",
  "appLabel": "WhatsApp",
  "category": "msg",
  "title": "Rahul",
  "text": "free at 5?",
  "bigText": null,
  "messages": [
    { "sender": "Rahul", "text": "did you see the match??", "minutesAgo": 1200 },
    { "sender": null, "text": "that last over was unreal", "minutesAgo": 1180, "fromUser": true },
    { "sender": "Rahul", "text": "free at 5?", "minutesAgo": 0 }
  ],
  "isGroup": false,
  "groupName": null,
  "selfName": "You",
  "hasReplyAction": true,
  "isOngoing": false,
  "isGroupSummary": false
}
```

- `app` is the package name, `appLabel` the name the phone shows for the app.
- `category` is Android's notification category, set by the app: `msg`, `email`, `missed_call`, `reminder`, `event`, `call`, `promo`, `social`, `transport`, `status`, `progress`, `alarm`, `navigation`, `recommendation`, or null. Many apps leave it null.
- `title` and `text` are the two lines the notification shows. `bigText` is the expanded text, when the app gives one (Gmail and Outlook put the subject and the start of the body there).
- `messages` is for chat-style notifications (WhatsApp, Messages, Telegram, Instagram DMs, Slack, and Google's phone app for missed calls). It holds up to the last 10 messages the app keeps in the notification, oldest first. `sender` is who wrote it as the app names them, or null when `fromUser` is true. `fromUser` marks his own messages. `isBot` marks a message the app says came from a bot. `mentionsYou` marks a message the app itself flags as mentioning him. `minutesAgo` is how long before the notification was posted the message was sent.
- `isGroup` and `groupName` are for group chats. `selfName` is how the chat app names him in the conversation, for example "You" in WhatsApp.
- `hasReplyAction` is true when the notification has an inline reply box. `isOngoing` is true for things such as music playing, navigation or a download in progress. `isGroupSummary` is true for the summary notification an app posts over a bundle of others.

## The files

Every file is a JSON array. Every case has an `id`, a `difficulty` (`everyday` for the kind of case a phone sees all the time, `tricky` for a case built to be hard), a `language` (`en`, `hinglish`, `hi`, or another ISO 639-1 code) and an optional `note` saying in a few words what makes it tricky.

### notifications.json

One notification each, with its label.

```json
{
  "id": "n001",
  "difficulty": "everyday",
  "language": "en",
  "note": null,
  "notification": { "…": "as above" },
  "expected": { "label": "reply", "who": "Rahul" }
}
```

`amount` is written as `{ "currency": "INR", "value": 499.0 }`.

### messages.json

One chat message each, from a person to him in a one-to-one chat, with three yes-or-no labels.

```json
{ "id": "m001", "difficulty": "everyday", "language": "en", "text": "call me asap", "urgent": true, "asks": true, "ack": false }
```

- `urgent`: the sender needs him now. An emergency, distress, or someone pressing him to respond right away ("call me asap", "where are you??", "papa hospital mein hai").
- `asks`: the message asks him a question or asks him to do something, so it expects an answer or an action.
- `ack`: the message only acknowledges or reacts: "ok", "thanks", "haha", 👍, "Liked a message". Nothing is owed back. A message that is `ack` is never `asks`.

### money.json

A line of notification text and the first amount of money in it, or null when there is none.

```json
{ "id": "a001", "difficulty": "everyday", "language": "en", "text": "Aisha requested ₹1,250.50 for dinner", "amount": { "currency": "INR", "value": 1250.5 } }
```

### guesses.json

One unlock each. The phone was put down, some notifications came in, and he unlocks it. The answer is the one he most likely unlocked to deal with, or `none` when nothing waiting is worth a chip (everything is noise, or old casual chatter he'd have seen already).

```json
{
  "id": "g001",
  "difficulty": "everyday",
  "language": "en",
  "note": null,
  "items": [
    { "id": "a", "minutesBeforeUnlock": 4, "notification": { "…": "as above" } },
    { "id": "b", "minutesBeforeUnlock": 40, "notification": { "…": "as above" } }
  ],
  "expected": { "chip": "a", "alsoFine": [] }
}
```

`minutesBeforeUnlock` is when the notification was posted. `chip` is an item id or `none`. `alsoFine` lists other items that would be just as reasonable a guess, for cases a person could honestly go either way on. In every scenario this is the first time he looks at his phone since the items arrived, and Earmark has no history yet for any person, app or group.
