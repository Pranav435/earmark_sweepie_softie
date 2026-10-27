# How accurate is Earmark?

An accuracy benchmark of Earmark 0.1.14, run on 27 October 2026.

Earmark makes the same few decisions all day. Is this notification something you owe? What does this message ask of you? How much money is this? Which loop did you unlock your phone for? Which replies should the strip offer? This report measures how often each of those decisions is right. It uses 750 labelled cases and 1,047 judged replies.

The first section is the short version. After that come how the test was done, how to read the numbers, one section for each decision, what to fix first, and the limits of the test.

## The short version

| What was tested | Cases | How often Earmark was right |
|---|---:|---:|
| Reading a notification: the right kind, or nothing | 300 | 249 (83%) |
| Is it a loop? Loops it caught | 185 | 167 (90%) |
| Is it a loop? Loops it opened that were real | 186 | 167 (90%) |
| The name on the chip, when the kind was right | 132 | 130 (98%) |
| Urgent messages it caught | 64 | 30 (47%) |
| Messages that ask something, caught | 169 | 101 (60%) |
| "ok", "thanks" and reactions, caught | 59 | 25 (42%) |
| Amounts of money read right | 80 | 63 (79%) |
| The chip at unlock: the right one, or rightly none | 70 | 42 (60%) |
| Reply strip offers at least one reply worth sending | 241 | 206 (85%) |

What stands out:

1. **Earmark is good at the main question.** Of the notifications that really were loops, it caught 90%. Of the loops it opened, 90% were real. It almost always names the right person.
2. **When it shows a chip, the chip is nearly always fair.** 38 of the 41 chips it showed came at unlocks that deserved one. Its main failure is staying quiet. A loop more than about half an hour old usually can't reach the score a chip needs. In the benchmark that meant no chip for a missed call from four hours ago, a ₹1,200 request from four hours ago, or a bill due today from two hours ago.
3. **It misses most acknowledgements outside plain English.** It doesn't treat "ठीक है बेटा 👍", "theek hai bhai 👍" or "Danke dir! 👍" as acknowledgements, so each of them opens a loop asking you to reply. These made up 9 of the 19 loops it shouldn't have opened.
4. **The urgency word lists catch fewer than half of urgent messages.** They also raise false alarms on "not urgent but…", "koi jaldi nahi" and "that was urgent yesterday".
5. **A bug in reading amounts.** An amount of four digits or more without a comma loses its last digits: "₹1500" is read as ₹150 and "$1200" as $120. An Indian-style amount loses everything after its first comma: "₹1,25,000" is read as ₹1.
6. **Smart Reply only answers English.** It answered 114 of 120 English messages and 2 of 121 others. Earmark's own quick replies fill the gap, which is why the strip still offers a sensible reply 85% of the time.

## How the test was done

- **The cases.** Three separate AI agents (Claude) wrote and labelled the cases. They worked from a labelling guide, `benchmark/LABELS.md` in the Android repository, which says what's right for the phone's owner. None of them could see Earmark's code. That way the cases test the rules rather than echo them.
- **Everyday and tricky.** About two thirds of the cases are everyday: what a phone sees all the time, worded the way people and companies really write. About a third are tricky, built on purpose to be hard: a promo that sounds like a bill, "not urgent but can u…", a group message for a different Pranav. Real phones see far fewer tricky cases, so the overall numbers are harsher than everyday use.
- **Languages.** Most cases are in English or Hinglish, with some Hindi and a few in Marathi, Gujarati, Tamil, Bengali, Arabic, Spanish, French, German and Portuguese.
- **The code.** The test runs the same rules Earmark 0.1.14 runs on the phone. Each notification is built the way Earmark's notification reader builds it. The unlock test runs the whole engine, as on a fresh install with no history. Smart Reply ran on an Android 16 emulator, through the same ML Kit library and built-in model the app uses.
- **The replies.** A fourth agent judged every suggested reply as worth sending or not. It didn't know which replies came from Smart Reply and which from Earmark's quick replies.
- **No answers were changed after the test ran.** Every label is the one its writer gave.

| Case set | What each case is | Cases |
|---|---|---:|
| Notifications | One notification and what Earmark should make of it | 300 |
| Messages | One chat message, marked urgent, asks, or only an acknowledgement | 300 |
| Amounts | A line of text and the first amount of money in it | 80 |
| Unlocks | The notifications that came in while the phone was down, and the chip it should show | 70 |
| Replies | Every reply the strip could offer for a message, judged | 1,047 |

