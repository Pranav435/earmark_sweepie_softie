# Benchmark misses

Every case the rules got wrong. Written by `./gradlew :core:benchmark`; the cases are in `benchmark/data`.

## Classifier

Each line: case id, difficulty and language; expected → got; what the case was.

- **n063** (tricky, hinglish) `none` → `reply` (opened “Reply to Ishaan”). WhatsApp · [msg] · Ishaan · nvm mil gaye 👍 Note: question withdrawn by the sender
- **n092** (tricky, en) `email` → `none` (skip: email asks nothing). Gmail · [email] · Ishaan Gupta · You left your laptop in Lab 3!! Note: urgent, but no question or explicit ask
- **n154** (tricky, en) `reply` → `none` (group chatter, not for you). WhatsApp · [msg] · Trek Squad 🏔️ · Aisha: we're at Kalsubai base, network is weak, PLEASE Note: group emergency that doesn't name him
- **n271** (everyday, en) `paid` → `none` (skip: text from a business). Messages · [msg] · AX-HDFCBK · Sent Rs.450.00 From HDFC Bank A/C *4521 To ISHAAN GUPTA On 27/10/26 Ref 630112345678 Not You? Call 18002586161/SMS BLOCK UPI to 7308080808

## Classifier names and amounts

Each line: case id, difficulty and language; expected → got; what the case was.

- **n046** (tricky, en) amount INR 799.0 → ₹699.00. Messages · [msg] · JD-AIRTEL-S · Payment overdue! Your Airtel Postpaid bill of Rs 699 was due on 03-Oct-2026. A late fee of Rs 100 has been added. Pay Rs 799 now to avoid suspension of outgoing calls. Note: overdue bill; the amount owed now includes a late fee

## Urgency

Each line: case id, difficulty and language; expected → got; what the case was.

- **m023** (everyday, en) urgent: true → false. i think i left the stove on, can u check rn pls
- **m064** (everyday, hinglish) urgent: true → false. chabi tere paas hai kya? main bahar khada hu
- **m108** (everyday, en) urgent: true → false. pls call back its about nani
- **m110** (everyday, en) urgent: true → false. someone hit my scooty, I'm ok but shaking. can u come
- **m142** (everyday, hinglish) urgent: true → false. bro exam hall mein aaja 5 min bache hai
- **m169** (tricky, hinglish) urgent: true → false. ruk, abhi ghar se mat nikalna, bahar bahut paani bhar gaya hai. nikalne se pehle call kar Note: a warning that needs action now
- **m264** (everyday, en) urgent: true → false. the quiz link closes in 5 min did u submit??
- **m265** (everyday, hinglish) urgent: true → false. bhai 500 bhej de abhi, wallet ghar pe bhool gaya
- **m295** (tricky, en) urgent: false → true. urgent!!! 🚨 look at this cat https://youtu.be/J---aiyznGQ Note: fake urgency on a meme link
- **m002** (tricky, en) asks: false → true. help!!! 😂 my mom just discovered instagram reels Note: fake distress
- **m020** (everyday, en) asks: true → false. urgent - prof wants to see you in his cabin right now
- **m047** (tricky, en) asks: false → true. how did i not know this lol Note: rhetorical question
- **m074** (everyday, en) asks: true → false. class has started where r u, sir is taking attendance
- **m104** (tricky, hi) asks: false → true. कौन करता है ऐसा? 🙄 Note: rhetorical question
- **m125** (tricky, en) asks: false → true. who even schedules a test on a sunday 🙄 Note: rhetorical question
- **m130** (tricky, hinglish) asks: false → true. building mein aag lagi thi!! sab bahar aa gaye, koi hurt nahi hua, tension mat le Note: emergency words, already over
- **m144** (everyday, en) asks: true → false. we're boarding in 10 min where are you
- **m180** (tricky, en) asks: true → false. i'm outside Note: implies 'come out now'; no question or verb aimed at him
- **m181** (tricky, en) asks: false → true. why am i like this 😭 Note: rhetorical question
- **m201** (tricky, hi) asks: true → false. भाई जल्दी call कर, urgent है Note: Devanagari and Latin in one message
- **m208** (tricky, en) asks: false → true. what was i even thinking lol Note: rhetorical question
- **m221** (tricky, en) asks: false → true. remember when we got stuck in the rain at marine drive? good times Note: question about the past, needs no answer
- **m293** (tricky, hinglish) asks: false → true. kaun karta hai aisa yaar 🙄 Note: rhetorical question
- **m011** (tricky, en) ack: true → false. lol that was urgent 😂 Note: 'urgent' is about something past; just laughing
- **m014** (tricky, hinglish) ack: true → false. lol woh toh urgent tha 😂 Note: 'urgent' is about the past; just laughing
- **m037** (tricky, es) ack: true → false. jajaja eso sí que fue urgente 😂 Note: 'urgent' is about the past; just laughing
- **m199** (tricky, hinglish) ack: true → false. chal theek hai, jaisi teri marzi Note: sulky, but only an acknowledgement

## MoneyParser

Each line: case id, difficulty and language; expected → got; what the case was.

- **a003** (tricky, hinglish) INR 1500.0 → no amount. 1.5k bhej de bhai, trip ka Note: 'k' shorthand, no currency written
- **a026** (tricky, en) INR 640.0 → no amount. 2 tickets for the 7pm show cost me 640, send ur half Note: no currency written; count and time come first

## Guess at unlock

Each line: case id, difficulty and language; expected → got; what the case was.

- **g037** (tricky, en) `none` → `b` “Here to reply to Rishi in Wing B Hostel 🏠?”; a, 15 min: Gmail · [email] · Myntra · LAST CHANCE!! Your cart is about to expire ⏰ | b, 5 min: WhatsApp · [msg] · Wing B Hostel 🏠 · Rishi: warden is losing it. unlock: chip for reply:com.whatsapp|Wing B Hostel 🏠, score 0.92 (recent 0.98 · who 0.40 from new · due 1.00 · history 0.50 · boost 0.15) of 1 open Note: Loud, urgent-sounding group message that doesn't name him, plus an urgent-sounding promo email.
- **g047** (tricky, en) `none` → `a` “Here to pay your Airtel Postpaid bill?”; a, 180 min: Messages · [msg] · VM-AIRTEL · Dear Customer, your Airtel Postpaid bill of Rs.599.00 for 98XXXXX231 is due today, 27-Oct-26. Pay now on the Airtel Thanks app to avoid late fee. | b, 60 min: Airtel Thanks · Payment successful ✅ · ₹599 paid for Airtel Postpaid 98XXXXX231 via Autopay. Thank you!. unlock: chip for payment:com.google.android.apps.messaging|VM-AIRTEL, score 0.81 (recent 0.61 · who 0.50 from new · due 1.00 · history 0.50 · boost 0.15) of 1 open Note: Bill due today was already paid by autopay.
