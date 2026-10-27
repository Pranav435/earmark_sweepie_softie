# How accurate is Earmark? Benchmark v2

An accuracy benchmark of Earmark 0.1.15, run on 27 October 2026, against 0.1.14.

The first benchmark (`docs/benchmark.md`) measured how often Earmark 0.1.14 made the right call, and listed what to fix first. 0.1.15 fixes most of that list. It reads messages with wider word lists and a small model that runs on the phone. It lets something you owe stay chip-worthy for hours, and it reads amounts, bills, payments and bookings it used to miss. This report measures the result.

The fixes were made while looking at the first benchmark's cases, so those cases can no longer say how good Earmark is. Rules written while looking at a test will pass that test. So this benchmark adds a new, sealed set of 750 cases of the same kinds, written by different agents who never saw the code, the old cases or the changes. That set was run once, after the code was frozen. **Its numbers are the ones to trust**, and they come first throughout.

## The short version

On the new cases, which nobody tuned anything against:

| What was tested | Cases | 0.1.14 | 0.1.15 |
|---|---:|---:|---:|
| Reading a notification: the right kind, or nothing | 300 | 246 (82%) | 272 (91%) |
| Is it a loop? Loops it caught | 185 | 164 (89%) | 174 (94%) |
| Is it a loop? Loops it opened that were real | 181 / 182 | 164 (91%) | 174 (96%) |
| Urgent messages it caught | 59 | 29 (49%) | 44 (75%) |
| Messages that ask something, caught | 170 | 92 (54%) | 156 (92%) |
| "ok", "thanks" and reactions, caught | 60 | 26 (43%) | 54 (90%) |
| Amounts of money read right | 80 | 53 (66%) | 69 (86%) |
| The chip at unlock: the right one, or rightly none | 70 | 32 (46%) | 67 (96%) |
| Reply strip offers at least one reply worth sending | 240 | 183 (76%) | 199 (83%) |

What stands out:

1. **The chip at unlock is right almost every time now: 96% of unlocks, up from 46%.** Most of 0.1.14's misses were something still owed that had come in more than half an hour before the unlock: a missed call, a money request, a bill, a question from family. Now those wait for you. Of 57 unlocks that deserved a chip, 55 got one, and every chip shown was at an unlock that deserved one.
2. **Reading messages improved the most.** Earmark now catches 92% of messages that ask something, up from 54%, and 75% of urgent ones, up from 49%. It still never mistook a real message for "ok": all 54 messages it called acknowledgements really were.
3. **The model earns its place, by a little.** On the new cases, the word lists alone get urgent right 91% of the time and asks 91%. With the model beside them, those are 92% and 93%. Most of the gain is from the wider lists. The model adds about two points on top, mostly by catching what the lists miss.
4. **No more wrong amounts.** 0.1.14 read 7 amounts wrong on the new cases (₹1,25,000 as ₹1, $1200 as $120). 0.1.15 read none wrong. It still misses 11, mostly amounts in Devanagari digits or written in words.
5. **It costs about twice the time and stays fast.** On a Galaxy A34, reading a notification takes about 1.1 ms, up from 0.6 ms, and the model adds 96 KB to the app.
6. **The gap between the two sets shows how much was tuning.** On the old cases, 0.1.15 reads 99% of notifications right. On the new ones, 91%. The new number is the honest one, and it's still nine points better than 0.1.14's 82%.

## What changed in 0.1.15

The first benchmark ended with nine things to fix. Here they are, with what 0.1.15 does about each. ADR 0031 has the details.