## How to read the numbers

Every number is given as a count and a percentage, such as "167 of 185 (90%)".

- **Right** means Earmark's answer matched the label.
- **Caught** means: of the cases that really were something, such as urgent, how many Earmark said were. A rule that misses things has a low catch rate.
- **Right when it says yes** means: of the cases Earmark said were something, how many really were. A rule that raises false alarms scores low here.

The last two matter more than plain "right". For example, Earmark is right about urgency 83% of the time, but only because most messages aren't urgent. It catches only 47% of the urgent ones. (In technical terms, "caught" is recall and "right when it says yes" is precision.)

## 1. Reading notifications

Every notification goes to the classifier. It decides whether the notification opens a loop, and of which kind; whether it's evidence that closes one, such as "you paid" or "order placed"; or whether it's nothing.

The classifier gave the right answer for 249 of 300 notifications (83%): 173 of 197 everyday cases (88%) and 76 of 103 tricky ones (74%).

On the question that matters most, whether a notification is a loop at all:

- It caught 167 of the 185 real loops (90%).
- Of the 186 loops it opened, 167 were real (90%).

When it got the kind right, the chip named the right person 130 times out of 132 (98%) and showed the right amount 32 times out of 34 (94%).

| Kind | Caught | Right when it says yes |
|---|---:|---:|
| Reply to a message | 66 of 70 (94%) | 66 of 78 (85%) |
| Call back | 20 of 20 (100%) | 20 of 21 (95%) |
| Email | 27 of 30 (90%) | 27 of 33 (82%) |
| Reminder | 19 of 20 (95%) | 19 of 19 (100%) |
| Payment request | 19 of 20 (95%) | 19 of 19 (100%) |
| Bill | 15 of 25 (60%) | 15 of 16 (94%) |
| Paid (closes a payment) | 10 of 15 (67%) | 10 of 10 (100%) |
| Booked (closes a ride or order) | 8 of 15 (53%) | 8 of 9 (89%) |
| You replied (closes a reply) | 15 of 15 (100%) | 15 of 15 (100%) |
| Nothing | 50 of 70 (71%) | 50 of 80 (62%) |

| Language | Right |
|---|---:|
| English | 153 of 182 (84%) |
| Hinglish | 63 of 74 (85%) |
| Hindi | 23 of 29 (79%) |
| Other languages | 10 of 15 (67%) |

### Where it goes wrong

- **Acknowledgements open loops.** "theek hai bhai 👍", "ठीक है बेटा 👍", "haha sahi hai 😂", "thank you so much yaar 🙏", "Danke dir! 👍", "jajaja 😂😂", "nvm mil gaye 👍" and a Gujarati "take care" each became "Reply to …". So did a reaction written with straight quotes, Loved "pahunch gaya hostel". That's 9 of the 19 loops it shouldn't have opened.
- **Bills in Hindi, Marathi or Hinglish are missed.** An LIC premium "देय है", a Jio bill "tak due hai" and a Marathi electricity bill all opened nothing. So did a semester fee and a society maintenance charge, because "fee" and "maintenance" aren't bill words to Earmark, and so did failed payments: a bounced EMI, a failed Spotify charge, an unpaid Uber trip. In all, 9 of the 25 bills opened nothing, and one more became an email loop.
- **Your name in Devanagari doesn't count.** In three group messages, people wrote "प्रणव, …" in Hindi or Marathi. Earmark only knows the name in Latin letters, so it treated them as chatter. A group message that said "Pranav K aur Riddhi…", meant for a different Pranav, did open a loop.
- **Emails from companies that sign with a person's name.** A sales email from "Neha Sharma", a Scaler Academy webinar invitation, a Google Calendar invitation and a Google Docs comment were all treated as a person asking something. Earmark only skips senders whose names say "no-reply", "team", "updates" and the like.
- **Evidence that's missed.** Bank UPI debit texts ("Sent Rs.450.00 … To ISHAAN GUPTA") are skipped as "text from a business" before Earmark checks for a payment, so they never count as paid. Ride confirmations from Ola and Rapido include the code you share with the driver, so they are skipped as one-time codes. Hinglish "Order confirm ho gaya" isn't read as booked. "Out for delivery" is read as booked.
- **Scam texts look urgent.** A text from a mobile number saying your electricity "will be disconnected tonight… contact immediately" became an urgent reply loop. A WhatsApp message from HDFC Bank's business account became a reply loop too.

