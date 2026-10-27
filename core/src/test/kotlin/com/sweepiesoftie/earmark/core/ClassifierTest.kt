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
}
