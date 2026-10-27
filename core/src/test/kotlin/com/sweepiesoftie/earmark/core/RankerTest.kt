package com.sweepiesoftie.earmark.core

import com.sweepiesoftie.earmark.core.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankerTest {
    private fun reply(postedAt: java.time.Instant, text: String = "free at 5?"): Loop =
        (Classifier.classify(Fixtures.rahul(postedAt, text), Fixtures.settings) as Classification.Open).loop

    @Test
    fun `Rahul's fresh question clears the bar with the unlock boost`() {
        // A question is owed, so it fades over 6 h: recent = exp(-1/360) = 0.9972; 0.35*0.9972 + 0.125 + 0.25 + 0.075 + 0.15 = 0.9490
        val score = Ranker.score(reply(at(16, 11)), now = at(16, 12), stats = Stats())
        assertEquals(0.9490, score.total, 0.0005)
        assertEquals(0.15, score.boost, 1e-9)
        assertTrue(score.total > Ranker.CHIP_THRESHOLD)
    }

    @Test
    fun `an old message stays below the bar even at the first unlock after it`() {
        // recent = exp(-120/30) = 0.0183; 0.35*0.0183 + 0.125 + 0.25*0.5 + 0.075 + 0.15 = 0.4814
        val score = Ranker.score(reply(at(14, 12), "that was fun"), now = at(16, 12), stats = Stats())
        assertEquals(0.4814, score.total, 0.0005)
        assertTrue(score.total <= Ranker.CHIP_THRESHOLD)
    }

    @Test
    fun `something owed still clears the bar hours later at the first unlock after it, and fades fast once seen`() {
        // A question from four hours ago: recent = exp(-240/360) = 0.5134; 0.35*0.5134 + 0.125 + 0.25*0.75 + 0.075 + 0.15 = 0.7172
        val question = reply(at(12, 12), "kab tak aayega ghar")
        assertEquals(0.7172, Ranker.score(question, at(16, 12), Stats(), unseen = true).total, 0.0005)
        // Seen at an earlier unlock, it fades in half an hour like anything else.
        assertTrue(Ranker.score(question, at(16, 12), Stats(), unseen = false).total <= Ranker.CHIP_THRESHOLD)
        // Chatter from the same time doesn't come back.
        assertTrue(Ranker.score(reply(at(12, 12), "that was fun"), at(16, 12), Stats(), unseen = true).total <= Ranker.CHIP_THRESHOLD)
    }

    @Test
    fun `a missed call, a money request, a bill and a reminder are owed, and chatter isn't`() {
        val chatter = reply(at(16, 0), "that was fun")
        assertTrue(Ranker.owes(reply(at(16, 0), "call me asap")))
        assertTrue(Ranker.owes(reply(at(16, 0), "did u eat")))
        assertTrue(!Ranker.owes(chatter))
        LoopKind.entries.filter { it in setOf(LoopKind.CallBack, LoopKind.Payment, LoopKind.Reminder) }.forEach {
            assertTrue(it.name, Ranker.owes(chatter.copy(kind = it, why = emptyList())))
        }
    }

    @Test
    fun `the boost is for a message you haven't had a chance to see, however long ago it came`() {
        val loop = reply(at(16, 0), "hey")
        assertEquals(0.15, Ranker.score(loop, at(16, 20), Stats(), unseen = true).boost, 1e-9)
        assertEquals(0.0, Ranker.score(loop, at(16, 20), Stats(), unseen = false).boost, 1e-9)
    }

    @Test
    fun `history is the hold rate for the kind`() {
        // (3 + 1) / (3 + 1 + 2) = 0.6667 with one smoothing hold and ignore, so no history reads 0.5
        val stats = Stats(kinds = mapOf(LoopKind.Reply to KindStat(holds = 3, ignores = 1)))
        assertEquals(0.6667, Ranker.score(reply(at(16, 11)), at(16, 12), stats).history, 0.0005)
        assertEquals(0.5, Ranker.score(reply(at(16, 11)), at(16, 12), Stats()).history, 1e-9)
    }

    @Test
    fun `who rises with how often and how fast you reply to them`() {
        val quick = Stats(contacts = mapOf("hash-rahul" to ContactStat(replies = 10, medianReplyMinutes = 4.0)))
        val slow = Stats(contacts = mapOf("hash-rahul" to ContactStat(replies = 10, medianReplyMinutes = 240.0)))
        assertEquals(1.0, Ranker.score(reply(at(16, 11)), at(16, 12), quick).who, 1e-9)
        assertEquals(0.75, Ranker.score(reply(at(16, 11)), at(16, 12), slow).who, 1e-9)
    }

    @Test
    fun `who comes from opening their notifications when you've never replied through Earmark`() {
        // 0.3 + 0.4 * (3 + 1) / (3 + 0 + 2) = 0.62
        val opens = Stats(contacts = mapOf("hash-rahul" to ContactStat(opened = 3)))
        val score = Ranker.score(reply(at(16, 11)), at(16, 12), opens)
        assertEquals(0.62, score.who, 1e-9)
        assertEquals("opens", score.whoFrom)
    }

    @Test
    fun `a new person starts from how you treat their app, and a new group from a bit less`() {
        val apps = Stats(apps = mapOf(Fixtures.CHATS to AppStat(done = 9, left = 1, medianDoneMinutes = 0.0)))
        // 0.3 + 0.2 * (9 + 1) / (10 + 2) + 0.2 * exp(0) = 0.6667
        assertEquals(0.6667, Ranker.score(reply(at(16, 11)), at(16, 12), apps).who, 0.0005)
        assertEquals("fewer than five closed messages isn't enough to go on", 0.5,
            Ranker.score(reply(at(16, 11)), at(16, 12), Stats(apps = mapOf(Fixtures.CHATS to AppStat(done = 4)))).who, 1e-9)
        val group = (Classifier.classify(Fixtures.family(at(16, 11), "Dev" to "urgent, call me"), Fixtures.settings) as Classification.Open).loop
        assertEquals(0.4, Ranker.score(group, at(16, 12), Stats()).who, 1e-9)
    }
}