| What benchmark v1 asked for | In 0.1.15 |
|---|---|
| 1. Fix the amount bug | Done. Whole numbers with or without commas, Indian and European grouping, "500 rs", "15,50 €", lakh, crore and k. Rupees now show the Indian way, "₹1,25,000". |
| 2. Recognise more acknowledgements | Done, in Hindi, Hinglish, Marathi, Gujarati, Tamil, Bengali, Arabic, Spanish, French, German and more, with filler like "bhai" and "beta", and reactions with straight quotes. |
| 3. Let owed loops stay chip-worthy for longer | Done. At the first unlock after something owed came in, it fades over six hours instead of thirty minutes. Chatter still fades fast. |
| 4. Teach urgency about "not" and the past | Done. "not urgent", "koi jaldi nahi", "was urgent", "yesterday", "sorted now", jokes and quotes no longer count. Urgent words were added in nine more languages. |
| 5. Read questions and requests without a question mark | Done. Question words at the start of a clause, Hinglish and Hindi question words, and instructions aimed at you ("bring the charger", "doodh le aana", "शाम को कॉल करना"). |
| 6. Read more bills and payments | Done. Bills in Hindi, Marathi and Hinglish, fees, maintenance, card dues, bills by email, and failed payments. Bank texts count as paid, and ride confirmations with a code count as booked. |
| 7. Know your name in other scripts | Done, without a setting. Words in Indian scripts are read as Latin letters, so "प्रणव" and "પ્રણવ" match Pranav. "Pranav K" doesn't. |
| 8. Better quick replies for where, what and how | Done, and in Hindi or Hinglish when the message is. |
| 9. Be wary of urgent texts from unknown numbers | Done. A text from an unsaved number about a blocked account, a KYC update or a prize opens nothing. |

Two more changes weren't on the list:

- **A small model reads every message** beside the word lists, for urgent, asks and acknowledgement. It's described below.
- **At an unlock, an urgent loop above the bar comes first**, as it already did for a message that arrives while you use the phone.

## How the test was done

- **Two sets of cases.** The *old cases* are benchmark v1's 750 cases and their reply judgements. They were open while 0.1.15 was built: the changes were written from what their misses showed. The *new cases* are 750 more of the same kinds, in `benchmark/holdout`. Four new AI agents (Claude) wrote and labelled them from the labelling guide alone (`benchmark/LABELS.md`). They saw no code, no old cases, no misses and no report. The new cases stayed sealed until 0.1.15's rules and model were frozen, and were then run once.
- **Three versions.** 0.1.14 as released (git `35ba2bc`); 0.1.15 with the word lists alone; and 0.1.15 as it ships, with the lists and the model. All three ran the same cases through the same benchmark code.
- **The model's training data is separate again.** Three more agents wrote 2,644 labelled messages for the model to learn from, also from the labelling guide alone. Neither set of benchmark cases was used to train it.
- **The replies.** Smart Reply ran on the Android 16 emulator over the new messages, as it did for the old ones. Three AI judges then judged every reply 0.1.14 or 0.1.15 would show as worth sending or not: 463 new replies on the old cases and 1,471 on the new ones. They weren't told where any reply came from. They worked from the first judge's standard, with 20 of its judgements as examples.
- **Speed.** A test app timed Earmark's engine on a Samsung Galaxy A34. It posted every old-case notification, and every old-case message as a WhatsApp chat, through `Engine.handle`, for both versions.
- **On the phone.** 0.1.15's release build was installed over 0.1.14 on the A34. The pill check passed there. Text messages and a missed call were sent to the emulator and read by the real listener.
- **Two fixes came after the sealed run.** Both were found on the emulator, not in the new cases' misses. First, a bank's text that you paid a friend closed a bill texted to the same app; now a bill closes only on a payment of its amount. Second, Google Messages shows an unsaved sender as "(987) 650-0044", which the scam check didn't recognise. Every number in this report was measured again after both fixes, and none changed.

| Case set | What each case is | Old cases | New cases |
|---|---|---:|---:|
| Notifications | One notification and what Earmark should make of it | 300 | 300 |
| Messages | One chat message, marked urgent, asks, or only an acknowledgement | 300 | 300 |
| Amounts | A line of text and the first amount of money in it | 80 | 80 |
| Unlocks | The notifications that came in while the phone was down, and the chip it should show | 70 | 70 |
| Replies | A reply the strip could offer, judged | 1,510 | 1,471 |

## How to read the numbers

As in the first benchmark, every number is a count and a percentage, such as "174 of 185 (94%)".

- **Right** means Earmark's answer matched the label.
- **Caught** means: of the cases that really were something, such as urgent, how many Earmark said were. In technical terms, recall.
- **Right when it says yes** means: of the cases Earmark said were something, how many really were. In technical terms, precision.

## 1. Reading notifications