## 2. What a message asks

Three word-list readings decide how much a message asks of you. They matter twice. "Urgent" and "asks" set how due a reply is, and so whether it wins the chip. An acknowledgement decides whether a loop opens at all.

| Reading | Caught | Right when it says yes | Right overall |
|---|---:|---:|---:|
| Urgent | 30 of 64 (47%) | 30 of 47 (64%) | 249 of 300 (83%) |
| Asks something | 101 of 169 (60%) | 101 of 112 (90%) | 221 of 300 (74%) |
| Only an acknowledgement | 25 of 59 (42%) | 25 of 25 (100%) | 266 of 300 (89%) |

The acknowledgement check never mistook a real message for "ok". That's the safe way round to be wrong: a missed acknowledgement costs a stray loop, not a missed message.

### Where it goes wrong

- **Urgent messages without the listed words.** "I need you to call me. Now.", "mom fell in the bathroom, can u come home now", "CALL ME", "kahan hai tu??", "kidhar hai? sab wait kar rahe hain" and "llámame ya, es urgente" were all missed. So were urgent messages in Tamil, Gujarati, Marathi, Bengali and Arabic, which the lists don't cover.
- **False alarms on the same words.** "not urgent but can u check if my parcel came", "koi jaldi nahi, aaram se call karna", "कोई जल्दी नहीं है…", "lol that was urgent 😂", "had a small emergency yesterday but it's all sorted now" and the reaction Emphasized "call me asap" were all read as urgent. 10 of the 17 false alarms say "not urgent" or talk about the past.
- **Questions without a question mark.** "what's the plan for saturday", "r u home", "did u finish the assignment", "kya kar raha hai", "bhai wifi ka password kya hai" and "kab aa raha hai ghar" weren't read as asking.
- **Requests phrased as instructions.** "cab is here, come down fast", "pick up ur phone", "don't forget to bring the charger tmrw", "ghar aaja jaldi" and "आते समय मेरे लिए एक किताब ले आना" weren't read as asking either.
- **Quoted and rhetorical questions.** she literally texted me "are you free tonight?" and then ghosted lmao, Liked "are you coming?", and "कौन करता है ऐसा? 🙄" were read as asking.
- **Acknowledgements in other words.** "ठीक है", "अच्छा", "धन्यवाद", "thik h", "haan bhai", "badhiya", "chalo done", "k thx bye", "ok see u then", "thank you so much 🙏" and "Liked an image" weren't recognised.

## 3. Amounts of money

Amounts appear on payment and bill chips, such as "Pay Aisha back ₹450". Earmark read 63 of 80 lines right (79%): 37 of 40 everyday lines and 26 of 40 tricky ones. It found the right amount 46 times, read a wrong amount 7 times, and missed an amount 10 times. It never invented an amount where there was none: all 17 lines without money came back empty.

### A bug: long numbers are cut short

Earmark reads an amount as up to three digits, then groups of exactly three digits after commas. Anything past that is cut off:

- **Four or more digits without commas lose the end.** The test data had one such case, "Rs1500 paid to Zepto", read as ₹150. Four extra checks outside the 80 cases confirm it's general: "₹1500 requested" is read as ₹150, "Rs 12000 is due" as ₹120, "INR 2500.50 debited" as ₹250, and "$1200 for rent" as $120.
- **Indian digit grouping loses everything after the first comma.** "₹1,25,000 credited" is read as ₹1, "Rs.10,00,000 sanctioned" as ₹10, and an ICICI credit card statement for INR 1,02,340.00 became a loop reading "Pay your ICICIB bill ₹1".

### Other misses

- Amounts with the currency after the number: "500 rs", "800 rs de dena", "500 रुपये भेज दो", "15,50 €".
- Lakh: "₹2 lakh" is read as ₹2, and "Rs. 1.5 lakh" as ₹1.50.
- Amounts with no currency at all, such as "1.5k bhej de" and "cost me 640, send ur half". These are fair to miss: nothing in them says money.

## 4. The chip at unlock

This test runs the whole engine. Notifications arrive while the phone is down. Then it's unlocked, and Earmark shows the chip it picks, or nothing. Each case has a best answer, sometimes other answers that are just as reasonable, or "none" when nothing is worth a chip.

