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
    fun `a question with a time word is fully due`() {
        assertEquals(1.0, open(Fixtures.rahul(now, "free at 5?")).due, 1e-9)
        assertEquals(0.5, open(Fixtures.rahul(now, "free tonight")).due, 1e-9)
        assertEquals(0.0, open(Fixtures.rahul(now, "haha nice")).due, 1e-9)
    }

    @Test
    fun `skips the cases that are not owed`() {
        val rahul = Fixtures.rahul(now)
        assertEquals("group without a mention", skipReason(rahul.copy(isGroupConversation = true)))
        assertEquals("one-time code", skipReason(rahul.copy(messages = listOf(Message("HDFC", "482913 is your OTP for login", now)))))
        assertEquals("bot", skipReason(rahul.copy(messages = listOf(Message("Swiggy Bot", "Rate your order?", now, isBot = true)))))
        assertEquals("group summary", skipReason(rahul.copy(isGroupSummary = true)))
        assertEquals("ongoing", skipReason(rahul.copy(isOngoing = true)))
        assertEquals("own notification", skipReason(rahul.copy(packageName = Fixtures.OWN, isOwn = true)))
    }

    @Test
    fun `a group message that mentions you is a loop`() {
        assertEquals(LoopKind.Reply, open(Fixtures.rahul(now).copy(isGroupConversation = true, mentionsUser = true)).kind)
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
        assertEquals(0.5, open(Fixtures.rahul(now, "هل أنت متاح؟")).due, 1e-9)
        assertEquals(0.5, open(Fixtures.rahul(now, "今日来られる？")).due, 1e-9)
    }
}