On the new cases, the classifier gave the right answer for 272 of 300 notifications (91%), up from 246 (82%). Everyday cases went from 88% to 95% (185 of 195), and tricky ones from 70% to 83% (87 of 105).

On whether a notification is a loop at all:

- It caught 174 of the 185 real loops (94%), up from 164 (89%).
- Of the 182 loops it opened, 174 were real (96%), up from 164 of 181 (91%).

When it got the kind right, the chip named the right person 130 times out of 131, and showed the right amount 37 times out of 37.

| Kind (new cases) | Caught, 0.1.14 | Caught, 0.1.15 | Right when it says yes, 0.1.14 | Right when it says yes, 0.1.15 |
|---|---:|---:|---:|---:|
| Reply to a message | 67 of 70 (96%) | 68 of 70 (97%) | 67 of 78 (86%) | 68 of 76 (89%) |
| Call back | 20 of 20 (100%) | 20 of 20 (100%) | 20 of 21 (95%) | 20 of 20 (100%) |
| Email | 26 of 30 (87%) | 26 of 30 (87%) | 26 of 30 (87%) | 26 of 27 (96%) |
| Reminder | 20 of 20 (100%) | 20 of 20 (100%) | 20 of 22 (91%) | 20 of 22 (91%) |
| Payment request | 13 of 20 (65%) | 17 of 20 (85%) | 13 of 13 (100%) | 17 of 17 (100%) |
| Bill | 15 of 25 (60%) | 20 of 25 (80%) | 15 of 17 (88%) | 20 of 20 (100%) |
| Paid (closes a payment) | 11 of 16 (69%) | 14 of 16 (88%) | 11 of 11 (100%) | 14 of 14 (100%) |
| Booked (closes a ride or order) | 9 of 16 (56%) | 12 of 16 (75%) | 9 of 11 (82%) | 12 of 13 (92%) |
| You replied (closes a reply) | 15 of 15 (100%) | 15 of 15 (100%) | 15 of 15 (100%) | 15 of 15 (100%) |
| Nothing | 50 of 68 (74%) | 60 of 68 (88%) | 50 of 82 (61%) | 60 of 76 (79%) |

| Language (new cases) | 0.1.14 | 0.1.15 |
|---|---:|---:|
| English | 140 of 171 (82%) | 154 of 171 (90%) |
| Hinglish | 64 of 75 (85%) | 68 of 75 (91%) |
| Hindi | 24 of 33 (73%) | 30 of 33 (91%) |
| Other languages | 18 of 21 (86%) | 20 of 21 (95%) |

On the old cases, 0.1.15 gets 296 of 300 right (99%), up from 249 (83%).

### Where it still goes wrong

These are from the new cases, which is why they weren't fixed: fixing them now would be tuning on the test.

- **A business sender ID with a small letter.** "VM-ViCARE" sent two Hinglish Vi bills, and Earmark took it for a person, because sender IDs are expected in capitals. Both became "Reply to VM-ViCARE". A WhatsApp message from ACT Fibernet with a bill became a reply too.
- **Bills it still doesn't read.** "Bill amt INR 1,178.82 … due 03Nov" has no space in "due 03Nov". An SBI card text says "कुल बकाया" (total outstanding) without a word for bill.
- **Payment requests it doesn't read.** Splitwise's "You owe Aditi Joshi ₹1,240.50. Settle up?" and Google Pay's reminder about an older request.
- **Bookings.** Uber's "Ramesh is on the way" is an ongoing notification, so it's skipped before Earmark reads it. IRCTC's PNR texts don't say "booked". Zomato's "Order on the way" was read as booked; it's tracking.
- **Paid in Hindi.** "भुगतान सफल" (payment successful) and "₹1,500 … को भेजे गए" (sent to) aren't read as paid.
- **Someone else's name.** "@Pranav Mhatre" is a different Pranav, but only a single initial ("Pranav K") is recognised as someone else. A Telegram mention by username ("@pranavsavla") isn't recognised as him.
- **Noise read as a loop.** A Hinglish phishing text from a mobile number without the English warning words, a Telegram channel's announcement, a Duolingo nudge in the reminder category, and a holiday from a subscribed calendar.

## 2. What a message asks

