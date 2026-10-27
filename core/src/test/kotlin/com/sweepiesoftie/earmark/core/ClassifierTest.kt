package com.sweepiesoftie.earmark.core

import com.sweepiesoftie.earmark.core.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierTest {
    private val now = at(16, 12)

    private fun open(signal: Signal, settings: Settings = Fixtures.settings): Loop {
        val result = Classifier.classify(signal, settings)
        assertTrue("expected a loop, got $result", result is Classification.Open)
        return (result as Classification.Open).loop
    }

    private fun skipReason(signal: Signal, settings: Settings = Fixtures.settings): String {
        val result = Classifier.classify(signal, settings)
        assertTrue("expected a skip, got $result", result is Classification.Skip)
        return (result as Classification.Skip).reason
    }

    @Test
    fun `a person's unanswered message opens a Reply loop`() {
        val loop = open(Fixtures.rahul(now))
        assertEquals(LoopKind.Reply, loop.kind)
        assertEquals("Reply to Rahul", loop.task)
        assertEquals("“free at 5?”", loop.detail)
        assertEquals(Route.Notification("0|com.whatsapp|1|rahul|10123", Fixtures.CHATS), loop.route)
        assertTrue(loop.canReply)
        assertEquals(now.plusSeconds(12 * 3600), loop.expiresAt)
    }

    @Test
    fun `a message to you is half due, and a question and a time word add a quarter each`() {
        assertEquals(1.0, open(Fixtures.rahul(now, "free at 5?")).due, 1e-9)
        assertEquals(0.75, open(Fixtures.rahul(now, "back home tonight")).due, 1e-9)
        assertEquals(0.75, open(Fixtures.rahul(now, "free?")).due, 1e-9)
        assertEquals(0.5, open(Fixtures.rahul(now, "that was fun")).due, 1e-9)
    }

    @Test
    fun `an urgent message is fully due, and the log gets why without its words`() {
        val loop = open(Fixtures.rahul(now, "call me asap"))
        assertEquals(1.0, loop.due, 1e-9)
        assertTrue(loop.urgent)
        assertEquals("asap is also a time word", listOf("urgent", "asks", "time"), loop.why)
    }

    @Test
    fun `a message that only acknowledges owes nothing`() {
        assertEquals("nothing to answer", skipReason(Fixtures.rahul(now, "haha ok 👍")))
        // An earlier question they're still waiting on keeps it a loop.
        val rahul = Fixtures.rahul(now.minusSeconds(60), "free at 5?")
        val thenOk = rahul.copy(messages = rahul.messages + Message("Rahul", "ok", now))
        assertEquals(1.0, open(thenOk).due, 1e-9)
    }

    @Test
    fun `skips the cases that are not owed`() {
        val rahul = Fixtures.rahul(now)
        assertEquals("one-time code", skipReason(rahul.copy(messages = listOf(Message("HDFC", "482913 is your OTP for login", now)))))
        assertEquals("bot", skipReason(rahul.copy(messages = listOf(Message("Swiggy Bot", "Rate your order?", now, isBot = true)))))
        assertEquals("group summary", skipReason(rahul.copy(isGroupSummary = true)))
        assertEquals("ongoing", skipReason(rahul.copy(isOngoing = true)))
        assertEquals("own notification", skipReason(rahul.copy(packageName = Fixtures.OWN, isOwn = true)))
    }

    private val withName = Fixtures.settings.copy(myNames = listOf("Pranav Savla"))

    @Test
    fun `group chatter isn't a loop, but is quiet so a tap on it can be learned`() {
        val chatter = Fixtures.family(now, "Mom" to "who's coming saturday?")
        assertEquals(Classification.Quiet("hash-family"), Classifier.classify(chatter, withName))
    }

    @Test
    fun `a group message that mentions you is a loop that quotes it and names the group`() {
        val family = Fixtures.family(now, "Mom" to "Pranav can you get the cake?", "Riya" to "haha", "Dev" to "lol same")
        val loop = open(family, withName)
        assertEquals("Reply to Mom in Family", loop.task)
        assertEquals("“Pranav can you get the cake?”", loop.detail)
        assertEquals("the chatter after it isn't a new message to you", family.messages.first().at, loop.updatedAt)
        assertEquals(0.75, loop.due, 1e-9)
        assertEquals(listOf("asks", "mentions you"), loop.why)
        assertTrue(loop.inGroup)
    }

    @Test
    fun `WhatsApp's message count is left off the group's name, and its ~ before people you haven't saved`() {
        val family = Fixtures.family(now, "~ Riya" to "Pranav are you coming?").copy(conversationTitle = "Family (2 messages)")
        assertEquals("Reply to Riya in Family", open(family, withName).task)
    }

    @Test
    fun `a mention as WhatsApp writes it, with the name between direction marks, counts`() {
        val family = Fixtures.family(now, "Mom" to "@\u2068Pranav Savla\u2069 can you get the cake?")
        assertEquals("Reply to Mom in Family", open(family, withName).task)
    }

    @Test
    fun `WhatsApp's "Mentioned you" message counts as a mention, with the real sender taken back out of its text`() {
        // As seen on the A34: the sender is "📣 Mentioned you", and the text starts with who wrote it.
        val family = Fixtures.family(now, "📣 Mentioned you" to "Riya: @\u2068Pranav Savla\u2069 \tsee this")
        val loop = open(family)
        assertEquals("Reply to Riya in Family", loop.task)
        assertEquals("“@Pranav Savla see this”", loop.detail)
        assertTrue(loop.why.toString(), "mentions you" in loop.why)
    }

    @Test
    fun `an @You mention counts without your contact card`() {
        val family = Fixtures.family(now, "Mom" to "@You see this").copy(selfName = "You")
        assertEquals("Reply to Mom in Family", open(family).task)
    }

    @Test
    fun `an urgent group message is a loop even without a mention`() {
        val loop = open(Fixtures.family(now, "Dev" to "Dad's in the hospital, call me"), Fixtures.settings)
        assertTrue(loop.urgent)
        assertEquals(1.0, loop.due, 1e-9)
    }

    @Test
    fun `in a group you usually open, a question is for you, but chatter still isn't`() {
        val opens = Stats(contacts = mapOf("hash-family" to ContactStat(opened = 4, swiped = 1)))
        assertEquals("Reply to Mom in Family", (Classifier.classify(Fixtures.family(now, "Mom" to "who's coming saturday?"), Fixtures.settings, opens) as Classification.Open).loop.task)
        assertEquals(Classification.Quiet("hash-family"), Classifier.classify(Fixtures.family(now, "Mom" to "saw this today"), Fixtures.settings, opens))
        val swipes = Stats(contacts = mapOf("hash-family" to ContactStat(opened = 3, swiped = 4)))
        assertEquals(Classification.Quiet("hash-family"), Classifier.classify(Fixtures.family(now, "Mom" to "who's coming saturday?"), Fixtures.settings, swipes))
    }

    @Test
    fun `own notifications are loops in demo mode`() {
        val demo = Fixtures.rahul(now).copy(packageName = Fixtures.OWN, isOwn = true)
        assertEquals(LoopKind.Reply, open(demo, Fixtures.settings.copy(demoMode = true)).kind)
    }

    @Test
    fun `your own last message is evidence that you replied`() {
        val rahul = Fixtures.rahul(now)
        val replied = rahul.copy(messages = rahul.messages + Message(null, "Yes, 5 works!", now.plusSeconds(60), fromUser = true))
        assertEquals(Classification.Replied("hash-rahul"), Classifier.classify(replied, Fixtures.settings))
    }

    private fun note(pkg: String, title: String?, text: String?) =
        Signal("0|$pkg|3|null|1", pkg, pkg, now, title = title, text = text)

    @Test
    fun `payment requests as the big payment apps word them`() {
        val venmo = open(note("com.venmo", "Venmo", "Aisha Khan requests \$12.00 for dinner"))
        assertEquals("Pay Aisha Khan back \$12", venmo.task)
        val paypal = open(note("com.paypal.android.p2pmobile", "PayPal", "Aisha sent you a request for \$12.00 USD"))
        assertEquals("Pay Aisha back \$12", paypal.task)
        val gpay = open(note("com.google.android.apps.nbu.paisa.user", "Aisha Khan requested ₹450.00", "Tap to pay"))
        assertEquals("Pay Aisha Khan back ₹450", gpay.task)
        assertEquals("₹450.00", gpay.detail)
        val phonepe = open(note("com.phonepe.app", "Payment request", "Payment request of ₹500 from Aisha"))
        assertEquals("Pay Aisha back ₹500", phonepe.task)
    }

    @Test
    fun `a booked, confirmed or on-the-way notice is evidence for flows and leave-by`() {
        assertEquals(Classification.Booked("com.ubercab"), Classifier.classify(note("com.ubercab", "Ramesh is arriving in 4 min", "White Swift · KA01AB1234"), Fixtures.settings))
        assertEquals(Classification.Booked("com.olacabs.customer"), Classifier.classify(note("com.olacabs.customer", "Your ride is confirmed", "Driver Suresh, 3 mins away"), Fixtures.settings))
        assertEquals(Classification.Booked("in.swiggy.android"), Classifier.classify(note("in.swiggy.android", "Order placed successfully", "Your food is being prepared"), Fixtures.settings))
    }

    @Test
    fun `a paid receipt is evidence, not a new request`() {
        val paid = Classifier.classify(note("com.venmo", "Venmo", "You paid Aisha Khan \$12.00"), Fixtures.settings) as Classification.Paid
        assertEquals("com.venmo", paid.packageName)
        assertEquals("\$12.00", paid.amount?.full)
    }

    @Test
    fun `questions and clock times in any script count as due`() {
        assertEquals(1.0, open(Fixtures.rahul(now, "can you send the slides before the 4:30 sync?")).due, 1e-9)
        assertEquals(0.75, open(Fixtures.rahul(now, "هل أنت متاح؟")).due, 1e-9)
        assertEquals(0.75, open(Fixtures.rahul(now, "今日来られる？")).due, 1e-9)
    }

    @Test
    fun `a missed call opens a call-back loop, not a reply`() {
        val loop = open(Fixtures.missedCall(now))
        assertEquals(LoopKind.CallBack, loop.kind)
        assertEquals("Call Mom back", loop.task)
        assertEquals("Missed call", loop.detail)
        assertEquals(0.75, loop.due, 1e-9)
        assertEquals(Route.Notification("0|com.google.android.dialer|1|MissedCall_content://call_log/calls/1|10142", Fixtures.DIALER), loop.route)
        assertEquals(now.plusSeconds(12 * 3600), loop.expiresAt)
    }

    @Test
    fun `missed calls as other apps word them, without the category`() {
        assertEquals("Call Rahul back", open(note("com.whatsapp", "Rahul", "Missed voice call")).task)
        assertEquals("Call Rahul back", open(note("com.samsung.android.dialer", "Missed call", "Rahul")).task)
        assertEquals("Call Rahul back", open(note("org.telegram.messenger", "Missed call from Rahul", null)).task)
        assertEquals("Call +91 98765 43210 back", open(note("com.samsung.android.dialer", "+91 98765 43210 (2)", "2 missed calls")).task)
    }

    @Test
    fun `someone who called twice is urgent`() {
        val loop = open(note("com.samsung.android.dialer", "Mom", "2 missed calls"))
        assertEquals("2 missed calls", loop.detail)
        assertEquals(1.0, loop.due, 1e-9)
        assertTrue(loop.urgent)
    }

    @Test
    fun `a chat message about a missed call is still a reply`() {
        assertEquals(LoopKind.Reply, open(Fixtures.rahul(now, "sorry I missed your call, free now?")).kind)
    }

    @Test
    fun `an email from a person that asks you something opens an email loop`() {
        val loop = open(Fixtures.email(now))
        assertEquals(LoopKind.Email, loop.kind)
        assertEquals("Reply to Prof. Mehta's email", loop.task)
        assertEquals("Prof. Mehta", loop.who)
        assertEquals("“Lab report”", loop.detail)
        assertEquals(listOf("asks"), loop.why)
        assertEquals(now.plusSeconds(24 * 3600), loop.expiresAt)
    }

    @Test
    fun `an email that asks nothing, or comes from a no-reply sender, is skipped`() {
        assertEquals("email asks nothing", skipReason(Fixtures.email(now, body = "Notes from today's lecture are attached.")))
        assertEquals("automated email", skipReason(Fixtures.email(now, from = "GitHub Notifications")))
        assertEquals("automated email", skipReason(Fixtures.email(now, from = "noreply@moodle.edu")))
        assertEquals("one-time code", skipReason(Fixtures.email(now, from = "Microsoft account", body = "Your security code is 482913")))
    }

    @Test
    fun `an urgent email is a loop even without a question`() {
        val loop = open(Fixtures.email(now, subject = "Urgent: grades", body = "Come to my office immediately."))
        assertTrue(loop.urgent)
        assertEquals(1.0, loop.due, 1e-9)
    }

    @Test
    fun `a reminder you set opens a reminder loop that is due now`() {
        val loop = open(Fixtures.reminder(now))
        assertEquals(LoopKind.Reminder, loop.kind)
        assertEquals("Submit lab report", loop.task)
        assertEquals("Reminder in Keep", loop.detail)
        assertEquals(1.0, loop.due, 1e-9)
        assertEquals(null, loop.who)
    }

    @Test
    fun `a reminder titled only "Reminder" takes its words from the text`() {
        assertEquals("Take your meds", open(Fixtures.reminder(now, title = "Reminder", text = "Take your meds")).task)
    }

    @Test
    fun `calendar events and reminder apps without a category count too`() {
        val event = note("com.google.android.calendar", "Physics quiz", "4:00 – 5:00 PM").copy(category = "event")
        assertEquals("Physics quiz", open(event).task)
        assertEquals("Water the plants", open(Fixtures.reminder(now, title = "Water the plants", category = null)).task)
        assertEquals("a reminder app's other notifications aren't reminders", "not a loop", skipReason(Fixtures.reminder(now, title = "Rahul shared a note", category = "social")))
    }

    @Test
    fun `a bill a business texts you opens a payment loop`() {
        val loop = open(Fixtures.airtel(now))
        assertEquals(LoopKind.Payment, loop.kind)
        assertTrue(loop.bill)
        assertEquals("Pay your Airtel postpaid bill ₹499", loop.task)
        assertEquals("₹499.00 · due 30-Oct", loop.detail)
        assertEquals(0.75, loop.due, 1e-9)
    }

    @Test
    fun `a bill due today or overdue is fully due`() {
        assertEquals(1.0, open(Fixtures.airtel(now, "Your electricity bill of Rs 1,240 is due today. Pay to avoid disconnection.")).due, 1e-9)
        assertEquals(1.0, open(Fixtures.airtel(now, "Your bill of Rs 499 is overdue. Ignore if already paid.")).due, 1e-9)
    }

    @Test
    fun `without a "your X bill", the biller is the sender ID or the app`() {
        assertEquals("Pay your AIRTEL bill ₹499", open(Fixtures.airtel(now, "Dear customer, bill of Rs 499 for 98xxxx is due on 30-Oct.")).task)
        val cred = note("com.dreamplug.androidapp", "Bill due", "Rs 2,150 is due on 05 Nov for card ending 4321").copy(appLabel = "CRED")
        assertEquals("Pay your CRED bill ₹2,150", open(cred).task)
    }

    @Test
    fun `texts from a business are never replies`() {
        assertEquals("text from a business", skipReason(Fixtures.airtel(now, "Rs 499 debited from your account for your Airtel bill.")))
        assertEquals("text from a business", skipReason(Fixtures.airtel(now, "Get 2GB extra data! Recharge with Rs 299 now?")))
        assertEquals("text from a business", skipReason(Fixtures.airtel(now, "Payment of Rs 499 received towards your bill due on 30-Oct. Thank you!")))
        val shortCode = Fixtures.airtel(now, "Reply YES to confirm?").let { it.copy(title = "56767", messages = listOf(Message("56767", it.text!!, now))) }
        assertEquals("text from a business", skipReason(shortCode))
    }

    @Test
    fun `bills in Hindi, Marathi and Hinglish, fees, maintenance and card dues`() {
        assertEquals("Pay your AIRTEL bill ₹1,099", open(Fixtures.airtel(now, "आपके पोस्टपेड नंबर का ₹1,099 का बिल 08-11-2026 तक देय है।")).task)
        assertTrue(open(Fixtures.airtel(now, "महावितरण: आपले वीज बिल रु. 860.00 असून देय दिनांक 09-11-2026 आहे.")).bill)
        assertTrue(open(Fixtures.airtel(now, "Aapka Rs 349 ka bill 06-11 tak due hai. Abhi pay karein.")).bill)
        assertTrue(open(Fixtures.airtel(now, "Hostel fee of Rs. 38,000 is due by 20-Nov.")).bill)
        assertTrue(open(Fixtures.airtel(now, "Maintenance of ₹3,200 for Flat C-104 is due by 12-Nov.")).bill)
        assertTrue(open(note("com.csam.icici.bank.imobile", "Payment due", "Total amount due ₹6,410.00 on your Credit Card XX1122 is due on 02-Nov.")).bill)
    }

    @Test
    fun `a failed payment is still owed, and a statement with nothing due isn't`() {
        assertTrue(open(note("com.spotify.music", "Payment failed", "We couldn't process your ₹59 payment for Premium.")).bill)
        assertTrue(open(note("com.hdfcbank", "EMI not debited", "EMI of ₹4,800 could not be debited due to insufficient balance.")).bill)
        assertTrue(open(note("com.ubercab", "Payment pending", "Your last trip couldn't be charged. Pay ₹180 to ride again.")).bill)
        assertEquals("text from a business", skipReason(Fixtures.airtel(now, "Card XX4521 statement: Total Amount Due Rs 0.00. No payment required.")))
    }

    @Test
    fun `a bank's debit text, or an app's receipt, says you paid`() {
        val bank = { text: String -> Fixtures.airtel(now, text).copy(title = "AX-HDFCBK", messages = listOf(Message("AX-HDFCBK", text, now))) }
        assertEquals(Classification.Paid(Fixtures.SMS, Money("₹", "300.00".toBigDecimal())), Classifier.classify(bank("Sent Rs.300.00 From HDFC Bank A/C *4521 To RIYA SHAH On 27/10/26 Ref 6301"), Fixtures.settings))
        assertEquals(Classification.Paid(Fixtures.SMS), Classifier.classify(bank("A/C X1234 debited by 250.0 on date 27Oct26 trf to PAPA Refno 6301"), Fixtures.settings))
        assertEquals("net.one97.paytm", (Classifier.classify(note("net.one97.paytm", "Paid ₹120 to Riya Shah", "Paid via UPI"), Fixtures.settings) as Classification.Paid).packageName)
        assertEquals("text from a business", skipReason(bank("Your A/c XX1234 is credited with Rs.5,000.00 by NEFT from ACME")))
    }

    @Test
    fun `ride and food confirmations count as booked, even with a ride code, and delivery tracking doesn't`() {
        assertEquals(Classification.Booked("com.olacabs.customer"), Classifier.classify(note("com.olacabs.customer", "Booking confirmed", "Ravi is on the way. Share OTP 5521 to start your ride."), Fixtures.settings))
        assertEquals(Classification.Booked("com.application.zomato"), Classifier.classify(note("com.application.zomato", "Order confirm ho gaya!", "Delivery in 30 min"), Fixtures.settings))
        assertEquals(Classification.Booked("com.ubercab"), Classifier.classify(note("com.ubercab", "Reservation confirmed", "Pickup Thu 6:00 AM"), Fixtures.settings))
        assertEquals("not a loop", skipReason(note("in.amazon.mShop.android.shopping", "Out for delivery", "Your package will arrive today by 9 PM.")))
        assertEquals("one-time code", skipReason(note("com.olacabs.customer", "Ola", "Your login OTP is 448812")))
    }

    @Test
    fun `payment requests worded as a request from someone, or a reminder to pay`() {
        assertEquals("Pay Riya Shah back ₹300", open(note("com.google.android.apps.nbu.paisa.user", "Payment request from Riya Shah", "₹300 · Lunch")).task)
        assertEquals("Pay Dev back ₹200", open(note("com.google.android.apps.nbu.paisa.user", "Payment reminder", "Dev sent you a reminder to pay ₹200")).task)
        assertEquals("Pay Kabir Rao back ₹900", open(note("com.phonepe.app", "Request expiring", "Pay ₹900 requested by Kabir Rao before it expires")).task)
    }

    @Test
    fun `spam calls are skipped, and a carrier's missed-call text names the caller`() {
        assertEquals("spam call", skipReason(note("com.truecaller", "Likely spam", "Missed call from +91 140 222 3333")))
        val alert = Fixtures.airtel(now, "Missed Call Alert: 9820011223 tried to call you 3 times. Last call at 9:10 AM.")
        assertEquals("Call 9820011223 back", open(alert).task)
    }

    @Test
    fun `phishing from an unknown number and business bots on WhatsApp aren't people waiting on you`() {
        val stranger = { text: String -> Fixtures.priya(now, text).copy(title = "+91 70000 12345", messages = listOf(Message("+91 70000 12345", text, now))) }
        assertEquals("looks like a scam", skipReason(stranger("Dear Customer, your bank account will be BLOCKED today. Update KYC: http://x.co/kyc")))
        assertEquals(LoopKind.Reply, open(stranger("hi this is Ravi from the hostel, can you call me?")).kind)
        // As Google Messages names an unsaved sender.
        val bracketed = Fixtures.priya(now, "Your account will be BLOCKED today. Update KYC: http://x.co").let { it.copy(title = "(987) 650-0044", messages = listOf(Message("(987) 650-0044", it.text!!, now))) }
        assertEquals("looks like a scam", skipReason(bracketed))
        val bank = Fixtures.priya(now, "Your balance is ₹4,210.50. Reply 'Menu' to see more options.").copy(title = "Axis Bank")
        assertEquals("text from a business", skipReason(bank.copy(messages = listOf(Message("Axis Bank", bank.text!!, now)))))
    }

    @Test
    fun `a reminder you set, delivered by a bot`() {
        val slack = Fixtures.priya(now, "Reminder: send the invoice.").let { it.copy(title = "Slackbot", messages = listOf(Message("Slackbot", it.text!!, now, isBot = true))) }
        assertEquals("send the invoice", open(slack).task)
    }

    @Test
    fun `an email's quoted reply and forwarded message aren't its sender asking`() {
        val thanks = Fixtures.email(now, body = "Got it, thanks.\n\nOn Mon, 26 Oct 2026, Pranav Savla wrote:\n> Could you please check my draft?")
        assertEquals("email asks nothing", skipReason(thanks))
        val forward = Fixtures.email(now, body = "Bhai ye form bhar dena aaj raat tak\n---------- Forwarded message ---------\nFrom: Events Team\nRegister now!")
        assertEquals(LoopKind.Email, open(forward).kind)
    }

    @Test
    fun `calendar invitations, doc comments, newsletters and sales pitches aren't people asking`() {
        assertEquals("automated email", skipReason(Fixtures.email(now, from = "Google Calendar", subject = "Invitation: Sync @ Thu", body = "Riya invited you to this event. Going?")))
        assertEquals("automated email", skipReason(Fixtures.email(now, from = "Riya Shah (Google Docs)", body = "Riya mentioned you in a comment: can you fix the table?")))
        assertEquals("automated email", skipReason(Fixtures.email(now, from = "Upgrad", body = "Join our free webinar on ML this Sunday. Will you be there?")))
        assertEquals("automated email", skipReason(Fixtures.email(now, from = "Karan Bose", body = "Hi Pranav, would you be open to a 15-minute call to see how Acme could help your team?")))
    }

    @Test
    fun `a bill that comes by email is a bill, and a booking confirmation is booked`() {
        val bill = open(Fixtures.email(now, from = "Tata Power", subject = "Your Tata Power bill for October", body = "Amount due: ₹2,310.40\nDue date: 05 Nov 2026"))
        assertTrue(bill.bill)
        assertEquals(Classification.Booked(Fixtures.MAIL), Classifier.classify(Fixtures.email(now, from = "IndiGo", subject = "Booking confirmed: BOM to BLR", body = "Your booking is confirmed."), Fixtures.settings))
    }

    @Test
    fun `your name in an Indian script counts as a mention, and someone else with your first name doesn't`() {
        assertEquals("Reply to Papa in Family", open(Fixtures.family(now, "Papa" to "प्रणव, कल सुबह जल्दी उठना"), withName).task)
        assertEquals("Reply to Ba in Family", open(Fixtures.family(now, "Ba" to "પ્રણવ, જમવા આવ"), withName).task)
        assertEquals(Classification.Quiet("hash-family"), Classifier.classify(Fixtures.family(now, "Riya" to "Pranav K and Isha, submit the sheet"), withName))
        assertEquals("Reply to Riya in Family", open(Fixtures.family(now, "Riya" to "Pranav I need the sheet"), withName).task)
    }
}