Earmark gave a fine answer at 42 of 70 unlocks (60%), and the single best answer at 38. It did better on tricky cases (15 of 23) than on everyday ones (27 of 47), because most everyday misses were older loops that got no chip.

| Question | Result |
|---|---:|
| Unlocks that deserved a chip and got one | 38 of 57 (67%) |
| Unlocks that deserved no chip and got none | 10 of 13 (77%) |
| Chips shown at unlocks that deserved one | 38 of 41 (93%) |
| Chips that were a fine pick, when a chip was deserved | 32 of 38 (84%) |

### Why the 28 misses happened

| Cause | Misses |
|---|---:|
| A real loop scored under the chip's bar of 0.70, so no chip showed | 16 |
| A chip showed, but for the wrong item | 6 |
| The classifier didn't see the loop at all, so no chip showed | 3 |
| A chip showed when nothing deserved one | 3 |

**Scoring under the bar** is the biggest cause. Earmark's score leans hard on how recent a loop is. After half an hour, the "recent" part of the score has fallen to about a third. After an hour it's an eighth. On a fresh install, with no history to raise the score, a loop older than half an hour rarely clears 0.70. Every one of the 16 was between 25 minutes and 4 hours old:

- a missed call from Dr. Shah's clinic, 4 hours old
- "कब तक आएगा घर?" from Mummy, 4 hours old
- a PhonePe request for ₹1,200, 4 hours old
- an Airtel bill due today, 2 hours old
- an email from a professor with questions on a thesis draft, 40 minutes old
- "@Pranav tera part kab tak hoga? kal submission hai" in a project group, 25 minutes old

Earmark learns who you answer and how fast, so these scores rise with use. The test only shows the first days.

**The wrong item** came from things covered above. A fresh "finally assignment submit kar diya 😮‍💨", which asks nothing, beat an older question. "see you at 7 then!" won because the payment request beside it wasn't recognised (see below). An urgent message in Hindi ("ज़रूरी बात है") and one in German ("Ist dringend!") lost to a fresher "free at 5?". "haha sahi hai", one minute old, beat a landlord's message in Hindi. A Telegram channel's announcement was treated as a person writing to him.

**Not seen at all:** Google Pay requests worded "Payment request from Aisha Khan" with the amount on the next line (twice here, and once more behind a wrong chip), and a credit card notice that says "payment due" without the word "bill".

**A chip that shouldn't have shown:** a phishing text from a mobile number ("your SBI YONO account will be BLOCKED today"), a loud group message that didn't name him, and "thanks man!!".

## 5. Reply suggestions

After you open a chat through Earmark, the reply strip offers up to three one-tap replies. They come from ML Kit Smart Reply. If Smart Reply can't read the language, they come from Earmark's quick replies, such as "Yes / No / Let me check". If Smart Reply isn't confident, the strip is empty. The test asked, for every message except plain acknowledgements: does the strip offer at least one reply worth sending, and how many of the replies it shows are worth sending?

| Source | Messages with a reply worth sending | Replies worth sending |
|---|---:|---:|
| Smart Reply, when it answers | 110 of 116 (95%) | 283 of 348 (81%) |
| Quick replies alone, if they were used every time | 190 of 241 (79%) | 305 of 723 (42%) |
| The strip as Earmark shows it | 206 of 241 (85%) | 434 of 702 (62%) |

| Language | Smart Reply answered | Strip had a reply worth sending |
|---|---:|---:|
| English | 114 of 120 (95%) | 108 of 120 (90%) |
| Hinglish | 2 of 73 (3%) | 57 of 73 (78%) |
| Hindi | 0 of 30 (0%) | 24 of 30 (80%) |
| Other languages | 0 of 18 (0%) | 17 of 18 (94%) |

Smart Reply is very good at English, and it gives up on everything else. Quick replies almost always include one fitting reply, but usually two that don't fit. The strip was empty for 7 messages, where Smart Reply wasn't confident: four links, a long vent, news of a baby born in hospital, and "dad's bp is really high, taking him to hospital". Smart Reply is built to stay quiet on sensitive topics.

### Where it goes wrong