| Reading (new cases) | Caught, 0.1.14 | Caught, 0.1.15 | Right when it says yes, 0.1.14 | Right when it says yes, 0.1.15 | Right overall, 0.1.14 | Right overall, 0.1.15 |
|---|---:|---:|---:|---:|---:|---:|
| Urgent | 29 of 59 (49%) | 44 of 59 (75%) | 29 of 49 (59%) | 44 of 52 (85%) | 250 of 300 (83%) | 277 of 300 (92%) |
| Asks something | 92 of 170 (54%) | 156 of 170 (92%) | 92 of 107 (86%) | 156 of 162 (96%) | 207 of 300 (69%) | 280 of 300 (93%) |
| Only an acknowledgement | 26 of 60 (43%) | 54 of 60 (90%) | 26 of 26 (100%) | 54 of 54 (100%) | 266 of 300 (89%) | 294 of 300 (98%) |

Every reading got better at both catching and being right when it says yes, so the gains aren't bought with false alarms. Hindi improved most on "asks", from 58% right to 97%, because Hindi requests are mostly instructions without a question mark ("दवाई ले आना").

On the old cases, 0.1.15 gets urgent right 98% of the time (83% before), asks 96% (74%), and acknowledgements 99% (89%).

### The model's share

| Reading (new cases) | Word lists alone | Lists and model |
|---|---:|---:|
| Urgent, right overall | 272 of 300 (91%) | 277 of 300 (92%) |
| Urgent, caught | 42 of 59 (71%) | 44 of 59 (75%) |
| Urgent, right when it says yes | 42 of 53 (79%) | 44 of 52 (85%) |
| Asks, right overall | 272 of 300 (91%) | 280 of 300 (93%) |
| Asks, caught | 149 of 170 (88%) | 156 of 170 (92%) |
| Only an acknowledgement, right overall | 293 of 300 (98%) | 294 of 300 (98%) |

The model is three small logistic regressions, one per reading. Each reads a message's words, its pairs of words, the runs of letters inside each word, its emoji, and which word-list rules fired. It learned from the 2,644 training messages. It can overrule the lists only when it's sure, and never against a rule that says what a message *isn't*: "not urgent", "it's over", a reaction, an offer, and, for acknowledgements, any question, request or greeting. A model of words can't tell what a "not" applies to. The rules can.

Tested by five-fold cross-validation on its own training messages (each fifth checked by a model trained on the rest), the lists and model together beat the lists alone on all three readings:

| Reading (training messages, cross-validated) | Word lists alone | Lists and model |
|---|---:|---:|
| Urgent: right / caught | 91% / 59% | 93% / 75% |
| Asks: right / caught | 88% / 82% | 91% / 89% |
| Acknowledgement: right / caught / right when it says yes | 93% / 65% / 99% | 96% / 80% / 98% |

These messages also show how much the word lists were shaped by the old cases. The lists get 97% of urgent readings right on the old cases, 91% on the new ones, and 91% on the model's training messages. The model's training messages had never been seen while writing the lists.

### Where it still goes wrong

- **Emergencies without urgent words.** "my car broke down on the highway near lonavala and my phone is at 3%", "theres a guy following me rn", "the doctor just said they need to operate on mom tonight", "nani nahi rahi 😭". 15 of the 59 urgent messages were missed, most of them like these. More training messages of this kind would help the model most.
- **"Urgent" that isn't.** "URGENT 🚨🚨 zara sale is live", "urgent wala kaam ho gaya, ab tension nahi 😌", and "where are you rn, want to grab chai?".
- **Requests said as statements.** "I need the 2000 back by friday", "you have the keys right", "Mom says call her".
- **Rhetorical questions** still count as asking: "who even schedules an exam on a saturday??".
- **Acknowledgements with more words:** "chal thik hai kal milte", "thanks for the help today man, really appreciate it 🙏", "बहुत अच्छा बेटा, खुश रहो".

## 3. Amounts of money

On the new cases, Earmark read 69 of 80 lines right (86%), up from 53 (66%). It found the right amount 56 times, up from 40. It read a wrong amount 0 times, down from 7. It missed an amount 11 times, down from 20. It never invented an amount where there was none, before or now: all 13 lines without money came back empty.

On the old cases it reads 78 of 80 right (98%), up from 63 (79%).

