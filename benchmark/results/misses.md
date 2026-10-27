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
- **m110** (everyday, en) urgent: true → false. someone hit my scooty, I'm ok but shaking. can u come
- **m204** (tricky, en) urgent: true → false. URGENT: reply by 6 today or ur seat in the workshop goes to the waitlist Note: deadline within hours, presses for a reply
- **m252** (tricky, hi) urgent: false → true. जल्दी आओ!!! केक खत्म होने वाला है 😂 Note: playful 'come quick'; not a real emergency
- **m264** (everyday, en) urgent: true → false. the quiz link closes in 5 min did u submit??
- **m265** (everyday, hinglish) urgent: true → false. bhai 500 bhej de abhi, wallet ghar pe bhool gaya
- **m020** (everyday, en) asks: true → false. urgent - prof wants to see you in his cabin right now
- **m104** (tricky, hi) asks: false → true. कौन करता है ऐसा? 🙄 Note: rhetorical question
- **m111** (tricky, en) asks: false → true. 📷 Photo Note: media only, no text
- **m125** (tricky, en) asks: false → true. who even schedules a test on a sunday 🙄 Note: rhetorical question
- **m127** (tricky, hinglish) asks: false → true. kya hi bolu ab 🤦 Note: rhetorical
- **m180** (tricky, en) asks: true → false. i'm outside Note: implies 'come out now'; no question or verb aimed at him
- **m254** (tricky, en) asks: true → false. Thanks for yesterday! Btw when's the next practice? Note: thanks, then a question
- **m259** (everyday, en) asks: true → false. happy birthday!! 🎉 party when?
- **m260** (tricky, hinglish) asks: false → true. usne poocha 'tu aa raha hai kya?' aur phir khud hi nahi aaya 😂 Note: quoted question, not asked of him
- **m290** (tricky, hinglish) asks: false → true. mujhe kya pata tha ki woh aisa karega? chhod, jaane de Note: rhetorical question
- **m293** (tricky, hinglish) asks: false → true. kaun karta hai aisa yaar 🙄 Note: rhetorical question
- **m011** (tricky, en) ack: true → false. lol that was urgent 😂 Note: 'urgent' is about something past; just laughing
- **m014** (tricky, hinglish) ack: true → false. lol woh toh urgent tha 😂 Note: 'urgent' is about the past; just laughing
- **m037** (tricky, es) ack: true → false. jajaja eso sí que fue urgente 😂 Note: 'urgent' is about the past; just laughing

## MoneyParser

Each line: case id, difficulty and language; expected → got; what the case was.

- **a003** (tricky, hinglish) INR 1500.0 → no amount. 1.5k bhej de bhai, trip ka Note: 'k' shorthand, no currency written
- **a026** (tricky, en) INR 640.0 → no amount. 2 tickets for the 7pm show cost me 640, send ur half Note: no currency written; count and time come first

## Guess at unlock

Each line: case id, difficulty and language; expected → got; what the case was.

- **g037** (tricky, en) `none` → `b` “Here to reply to Rishi in Wing B Hostel 🏠?”; a, 15 min: Gmail · [email] · Myntra · LAST CHANCE!! Your cart is about to expire ⏰ | b, 5 min: WhatsApp · [msg] · Wing B Hostel 🏠 · Rishi: warden is losing it. unlock: chip for reply:com.whatsapp|Wing B Hostel 🏠, score 0.92 (recent 0.98 · who 0.40 from new · due 1.00 · history 0.50 · boost 0.15) of 1 open Note: Loud, urgent-sounding group message that doesn't name him, plus an urgent-sounding promo email.
- **g047** (tricky, en) `none` → `a` “Here to pay your Airtel Postpaid bill?”; a, 180 min: Messages · [msg] · VM-AIRTEL · Dear Customer, your Airtel Postpaid bill of Rs.599.00 for 98XXXXX231 is due today, 27-Oct-26. Pay now on the Airtel Thanks app to avoid late fee. | b, 60 min: Airtel Thanks · Payment successful ✅ · ₹599 paid for Airtel Postpaid 98XXXXX231 via Autopay. Thank you!. unlock: chip for payment:com.google.android.apps.messaging|VM-AIRTEL, score 0.81 (recent 0.61 · who 0.50 from new · due 1.00 · history 0.50 · boost 0.15) of 1 open Note: Bill due today was already paid by autopay.

