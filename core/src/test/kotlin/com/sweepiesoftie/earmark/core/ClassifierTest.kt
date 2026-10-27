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
        assertEquals(0.75, open(Fixtures.rahul(now, "free tonight")).due, 1e-9)
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
        assertEquals(Classification.Paid("com.venmo"), Classifier.classify(note("com.venmo", "Venmo", "You paid Aisha Khan \$12.00"), Fixtures.settings))
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
}