The 11 misses: amounts in Devanagari or Gujarati digits ("₹१,२५०", "₹૨૦૦"), amounts in words ("पाँच सौ रुपये", "1.5 lakh ka laptop" without a currency, "200 دولار"), bare numbers in a chat about money ("bhai 300 bhej de"), and "1,250/-", where "/-" means rupees.

## 4. The chip at unlock

On the new cases, Earmark gave a fine answer at 67 of 70 unlocks (96%), up from 32 (46%), and the single best answer at 65, up from 32. Everyday unlocks went from 22 of 46 to 45 of 46, and tricky ones from 10 of 24 to 22 of 24.

| Question (new cases) | 0.1.14 | 0.1.15 |
|---|---:|---:|
| Unlocks that deserved a chip and got one | 27 of 57 (47%) | 55 of 57 (96%) |
| Unlocks that deserved no chip and got none | 11 of 13 (85%) | 13 of 13 (100%) |
| Chips shown at unlocks that deserved one | 27 of 29 (93%) | 55 of 55 (100%) |
| Chips that were a fine pick, when a chip was deserved | 21 of 27 (78%) | 54 of 55 (98%) |

0.1.14 missed 38 of the 70 new unlocks:

- 27 times a real loop scored under the bar, nearly all because it was more than half an hour old.
- 8 times it showed the wrong chip, or a chip when none was wanted.
- 3 times the classifier didn't see the loop at all.

0.1.15 misses 3:

- Two bills that came in five and six hours before the unlock. Six hours of fading brings them just under the bar.
- One where a reel link from a minute ago beat a ₹2,400 Google Pay request from three hours ago.

On the old cases it gives a fine answer at 68 of 70 unlocks (97%), up from 42 (60%).

**Why it changed.** Three changes did most of the work. Owed loops now fade over six hours at the first unlock after they came in. The classifier reads more bills and payment requests. And acknowledgements no longer open loops that can win the chip. An old loop that isn't owed, such as "haha nice" from two hours ago, still fades in half an hour. A loop you've already seen at an earlier unlock fades in half an hour too, so an ignored question doesn't come back every time you pick up the phone.

**A caution about this number.** The agent who wrote the new unlock cases was told, as a way to decide, that something still owed (a missed call, a money request, a bill, a question from family) is often still worth a chip hours later. That is the same judgement the first benchmark's independent writers made, in 16 of their 28 misses. It is also the product decision behind this change. But it means the new unlock cases agree with that decision partly because they were asked to. The other sections had no such hint.

## 5. Reply suggestions

| Strip (new cases) | 0.1.14 | 0.1.15 |
|---|---:|---:|
| Messages with a reply worth sending | 183 of 240 (76%) | 199 of 240 (83%) |
| Replies worth sending, of those shown | 366 of 687 (53%) | 444 of 687 (65%) |
| Quick replies alone: messages with one worth sending | 155 of 240 (65%) | 184 of 240 (77%) |
| Quick replies alone: replies worth sending | 237 of 720 (33%) | 373 of 720 (52%) |

| Language (new cases) | 0.1.14 | 0.1.15 |
|---|---:|---:|
| English | 104 of 121 (86%) | 103 of 121 (85%) |
| Hinglish | 44 of 71 (62%) | 58 of 71 (82%) |
| Hindi | 19 of 28 (68%) | 22 of 28 (79%) |
| Other languages | 16 of 20 (80%) | 16 of 20 (80%) |

Smart Reply is unchanged: it answered 110 of the 240 new messages, nearly all English, and 104 of those had a reply worth sending. The gain is all in quick replies, which the strip uses when Smart Reply can't read the language. They now answer the question asked ("Raste mein hoon" to "kidhar hai tu", "Badhiya, tum batao?" to "kaisa hai bhai"), in Hindi or Hinglish when the message is. On the old cases, the strip offers a reply worth sending for 216 of 241 messages (90%), up from 206 (85%).

### Where it still goes wrong

