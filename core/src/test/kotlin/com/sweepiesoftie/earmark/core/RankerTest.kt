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
        // recent = exp(-1/30) = 0.9672; 0.35*0.9672 + 0.25*0.5 + 0.25*1.0 + 0.15*0.5 + 0.15 = 0.9385
        val score = Ranker.score(reply(at(16, 11)), now = at(16, 12), stats = Stats())
        assertEquals(0.9385, score.total, 0.0005)
        assertEquals(0.15, score.boost, 1e-9)
        assertTrue(score.total > Ranker.CHIP_THRESHOLD)
    }

    @Test
    fun `an old message stays below the bar even at the first unlock after it`() {
        // recent = exp(-120/30) = 0.0183; 0.35*0.0183 + 0.125 + 0.25*0.5 + 0.075 + 0.15 = 0.4814
        val score = Ranker.score(reply(at(14, 12), "haha nice"), now = at(16, 12), stats = Stats())
        assertEquals(0.4814, score.total, 0.0005)
        assertTrue(score.total <= Ranker.CHIP_THRESHOLD)
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
}