- **"Yes / No" for where, what and how questions in Hindi and Hinglish.** "kahan hai tu??", "कहाँ हो? सब इंतज़ार कर रहे हैं", "परीक्षा कैसी गई?" and "kaisa hai bhai?" all got "Yes / No / Let me check". Quick replies offer that to every question, so a where, what or how question gets yes or no. In English, Smart Reply usually answers first, so this shows up mostly in Hindi and Hinglish.
- **Hinglish questions without a question mark** got "Got it / Will reply soon / Can I call you?": "bhai wifi ka password kya hai", "aaj college aa raha hai kya".
- **Smart Reply's own misses** are few: "Okay / Yes / Yeah" to "which bus goes to campus from dadar", and "Yes / Okay / No" to "class has started where r u, sir is taking attendance".

## What to fix first

In order of how much they'd help for the work involved:

1. **Fix the amount bug.** Read the whole number, with or without commas, including Indian grouping. It's a small change to one regular expression, and today a chip can ask you to pay $120 when you owe $1,200.
2. **Recognise more acknowledgements.** Add Hindi, Hinglish and the other languages' words for ok and thanks (ठीक है, अच्छा, धन्यवाद, thik h, sahi hai, badhiya, haan bhai), English phrases such as "thank you so much" and "see u then", reactions written with straight quotes, and "Liked an image". This removes about half the stray reply loops.
3. **Let owed loops stay chip-worthy for longer.** A missed call, a payment request or a bill due today that came in while the phone was down is still owed hours later. Recency could fade more slowly for those kinds, or count from when the phone was put down. This is a product decision (ADR 0017 tuned the score for the first unlock after a message), and it's behind 16 of the 28 unlock misses.
4. **Teach urgency about "not" and the past.** Ignore "urgent" after "not" or "no", and "koi jaldi nahi" and "कोई जल्दी नहीं". Ignore it in sentences about yesterday, and in quoted reactions. Add phrases such as "now", "rn", "come home", "kahan hai", "kidhar hai", and the common words for urgent in Marathi, Gujarati, Tamil, Bengali and Spanish.
5. **Read questions and requests without a question mark.** Question words at the start (what, when, did, r u, kya, kab, kahan, kaisa) and instructions aimed at you (come, pick up, bring, aaja, le aana, घर आओ).
6. **Read more bills and payments.** Bill wording in Hindi and Marathi (देय, due hai, बिल), "fee", "maintenance", and failed payments or bounced EMIs. Check for "you paid" before skipping a bank's text. Don't skip a ride confirmation as a one-time code.
7. **Know your name in other scripts.** Let Settings › Your name take प्रणव as well as Pranav, or offer it.
8. **Give quick replies a set for where, what and how questions, and Hindi and Hinglish question words**, so "kahan hai tu??" gets something better than "Yes / No".
9. **Be wary of urgent texts from unknown numbers** that talk about blocked accounts or disconnection. They are almost always scams.

## Limits of this test

- **The cases are written, not collected.** An AI wrote every case from what it knows of real notifications. Real apps may word things differently. No real messages were used, because Earmark never stores any.
- **The mix isn't a real phone's mix.** Most of a real phone's notifications are noise that's easy to skip, and few are tricky. The test has more loops and more hard cases, so the overall numbers aren't the share of a real day Earmark gets right.
- **One labeller per case.** Some cases could honestly go either way. The agents flagged about 40 as hard calls, such as an emergency stated without a question, or "lol that was urgent 😂". A second labeller would make the labels firmer.
- **An AI judged the replies.** It didn't know where each reply came from, but its idea of "worth sending" is one view.
- **A fresh install only.** Earmark learns which people you answer, which groups you read and how fast you deal with each app. None of that is in the test, so the unlock results show its first days, not its best.
- **Smart Reply saw one message at a time.** On the phone it sees up to the last ten messages of the conversation, which can help it.
- **Context the agents saw.** The agents that wrote the cases had the project's short guide for coding agents in view, which mentions a couple of Earmark's rules, such as the chip's bar of 0.70. They were told to label from the labelling guide alone.

## Run it again

The benchmark lives in the `benchmark` folder of the Android repository: the labelling guide, the cases, the results and the steps, in its README. One command reruns everything that runs on a laptop:

```bash
./gradlew :core:benchmark
```

It prints the headline numbers. It writes every number to `benchmark/results/results.json`, and every case the rules got wrong to `benchmark/results/misses.md`. Smart Reply needs a phone or the emulator; the README has the four commands. Run it after changing a rule, and compare the results with the ones committed here.
