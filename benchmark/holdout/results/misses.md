# Benchmark misses

Every case the rules got wrong. Written by `./gradlew :core:benchmark`; the cases are in `benchmark/data`.

## Classifier

Each line: case id, difficulty and language; expected → got; what the case was.

- **h-n007** (tricky, hinglish) `none` → `reply` (opened “Reply to +91 70423 18856”: time, model). Messages · [msg] · +91 70423 18856 · Priya grahak, aapka bijli connection aaj raat 9:30 baje kaat diya jayega kyunki pichhle mahine ka bill update nahi hua hai. Turant electricity officer se 9832145076 pe sampark karein. Note: power-cut scam from a mobile number
- **h-n034** (tricky, en) `bill` → `reply` (opened “Reply to ACT Fibernet”: time, model). WhatsApp · [msg] · ACT Fibernet · Hi Pranav, your ACT Fibernet bill of ₹ 1,178.82 for November is generated. Due date: 05-Nov-2026. Pay now to avoid service interruption. Note: company bill arriving over WhatsApp with a reply box
- **h-n040** (tricky, en) `payment_request` → `none` (skip: not a loop). Google Pay · Reminder: Nidhi Kapoor's request · ₹300 for Uber to airport is still pending. Pay now Note: payment-app nudge about an older request
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
- **h-n193** (tricky, en) `email` → `none` (skip: email asks nothing). Outlook · [email] · kavita.n@iitb.ac.in · Re: TA hours Thursday Note: sender shown only as an address
- **h-n196** (tricky, en) `booked` → `none` (skip: text from a business). Messages · [msg] · AD-IRCTCI · PNR:4521873390,TRN:12951,DOJ:14-11-26,3A,BCT-NDLS,DP:16:35,Pranav Savla+1,B3 21,B3 24,Fare:4510,SC:35.4+PG CHGS Note: terse PNR SMS with a fare, not a bill
- **h-n199** (tricky, en) `email` → `none` (skip: automated email). Gmail · [email] · Kunal Joshi via CS 2027 · Lab slot clash – Pranav, Isha Note: mailing-list email that names him
- **h-n207** (everyday, hinglish) `none` → `booked` (booked). Zomato · Order on the way 🛵 · Ravi aapka order leke nikal chuke hain. 12 min mein pahunchenge.
- **h-n212** (everyday, en) `booked` → `none` (skip: ongoing). Uber · Suresh is on the way · White Swift Dzire · MH02 FG 4471 · arriving in 4 min
- **h-n215** (tricky, hinglish) `bill` → `reply` (opened “Reply to VM-ViCARE”). Messages · [msg] · VM-ViCARE · Vi: Aapka postpaid bill Rs 499 ka hai, 01/11 tak pay karein: vi.app/pay. Agar pay kar diya hai to ignore karein. Note: Hinglish bill with an 'ignore if paid' line
- **h-n241** (tricky, hinglish) `none` → `email` (opened “Reply to Ritika from Swiggy One's email”: asks). Gmail · [email] · Ritika from Swiggy One · Pranav, Diwali ki taiyaari ho gayi? 🪔 Note: marketing email signed by a person, asks a question
- **h-n245** (tricky, hinglish) `none` → `reply` (opened “Reply to Ajay in Cricket Sunday 🏏”: asks, model, mentions you). WhatsApp · [msg] · Cricket Sunday 🏏 · Ajay: @Pranav Mhatre tu kal keeper hai, 7 baje ground pe aa jaana Note: a different Pranav is tagged
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
- **h-m073** (everyday, en) urgent: true → false. theres a guy following me rn, can u stay on call with me till i get home
- **h-m123** (tricky, en) urgent: true → false. is this still your number? its ananya. theres been a fire in our building and i need a place to stay tonight Note: starts like small talk, then an emergency
- **h-m130** (tricky, hinglish) urgent: false → true. aaj prof ne urgent urgent bolke assignment diya aur khud hi leave pe chale gaye 🤡 Note: "urgent" in a story
- **h-m152** (tricky, en) urgent: false → true. where are you rn, want to grab chai? Note: "where are you" but casual, not urgent
- **h-m156** (everyday, en) urgent: false → true. can i borrow ur bike for an hour
- **h-m157** (tricky, en) urgent: true → false. 911 911 911 call me Note: slang for emergency
- **h-m162** (tricky, en) urgent: true → false. i know ur in class but this cant wait. call me the second u see this Note: pressing without the word urgent
- **h-m174** (tricky, en) urgent: false → true. Mom says call her Note: relayed request
- **h-m175** (everyday, en) urgent: true → false. The landlord is here and says he's changing the locks today unless we pay. Can you come home now?
- **h-m180** (tricky, hinglish) urgent: true → false. nani nahi rahi 😭 Note: bad news and distress, no explicit ask
- **h-m184** (everyday, hinglish) urgent: true → false. bhai location bhej abhi, gaadi kharab ho gayi hai highway pe
- **h-m186** (tricky, hinglish) urgent: false → true. urgent wala kaam ho gaya, ab tension nahi 😌 Note: "urgent" for something already done
- **h-m189** (everyday, hinglish) urgent: false → true. aaj office se jaldi nikal gaya, ghar pe hi hu
- **h-m209** (tricky, en) urgent: true → false. ok this is actually urgent, prof moved the exam to TODAY 2pm, are you coming?? Note: "urgent" that really is urgent
- **h-m214** (tricky, en) urgent: true → false. the doctor just said they need to operate on mom tonight Note: emergency news, no explicit ask
- **h-m218** (tricky, en) urgent: false → true. URGENT 🚨🚨 zara sale is live, 70% off on everything Note: says urgent about a sale
- **h-m224** (everyday, en) urgent: true → false. im lost on the trail near the fort, battery dying, sending location, please call the forest office
- **h-m268** (tricky, en) urgent: true → false. hey sorry to bother u this late, my sister's not home yet and she's not picking up. is she with u guys? Note: polite tone, real worry
- **h-m271** (everyday, en) urgent: true → false. my landlord just cut off the water and power. can i crash at urs tonight? need to know asap
- **h-m281** (everyday, hinglish) urgent: true → false. bhai mere result mein kuch gadbad hai, fail dikha raha hai, abhi call kar please
- **h-m288** (tricky, hinglish) urgent: false → true. mummy pooch rahi hai tu kab aayega Note: relayed question meant for him
- **h-m298** (everyday, es) urgent: true → false. contesta porfa!!! mi abuela está en urgencias
- **h-m060** (tricky, en) asks: false → true. 📷 Photo Note: bare photo, nothing asked, not a reaction
- **h-m068** (tricky, en) asks: false → true. who even schedules an exam on a saturday?? like seriously Note: rhetorical question
- **h-m088** (tricky, hinglish) asks: true → false. acha thanks, aur haan kal 10 baje tak aa jana Note: thanks first, then a request
- **h-m103** (tricky, en) asks: true → false. Urgent!! need ur netflix password before the episode drops 😂 Note: says urgent, only a joke
- **h-m122** (tricky, en) asks: true → false. Happy birthday!! 🎂 party tonight or what Note: birthday wish that also asks
- **h-m139** (everyday, hinglish) asks: false → true. chal koi na
- **h-m155** (tricky, en) asks: true → false. Not urgent!! just wanted to ask if you're coming sat Note: says not urgent, still asks
- **h-m168** (tricky, en) asks: true → false. you have the keys right Note: question shaped as a statement
- **h-m174** (tricky, en) asks: true → false. Mom says call her Note: relayed request
- **h-m179** (everyday, hinglish) asks: true → false. kal 7 baje station pe milna, late mat hona
- **h-m185** (everyday, hinglish) asks: true → false. mujhe bhi le chal na kal
- **h-m211** (tricky, en) asks: false → true. cant believe its almost november. where did this year even go Note: rhetorical question
- **h-m221** (tricky, en) asks: true → false. I need the 2000 back by friday, rent's due Note: statement of need that asks for money back
- **h-m239** (tricky, hinglish) asks: true → false. urgent kuch nahi hai bas yaad dilana tha ki kal mummy ka birthday hai, cake order kar dena Note: "urgent" denied, request at the end
- **h-m244** (tricky, hi) asks: false → true. कौन इतनी गर्मी में शादी रखता है? 🥵 Note: rhetorical question
- **h-m249** (tricky, de) asks: true → false. Kannst du mir kurz Bescheid geben, ob du Samstag kommst Note: question without a question mark
- **h-m280** (tricky, hinglish) asks: true → false. yaar aaj ka din itna bekaar tha, subah alarm nahi baja, phir auto wale ne 200 maange, office mein boss ne daanta aur ab ghar aake dekha toh light nahi hai 😩 tu free hai toh shaam ko chai pe chal Note: long vent ending in an invitation
- **h-m285** (everyday, hinglish) asks: false → true. aaj gym mein PR maara 💪
- **h-m298** (everyday, es) asks: true → false. contesta porfa!!! mi abuela está en urgencias
- **h-m300** (tricky, mr) asks: true → false. उद्या येताना माझं पुस्तक घेऊन ये Note: request as an instruction
- **h-m039** (everyday, hinglish) ack: true → false. chal thik hai kal milte
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

## Reply strip

Each line: case id, difficulty and language; where nothing on the strip makes sense; what the case was.

- **h-m017** (tricky, en) “Haha” / “Hahaha” / “😟”. URGENT: which charger did u borrow, mine's missing lol no rush Note: says urgent but isn't
- **h-m021** (tricky, en) “😟” / “😞” / “Okay”. im at the ER with dad. he collapsed at home. i cant think Note: distress with no explicit ask
- **h-m024** (everyday, en) “Okay” / “Yes” / “I did”. remind me what we decided for the presentation slides?
- **h-m038** (tricky, hinglish) “Raste mein hoon” / “Bas 5 min” / “Thodi der mein”. teacher ne pucha "homework kahan hai?" maine bola kutta kha gaya 😂😂 Note: quoted question, not to him
- **h-m043** (everyday, hi) “ठीक है, हो जाएगा” / “हो गया” / “अभी नहीं हो पाएगा”. जल्दी घर आओ, दादी की तबीयत बहुत खराब है
- **h-m057** (everyday, en) “😊” / “Okay” / “No”. whats ur address again, ordering u something 👀
- **h-m079** (everyday, hinglish) “Theek hai, ho jayega” / “Done” / “Abhi nahi ho payega”. bhai jaldi aa hostel, rohit ka accident ho gaya
- **h-m084** (tricky, hinglish) “Accha” / “Baad mein baat karte hain” / “Call karoon?”. urgent mein maggi bana raha tha aur gas khatam ho gaya 😭😂 Note: "urgent" in a joke
- **h-m089** (everyday, hinglish) “Accha” / “Baad mein baat karte hain” / “Call karoon?”. result aa gaya, pass ho gaya 🎉
- **h-m090** (tricky, hinglish) “Accha” / “Baad mein baat karte hain” / “Call karoon?”. sir ne pucha "assignment kisne nahi diya" and sab chup 😂 Note: quoted question, not to him
- **h-m099** (everyday, fr) “Okay, will do” / “Done” / “Can't right now”. Réponds-moi stp, c'est grave, Léa a eu un accident
- **h-m111** (tricky, en) no strip (no confident reply). flight got cancelled, stuck at the airport, no hotels anywhere, i'm literally freaking out Note: distress with no explicit ask
- **h-m120** (tricky, en) no strip (no confident reply). https://www.instagram.com/reel/C9xYz12abQk/ 💀💀 Note: shared link, nothing asked
- **h-m130** (tricky, hinglish) “Accha” / “Baad mein baat karte hain” / “Call karoon?”. aaj prof ne urgent urgent bolke assignment diya aur khud hi leave pe chale gaye 🤡 Note: "urgent" in a story
- **h-m138** (everyday, hinglish) “Theek hai, ho jayega” / “Done” / “Abhi nahi ho payega”. jaldi neeche aa, ambulance aa gayi hai dadaji ke liye
- **h-m140** (tricky, hinglish) “Haan” / “Nahi” / “Ek min, check karna padega”. usne mujhse pucha "tu pranav ko jaanta hai?" maine bola haan obviously Note: quoted question that names him
- **h-m143** (everyday, hi) “ठीक है, हो जाएगा” / “हो गया” / “अभी नहीं हो पाएगा”. तुरंत अस्पताल आ जाओ, नानी को ICU में ले गए हैं
- **h-m145** (tricky, hi) “ठीक है, हो जाएगा” / “हो गया” / “अभी नहीं हो पाएगा”. मम्मी पूछ रही थीं कि तुम दिवाली पर आओगे या नहीं Note: reported question that is meant for him
- **h-m147** (everyday, mr) “ठीक है, हो जाएगा” / “हो गया” / “अभी नहीं हो पाएगा”. लवकर घरी ये, आजोबा पडले आहेत
- **h-m157** (tricky, en) no strip (no confident reply). 911 911 911 call me Note: slang for emergency
- **h-m160** (everyday, en) no strip (no confident reply). pls review my PR when u can https://github.com/aryan-k/notes-app/pull/14
- **h-m164** (tricky, en) “Hahaha” / “lol” / “Haha”. sorry long msg but i need to rant. my flatmate ate my food again, didnt clean the kitchen again, and then had the audacity to complain that im too loud on calls. like?? im so done. anyway dont worry about it lol Note: long rant; its "like??" is not a question to him
- **h-m165** (tricky, en) no strip (no confident reply). There's been an accident on the expressway. Rhea's car. She's in surgery now Note: emergency news, no explicit ask
- **h-m182** (tricky, hinglish) “Haan” / “Nahi” / “Ek min, check karna padega”. maine use bola "kya tu pagal hai?" aur woh hasne laga 😂 Note: quoted question, not to him
- **h-m185** (everyday, hinglish) “Accha” / “Baad mein baat karte hain” / “Call karoon?”. mujhe bhi le chal na kal
- **h-m194** (tricky, hi) “एक मिनट, देखना पड़ेगा” / “पता नहीं” / “थोड़ी देर में बताते हैं”. उसने पूछा "तुम्हारा नाम क्या है?" और मैं अपना ही नाम भूल गया 😂 Note: quoted question, not to him
- **h-m200** (tricky, fr) “Yes” / “No” / “Let me check”. Le prof m'a demandé « vous avez lu le chapitre ? » et j'ai menti mdr Note: quoted question, not to him
- **h-m201** (tricky, en) “Okay, will do” / “Done” / “Can't right now”. u up Note: two-word question, no question mark
- **h-m205** (everyday, en) “Okay” / “Oh, okay” / “Will do”. remind me the name of that cafe we went to last week
- **h-m224** (everyday, en) no strip (no confident reply). im lost on the trail near the fort, battery dying, sending location, please call the forest office
- **h-m238** (everyday, hinglish) “Theek hai, ho jayega” / “Done” / “Abhi nahi ho payega”. kya scene hai aaj raat ka
- **h-m244** (tricky, hi) “एक मिनट, देखना पड़ेगा” / “पता नहीं” / “थोड़ी देर में बताते हैं”. कौन इतनी गर्मी में शादी रखता है? 🥵 Note: rhetorical question
- **h-m250** (tricky, pt) “Yes” / “No” / “Let me check”. Minha mãe perguntou "você vai casar quando?" de novo kkkkk Note: quoted question, not to him
- **h-m252** (tricky, en) no strip (no confident reply). are you okay?? saw the news about the train accident on your line, PLEASE reply Note: worry pressing for a reply
- **h-m262** (tricky, en) no strip (no confident reply). Someone just hit my scooter and drove off. I'm bleeding a bit, people are helping me Note: emergency news, no explicit ask
- **h-m271** (everyday, en) no strip (no confident reply). my landlord just cut off the water and power. can i crash at urs tonight? need to know asap
- **h-m272** (tricky, en) no strip (no confident reply). Your turn to book the court this week 🏸 Note: statement that means he must act
- **h-m273** (everyday, en) no strip (no confident reply). On my way to the hospital for my shift, talk later
- **h-m279** (tricky, hinglish) “Okay, will do” / “Done” / “Can't right now”. sun na, ek kaam hai tujhse Note: announces a request
- **h-m285** (everyday, hinglish) “Theek hai, ho jayega” / “Done” / “Abhi nahi ho payega”. aaj gym mein PR maara 💪
- **h-m293** (tricky, hi) “हाँ” / “नहीं” / “एक मिनट, देखना पड़ेगा”. मैंने उससे पूछा "चाय पियोगे?" और वो भाग गया 😂 Note: quoted question, not to him