- **Emergencies get "Theek hai, ho jayega".** "bhai jaldi aa hostel, rohit ka accident ho gaya" and "तुरंत अस्पताल आ जाओ" are requests, so they get "Okay, will do / Done / Can't right now". An urgent request should get "On my way / Calling you now".
- **Quoted questions get answers.** 'teacher ne pucha "homework kahan hai?"…' got "Raste mein hoon".
- **Good news and stories get "Accha / Baad mein baat karte hain / Call karoon?"**, which is flat for "result aa gaya, pass ho gaya 🎉".

## 6. Speed and size

| Measure | 0.1.14 | 0.1.15 |
|---|---:|---:|
| Reading one notification, Galaxy A34 | 0.58 ms | 1.12 ms |
| Reading one chat message, Galaxy A34 | 0.28 ms | 0.71 ms |
| The first notification after the listener starts, Galaxy A34 | 11 ms | 20 ms |
| Release APK | 37.11 MB | 37.22 MB |

These are the engine's whole handling of each notification, timed in a debug build, where code runs slower than in the release app. The model's weights are 96 KB, one byte each, and load once, when the listener connects, off the main thread. Even at 1 ms each, a phone that gets 300 notifications a day spends about a third of a second a day reading them. Nothing goes to a server, and the app still has no `INTERNET` permission.

Most of the extra time is the word lists, which now check every message for about 20 kinds of clue. The model itself takes about a tenth of the time.

## What to fix next

In order of how much they'd help for the work involved. All of them come from the new cases' misses.

1. **Give urgent requests urgent quick replies:** "On my way", "Calling you now". Today an accident gets "Okay, will do".
2. **Accept business sender IDs with small letters**, such as "VM-ViCARE", and treat a business's WhatsApp account that sends a bill as a bill.
3. **More training messages about emergencies without urgent words**, such as breakdowns, being followed, or a death in the family. They are the model's biggest gap.
4. **Read amounts in Indian digits, "/-", and amounts in words.**
5. **Read the ride app's "on the way" even when the notification is ongoing**, and IRCTC's PNR texts as booked. Food "on the way" is tracking, not booked.
6. **Read Splitwise's "You owe" and payment apps' reminders about older requests** as payment requests.
7. **Know a different Pranav by surname**, not only by initial, and Telegram usernames.
8. **Let bills fade more slowly than questions.** A bill due in days is as owed after six hours as after one.
9. **Write a new sealed set before the next round of changes.** This one has now been read, so it can't test fixes made from it.

## Limits of this test

- **Everything was written by AI.** The cases, the labels, the model's training messages and the reply judgements all came from Claude agents. They worked from the labelling guide, but they share a way of writing. Real messages may be worded differently, and the model may have learned the agents' style as well as the task.
- **The old cases are no longer a test.** Their numbers show how well the fixes fit the misses they were made from. Only the new cases measure how well 0.1.15 does on messages nobody tuned against.
- **What the new cases' writers were told.** The unlock writer's hint is described in section 4. Separately, one of the model's training writers was told a bare "hi" usually asks for a reply. That contradicted ADR 0017, so those labels were changed back by hand before training, along with one offer labelled as a request. Six messages changed, and the model README lists them.
- **One labeller per case,** as before. Some cases could honestly go either way: "where are you rn, want to grab chai?" is labelled not urgent, and "kidhar pahuncha?" could be either.
- **Different reply judges.** The first benchmark's judge judged the old replies. Three new judges, given 20 of its judgements as examples, judged the rest. Their standards may differ a little. Both versions' replies on the new cases were judged by the same new judges, so that comparison is fair.
- **A fresh install only.** As before, the unlock test starts with no history about any person, group or app.
- **Speed on one phone,** in a debug build. Release builds are faster, and other phones differ.

## Run it again

The benchmark lives in the `benchmark` folder of the Android repository. `benchmark/README.md` has every step.

```bash
./gradlew :core:benchmark                                         # the old cases
./gradlew :core:benchmark -PbenchmarkRoot=benchmark/holdout       # the new cases
./gradlew :core:benchmark -PrulesOnly                             # without the message model
./gradlew :core:trainModel                                        # retrain the model (model/README.md)
```

Each run writes `results.json` with every number, and `misses.md` with every case the rules got wrong, to the set's `results` folder. 0.1.14's results on the new cases are in `benchmark/holdout/results/v0.1.14`, and the A34 timings are in `benchmark/results/engine-timing.json`.
