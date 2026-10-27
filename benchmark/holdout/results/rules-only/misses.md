# Benchmark misses

Every case the rules got wrong. Written by `./gradlew :core:benchmark`; the cases are in `benchmark/data`.

## Classifier

Each line: case id, difficulty and language; expected → got; what the case was.

- **h-n007** (tricky, hinglish) `none` → `reply` (opened “Reply to +91 70423 18856”: urgent, time). Messages · [msg] · +91 70423 18856 · Priya grahak, aapka bijli connection aaj raat 9:30 baje kaat diya jayega kyunki pichhle mahine ka bill update nahi hua hai. Turant electricity officer se 9832145076 pe sampark karein. Note: power-cut scam from a mobile number
- **h-n034** (tricky, en) `bill` → `reply` (opened “Reply to ACT Fibernet”: asks, time). WhatsApp · [msg] · ACT Fibernet · Hi Pranav, your ACT Fibernet bill of ₹ 1,178.82 for November is generated. Due date: 05-Nov-2026. Pay now to avoid service interruption. Note: company bill arriving over WhatsApp with a reply box
- **h-n040** (tricky, en) `payment_request` → `none` (skip: not a loop). Google Pay · Reminder: Nidhi Kapoor's request · ₹300 for Uber to airport is still pending. Pay now Note: payment-app nudge about an older request
- **h-n050** (tricky, en) `none` → `email` (opened “Reply to Ravi Iyer's email”: urgent, asks). Outlook · [email] · Ravi Iyer · Automatic reply: Lab access for weekend Note: auto-reply from a person's address
- **h-n073** (everyday, en) `booked` → `none` (skip: ongoing). Uber · [status] · Ramesh is on the way · White Maruti Swift Dzire · MH 02 FG 4821 · Arriving in 4 min
- **h-n082** (everyday, en) `booked` → `reply` (opened “Reply to VM-IRCTCi”: time). Messages · [msg] · VM-IRCTCi · PNR:4521876390,TRN:12951,DOJ:14-11-26,SL,MMCT-NDLS,DP:16:35,Boarding at MMCT,PRANAV SAVLA,S6 52,Fare:785.00,SC:17.70+PG CHGS. Download ticket from IRCTC app.
- **h-n089** (tricky, en) `payment_request` → `none` (skip: not a loop). Splitwise · Aditi Joshi sent you a reminder · You owe Aditi Joshi ₹1,240.50 for “Lonavala villa”. Settle up? Note: settle-up reminder from an expense-splitting app
- **h-n094** (everyday, hinglish) `bill` → `reply` (opened “Reply to VM-ViCARE”). Messages · [msg] · VM-ViCARE · Aapka Vi postpaid bill Rs 499 ka 09-Nov tak due hai. Late fee se bachne ke liye abhi pay karein: vi.app/pay
- **h-n099** (everyday, hi) `paid` → `none` (skip: not a loop). Paytm · भुगतान सफल · ₹200 का भुगतान Sharma General Store को सफलतापूर्वक किया गया
- **h-n116** (tricky, en) `none` → `reminder` (opened “Your streak is in danger! 🔥”: reminder). Duolingo · [reminder] · Your streak is in danger! 🔥 · Hi Pranav, it's Duo. Just 5 minutes keeps your 212-day streak alive. Note: app nudge in the reminder category, not his reminder
- **h-n136** (tricky, en) `reply` → `none` (group chatter, not for you). Telegram · [msg] · GDSC Core Team · Shreya: @pranavsavla can you take the 4pm Flutter session? Rohit is down with fever Note: group mention by username handle
- **h-n151** (everyday, en) `none` → `reply` (opened “Reply to Placement Updates 2027”: time). Telegram · [msg] · Placement Updates 2027 · 📢 Amazon SDE Intern drive: registrations close tonight 11:59 PM. Link in the pinned message.
- **h-n156** (tricky, hinglish) `none` → `reply` (opened “Reply to Neha”: asks). WhatsApp · [msg] · Neha · nvm ho gaya, Ishaan ne help kar di. thanks! Note: later message cancels the ask
- **h-n180** (tricky, en) `bill` → `none` (skip: text from a business). Messages · [msg] · VM-ACTFBR · ACT Fibernet: Bill amt INR 1,178.82 for A/c 1009XX56 due 03Nov. Avoid disconnection, pay at act.ly/pay Note: terse, INR prefix with paise
- **h-n196** (tricky, en) `booked` → `none` (skip: text from a business). Messages · [msg] · AD-IRCTCI · PNR:4521873390,TRN:12951,DOJ:14-11-26,3A,BCT-NDLS,DP:16:35,Pranav Savla+1,B3 21,B3 24,Fare:4510,SC:35.4+PG CHGS Note: terse PNR SMS with a fare, not a bill
- **h-n199** (tricky, en) `email` → `none` (skip: automated email). Gmail · [email] · Kunal Joshi via CS 2027 · Lab slot clash – Pranav, Isha Note: mailing-list email that names him
- **h-n207** (everyday, hinglish) `none` → `booked` (booked). Zomato · Order on the way 🛵 · Ravi aapka order leke nikal chuke hain. 12 min mein pahunchenge.
- **h-n212** (everyday, en) `booked` → `none` (skip: ongoing). Uber · Suresh is on the way · White Swift Dzire · MH02 FG 4471 · arriving in 4 min
- **h-n215** (tricky, hinglish) `bill` → `reply` (opened “Reply to VM-ViCARE”). Messages · [msg] · VM-ViCARE · Vi: Aapka postpaid bill Rs 499 ka hai, 01/11 tak pay karein: vi.app/pay. Agar pay kar diya hai to ignore karein. Note: Hinglish bill with an 'ignore if paid' line
- **h-n241** (tricky, hinglish) `none` → `email` (opened “Reply to Ritika from Swiggy One's email”: asks). Gmail · [email] · Ritika from Swiggy One · Pranav, Diwali ki taiyaari ho gayi? 🪔 Note: marketing email signed by a person, asks a question
- **h-n245** (tricky, hinglish) `none` → `reply` (opened “Reply to Ajay in Cricket Sunday 🏏”: mentions you). WhatsApp · [msg] · Cricket Sunday 🏏 · Ajay: @Pranav Mhatre tu kal keeper hai, 7 baje ground pe aa jaana Note: a different Pranav is tagged
- **h-n253** (everyday, en) `payment_request` → `none` (skip: not a loop). Splitwise · Meera Iyer sent you a reminder · You owe Meera ₹1,240.00 for "Lonavala trip". Settle up now?
- **h-n256** (tricky, hi) `paid` → `none` (skip: text from a business). Messages · [msg] · VM-SBIUPI · प्रिय यूज़र, आपके खाते XX2290 से ₹1,500 UPI द्वारा राहुल शर्मा को भेजे गए (संदर्भ 630211458812)। यदि आपने नहीं भेजे तो 1800111109 पर कॉल करें -SBI Note: Hindi bank debit alert
- **h-n262** (everyday, hi) `bill` → `none` (skip: text from a business). Messages · [msg] · JM-SBICRD · प्रिय कार्डधारक, आपके SBI कार्ड XX6631 का कुल बकाया ₹6,720 है। भुगतान की अंतिम तिथि 08-11-2026 है।
- **h-n269** (tricky, en) `none` → `reminder` (opened “Diwali”: event). Calendar · [event] · Diwali · All day · Holidays in India Note: subscribed holiday calendar, not his own event
- **h-n273** (tricky, en) `email` → `none` (skip: email asks nothing). Gmail · [email] · Prof. Mehta · Re: Project extension request Note: Re: thread that quotes his own earlier mail
- **h-n285** (tricky, en) `reply` → `none` (group chatter, not for you). Telegram · [msg] · ML Study Circle · Devansh: does your code handle the case where the batch is empty? Note: app flags a reply to his message as a mention; no name in text
- **h-n299** (everyday, de) `email` → `none` (skip: email asks nothing). Gmail · [email] · Lena Hoffmann · Ihre Bewerbung – fehlende Unterlagen

## Classifier names and amounts

Each line: case id, difficulty and language; expected → got; what the case was.

- **h-n237** (tricky, en) who “Vikram Singh” → “Google Pay”. Google Pay · Reminder: pay Vikram Singh · Vikram Singh's request for ₹1,099.50 expires today Note: reminder of an open request; paise

## Urgency

Each line: case id, difficulty and language; expected → got; what the case was.

- **h-m064** (everyday, en) urgent: true → false. my car broke down on the highway near lonavala and my phone is at 3%. can you come or send someone
- **h-m066** (tricky, en) urgent: false → true. Forwarded many times URGENT NEWS: Petrol prices to rise by ₹10 from midnight tonight Note: forwarded rumour labelled urgent
- **h-m073** (everyday, en) urgent: true → false. theres a guy following me rn, can u stay on call with me till i get home
- **h-m111** (tricky, en) urgent: true → false. flight got cancelled, stuck at the airport, no hotels anywhere, i'm literally freaking out Note: distress with no explicit ask
- **h-m123** (tricky, en) urgent: true → false. is this still your number? its ananya. theres been a fire in our building and i need a place to stay tonight Note: starts like small talk, then an emergency
- **h-m130** (tricky, hinglish) urgent: false → true. aaj prof ne urgent urgent bolke assignment diya aur khud hi leave pe chale gaye 🤡 Note: "urgent" in a story
- **h-m152** (tricky, en) urgent: false → true. where are you rn, want to grab chai? Note: "where are you" but casual, not urgent
- **h-m154** (everyday, en) urgent: true → false. need u to come to the police station in andheri, they wont let me go without someone
- **h-m157** (tricky, en) urgent: true → false. 911 911 911 call me Note: slang for emergency
- **h-m162** (tricky, en) urgent: true → false. i know ur in class but this cant wait. call me the second u see this Note: pressing without the word urgent
- **h-m170** (tricky, en) urgent: false → true. omg URGENT update: the cat knocked over my coffee again 😾 Note: says urgent, only a joke
- **h-m180** (tricky, hinglish) urgent: true → false. nani nahi rahi 😭 Note: bad news and distress, no explicit ask
- **h-m184** (everyday, hinglish) urgent: true → false. bhai location bhej abhi, gaadi kharab ho gayi hai highway pe
- **h-m186** (tricky, hinglish) urgent: false → true. urgent wala kaam ho gaya, ab tension nahi 😌 Note: "urgent" for something already done
- **h-m189** (everyday, hinglish) urgent: false → true. aaj office se jaldi nikal gaya, ghar pe hi hu
- **h-m198** (everyday, bn) urgent: true → false. এক্ষুনি ফোন কর, বাবা পড়ে গেছে
- **h-m214** (tricky, en) urgent: true → false. the doctor just said they need to operate on mom tonight Note: emergency news, no explicit ask
- **h-m217** (everyday, en) urgent: true → false. pls pick up its about grandma
- **h-m218** (tricky, en) urgent: false → true. URGENT 🚨🚨 zara sale is live, 70% off on everything Note: says urgent about a sale
- **h-m224** (everyday, en) urgent: true → false. im lost on the trail near the fort, battery dying, sending location, please call the forest office
- **h-m232** (everyday, hinglish) urgent: true → false. bhai sun mera phone band hone wala hai, mujhe dadar station se pick kar le please
- **h-m233** (tricky, hinglish) urgent: false → true. Forwarded URGENT: kal se sab bank 5 din band rahenge 😱 Note: forwarded rumour labelled urgent
- **h-m239** (tricky, hinglish) urgent: false → true. urgent kuch nahi hai bas yaad dilana tha ki kal mummy ka birthday hai, cake order kar dena Note: "urgent" denied, request at the end
- **h-m273** (everyday, en) urgent: false → true. On my way to the hospital for my shift, talk later
- **h-m275** (tricky, en) urgent: false → true. Is it urgent? Nah. Is it important? Kinda. Read the doc I sent before tmrw's meeting Note: rhetorical questions, denies urgency, then a request
- **h-m281** (everyday, hinglish) urgent: true → false. bhai mere result mein kuch gadbad hai, fail dikha raha hai, abhi call kar please
- **h-m286** (tricky, hinglish) urgent: true → false. bhai flat mein aag lag gayi, sab bahar aa gaye hain Note: emergency news, no explicit ask
- **h-m298** (everyday, es) urgent: true → false. contesta porfa!!! mi abuela está en urgencias
- **h-m059** (everyday, en) asks: true → false. 📷 which one should i wear tonight 1 or 2
- **h-m067** (tricky, en) asks: true → false. you coming or not Note: question without a question mark
- **h-m068** (tricky, en) asks: false → true. who even schedules an exam on a saturday?? like seriously Note: rhetorical question
- **h-m088** (tricky, hinglish) asks: true → false. acha thanks, aur haan kal 10 baje tak aa jana Note: thanks first, then a request
- **h-m099** (everyday, fr) asks: true → false. Réponds-moi stp, c'est grave, Léa a eu un accident
- **h-m103** (tricky, en) asks: true → false. Urgent!! need ur netflix password before the episode drops 😂 Note: says urgent, only a joke
- **h-m118** (tricky, en) asks: true → false. k but did u book the tickets or not Note: starts like an ack but asks
- **h-m122** (tricky, en) asks: true → false. Happy birthday!! 🎂 party tonight or what Note: birthday wish that also asks
- **h-m125** (tricky, en) asks: false → true. why is it that every time i clean my room it gets messier the next day 😭 Note: rhetorical question
- **h-m145** (tricky, hi) asks: true → false. मम्मी पूछ रही थीं कि तुम दिवाली पर आओगे या नहीं Note: reported question that is meant for him
- **h-m147** (everyday, mr) asks: true → false. लवकर घरी ये, आजोबा पडले आहेत
- **h-m155** (tricky, en) asks: true → false. Not urgent!! just wanted to ask if you're coming sat Note: says not urgent, still asks
- **h-m164** (tricky, en) asks: false → true. sorry long msg but i need to rant. my flatmate ate my food again, didnt clean the kitchen again, and then had the audacity to complain that im too loud on calls. like?? im so done. anyway dont worry about it lol Note: long rant; its "like??" is not a question to him
- **h-m168** (tricky, en) asks: true → false. you have the keys right Note: question shaped as a statement
- **h-m174** (tricky, en) asks: true → false. Mom says call her Note: relayed request
- **h-m179** (everyday, hinglish) asks: true → false. kal 7 baje station pe milna, late mat hona
- **h-m185** (everyday, hinglish) asks: true → false. mujhe bhi le chal na kal
- **h-m207** (tricky, en) asks: true → false. lemme know Note: two-word request
- **h-m208** (everyday, en) asks: false → true. watching the match at rahul's, it's insane
- **h-m211** (tricky, en) asks: false → true. cant believe its almost november. where did this year even go Note: rhetorical question
- **h-m221** (tricky, en) asks: true → false. I need the 2000 back by friday, rent's due Note: statement of need that asks for money back
- **h-m244** (tricky, hi) asks: false → true. कौन इतनी गर्मी में शादी रखता है? 🥵 Note: rhetorical question
- **h-m249** (tricky, de) asks: true → false. Kannst du mir kurz Bescheid geben, ob du Samstag kommst Note: question without a question mark
- **h-m279** (tricky, hinglish) asks: true → false. sun na, ek kaam hai tujhse Note: announces a request
- **h-m280** (tricky, hinglish) asks: true → false. yaar aaj ka din itna bekaar tha, subah alarm nahi baja, phir auto wale ne 200 maange, office mein boss ne daanta aur ab ghar aake dekha toh light nahi hai 😩 tu free hai toh shaam ko chai pe chal Note: long vent ending in an invitation
- **h-m286** (tricky, hinglish) asks: false → true. bhai flat mein aag lag gayi, sab bahar aa gaye hain Note: emergency news, no explicit ask
- **h-m298** (everyday, es) asks: true → false. contesta porfa!!! mi abuela está en urgencias
- **h-m300** (tricky, mr) asks: true → false. उद्या येताना माझं पुस्तक घेऊन ये Note: request as an instruction
- **h-m039** (everyday, hinglish) ack: true → false. chal thik hai kal milte
- **h-m113** (everyday, en) ack: true → false. thank u so much, means a lot 🥹
- **h-m132** (everyday, hinglish) ack: true → false. haha pagal hai tu
- **h-m139** (everyday, hinglish) ack: true → false. chal koi na
- **h-m259** (everyday, en) ack: true → false. thanks for the help today man, really appreciate it 🙏
- **h-m283** (everyday, hinglish) ack: true → false. arey koi baat nahi
- **h-m294** (everyday, hi) ack: true → false. बहुत अच्छा बेटा, खुश रहो

## MoneyParser

Each line: case id, difficulty and language; expected → got; what the case was.

- **h-a041** (tricky, hinglish) INR 300.0 → no amount. bhai 300 bhej de kal ka auto ka Note: bare number, rupees implied
- **h-a046** (tricky, hinglish) INR 150000.0 → no amount. 1.5 lakh ka laptop le liya usne 😳 Note: lakh in words
- **h-a047** (tricky, en) INR 2000.0 → no amount. ok send 2k for the trip Note: k suffix, no currency
- **h-a052** (tricky, en) INR 1250.0 → no amount. Paid 1,250/- for the cab Note: /- suffix means rupees
- **h-a053** (tricky, hi) INR 500.0 → no amount. मुझे पाँच सौ रुपये चाहिए कल तक Note: amount in Hindi words
- **h-a054** (tricky, hi) INR 1250.0 → no amount. ₹१,२५० का बिल आया है Note: Devanagari digits
- **h-a067** (tricky, hinglish) INR 200.0 → no amount. kal 5 baje milte hai, 200 ka recharge karwa dena mera Note: time number before the amount
- **h-a070** (tricky, hinglish) INR 2.0E7 → no amount. 2 crore ka flat hai bhai, EMI hi 1.5 lakh mahina Note: crore in words, two amounts
- **h-a073** (tricky, mr) INR 500.0 → no amount. मला ५०० रुपये पाठव ना Note: Marathi, Devanagari digits
- **h-a074** (tricky, gu) INR 200.0 → no amount. મને ₹૨૦૦ મોકલ Note: Gujarati digits
- **h-a076** (tricky, ar) USD 200.0 → no amount. حولتلك 200 دولار Note: currency in Arabic words

## Guess at unlock

Each line: case id, difficulty and language; expected → got; what the case was.

- **h-g024** (tricky, en) `a` → `none`; a, 360 min: Messages · [msg] · VM-HDFCBK · Payment of Rs.12,430.00 is due on HDFC Bank Credit Card ending 4521 on 28-OCT-26. Minimum amount due Rs.620.00. Pay now: hdfcbk.io/cc. Ignore if paid. | b, 3 min: WhatsApp · [msg] · Varun · hahaha 😂 | c, 9 min: WhatsApp · [msg] · Football 5s ⚽ · Arnav: 🔥🔥. unlock: no chip, best payment:com.google.android.apps.messaging|VM-HDFCBK scored 0.67 (recent 0.37 · who 0.50 from new · due 0.75 · history 0.50 · boost 0.15), needs > 0.7 Note: 6-hour-old card bill due tomorrow vs fresh laughter and group chatter
- **h-g046** (everyday, en) `a` → `none`; a, 300 min: Messages · [msg] · VM-LICIND · Dear Customer, premium of Rs.8,450/- for Policy No.9123XXXX is due on 02/11/2026. Pay online at licindia.in or via LIC Pay Direct. | b, 10 min: WhatsApp · [msg] · Dhruv · ok. unlock: no chip, best payment:com.google.android.apps.messaging|VM-LICIND scored 0.69 (recent 0.43 · who 0.50 from new · due 0.75 · history 0.50 · boost 0.15), needs > 0.7
- **h-g059** (tricky, hinglish) `b` → `a` “Here to reply to Kabir?”; a, 1 min: WhatsApp · [msg] · Kabir · bhai ye reel dekh 😂😂 instagram.com/reel/C9xk2Lp | b, 180 min: Google Pay · Mihir Joshi requested ₹2,400 · PVR + khana, tera share · Tap to pay. unlock: chip for reply:com.whatsapp|Kabir, score 0.81 (recent 0.97 · who 0.50 from new · due 0.50 · history 0.50 · boost 0.15) of 2 open Note: fresh meme vs a 3-hour-old payment request
- **h-g070** (tricky, hi) `none` → `a` “Here to reply to +91 87654 32109?”; a, 25 min: Messages · [msg] · +91 87654 32109 · प्रिय उपभोक्ता, आपका बिजली कनेक्शन आज रात 9:30 बजे काट दिया जाएगा क्योंकि पिछले महीने का बिल अपडेट नहीं है। तुरंत बिजली अधिकारी से संपर्क करें 9876543210 | b, 50 min: Paytm · [promo] · ₹100 cashback is waiting! 🎁 · Pay your electricity bill with Paytm UPI and win up to ₹100 cashback.. unlock: chip for reply:com.google.android.apps.messaging|+91 87654 32109, score 0.93 (recent 0.93 · who 0.50 from new · due 1.00 · history 0.50 · boost 0.15) of 1 open Note: Hindi electricity-cut scam from a mobile number
