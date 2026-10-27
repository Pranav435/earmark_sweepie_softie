package com.sweepiesoftie.earmark.core

import com.sweepiesoftie.earmark.core.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class SmartDriftTest {
    private fun reply(due: Double): Loop = Loop(
        id = LoopId("reply:hash-rahul"),
        kind = LoopKind.Reply,
        task = "Reply to Rahul",
        who = "Rahul",
        detail = null,
        packageName = Fixtures.CHATS,
        conversation = "hash-rahul",
        notificationKey = null,
        route = Route.LaunchApp(Fixtures.CHATS),
        openedAt = at(16, 0),
        updatedAt = at(16, 0),
        expiresAt = at(23, 0),
        due = due,
    )

    private fun seconds(loop: Loop, held: Boolean = false, app: AppStat? = null): Long = SmartDrift.timer(loop, held, app).after.seconds

    @Test
    fun `a plain message waits 45 s while the feed app is still being learned`() {
        assertEquals(45, seconds(reply(0.5)))
        assertEquals("usual visit 45 s, still learning", SmartDrift.timer(reply(0.5), held = false, app = AppStat(visits = 4, medianVisitSeconds = 200.0)).why)
    }

    @Test
    fun `something urgent comes back in half the time, and something due soon in three quarters`() {
        assertEquals(23, seconds(reply(1.0)))
        assertEquals(34, seconds(reply(0.75)))
        assertEquals("usual visit 45 s, still learning · urgent ×0.5", SmartDrift.timer(reply(1.0), held = false, app = null).why)
    }

    @Test
    fun `saying yes to the chip brings it back a quarter sooner`() {
        assertEquals(34, seconds(reply(0.5), held = true))
        assertEquals(17, seconds(reply(1.0), held = true))
    }

    @Test
    fun `after five visits it goes by your usual visit to that app`() {
        val instagram = AppStat(visits = 5, medianVisitSeconds = 80.0)
        assertEquals(80, seconds(reply(0.5), app = instagram))
        assertEquals("usual visit 80 s · urgent ×0.5", SmartDrift.timer(reply(1.0), held = false, app = instagram).why)
    }

    @Test
    fun `it stays between 15 seconds and 2 minutes`() {
        assertEquals(15, seconds(reply(1.0), held = true, app = AppStat(visits = 9, medianVisitSeconds = 10.0)))
        assertEquals(120, seconds(reply(0.5), app = AppStat(visits = 9, medianVisitSeconds = 600.0)))
    }

    @Test
    fun `the usual visit is a running median that moves at most 10 seconds a visit`() {
        val first = SmartDrift.learn(null, Duration.ofSeconds(40))
        assertEquals(AppStat(visits = 1, medianVisitSeconds = 40.0), first)
        assertEquals(50.0, SmartDrift.learn(first, Duration.ofMinutes(30)).medianVisitSeconds, 0.0)
        assertEquals(37.0, SmartDrift.learn(first, Duration.ofSeconds(37)).medianVisitSeconds, 0.0)
        assertEquals(30.0, SmartDrift.learn(first, Duration.ofSeconds(5)).medianVisitSeconds, 0.0)
    }
}