## Reply strip

Each line: case id, difficulty and language; where nothing on the strip makes sense; what the case was.

- **m012** (tricky, en) “😊” / “You're welcome” / “☺”. wow thanks for the help 🙃 Note: sarcastic 'thanks' is not an acknowledgement
- **m026** (tricky, hinglish) “Haan” / “Nahi” / “Ek min, check karna padega”. tu trip pe kahan ja raha hai? Note: 'kahan' in a casual question
- **m052** (tricky, en) no strip (no confident reply). my sister is in the hospital, it's a girl!! 🎉 Note: hospital, but good news
- **m057** (everyday, ar) “Yes” / “No” / “Let me check”. وين انت؟ الكل ينتظرك
- **m063** (everyday, en) no strip (no confident reply). new trailer is out https://youtu.be/8ugaeA-nMTc
- **m074** (everyday, en) “Yes” / “Okay” / “No”. class has started where r u, sir is taking attendance
- **m077** (tricky, hi) “हाँ” / “नहीं” / “एक मिनट, देखना पड़ेगा”. उसने पूछा 'क्या तुम आ रहे हो?' और फिर खुद ही नहीं आया Note: quoted question, not asked of him
- **m088** (everyday, hinglish) “Theek hai, ho jayega” / “Done” / “Abhi nahi ho payega”. kya kar raha hai
- **m089** (everyday, en) no strip (no confident reply). https://youtu.be/dQw4w9WgXcQ
- **m106** (everyday, en) “Okay” / “Yes” / “Yeah”. which bus goes to campus from dadar
- **m126** (everyday, en) no strip (no confident reply). dad's bp is really high, taking him to hospital
- **m127** (tricky, hinglish) “Okay, will do” / “Done” / “Can't right now”. kya hi bolu ab 🤦 Note: rhetorical
- **m148** (everyday, en) “Okay” / “Yes” / “😞”. Pranav where are you? the client call started 5 min ago
- **m165** (everyday, hi) “अभी कॉल करते हैं” / “अभी बात नहीं हो सकती” / “5 मिनट दो”. शाम को फ़ोन करना
- **m178** (tricky, en) “Okay” / “Yes” / “No”. plsssss reply Note: repeated letters, pressing for a reply
- **m200** (tricky, hinglish) no strip (no confident reply). ye dekh 😂 https://www.instagram.com/reel/Cx12abcDEF/ Note: 'ye dekh' (look at this) only shares a link
- **m210** (tricky, en) “Okay” / “Oh” / “Oh, okay”. This message was deleted Note: deleted message
- **m230** (everyday, hinglish) “Accha” / “Baad mein baat karte hain” / “Call karoon?”. bhai result aa gaya, pass ho gaya 🎉
- **m234** (tricky, hi) “हाँ” / “नहीं” / “एक मिनट, देखना पड़ेगा”. तुम ट्रिप पर कहाँ जा रहे हो? Note: 'kahan' in a casual question
- **m251** (tricky, hinglish) “Accha” / “Baad mein baat karte hain” / “Call karoon?”. haan haan bahut help ki tune 🙃 Note: sarcasm, not an acknowledgement
- **m253** (everyday, hinglish) “Got it” / “Will reply soon” / “Can I call you?”. mummy ne aaj pav bhaji banayi
- **m255** (tricky, en) no strip (no confident reply). honestly I'm so done with this group project. nobody replies, nobody does their part, and then they act surprised when the deadline comes. I'm just gonna do it myself like always Note: long venting, no ask
- **m260** (tricky, hinglish) “Haan” / “Nahi” / “Ek min, check karna padega”. usne poocha 'tu aa raha hai kya?' aur phir khud hi nahi aaya 😂 Note: quoted question, not asked of him
- **m290** (tricky, hinglish) “Ek min, check karna padega” / “Pata nahi” / “Thodi der mein bolte hain”. mujhe kya pata tha ki woh aisa karega? chhod, jaane de Note: rhetorical question
- **m295** (tricky, en) no strip (no confident reply). urgent!!! 🚨 look at this cat https://youtu.be/J---aiyznGQ Note: fake urgency on a meme link
