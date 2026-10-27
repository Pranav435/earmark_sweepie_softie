package com.sweepiesoftie.earmark.core

import com.sweepiesoftie.earmark.core.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class DriftMachineTest {
    private val rules = DriftRules(feedApps = setOf(Fixtures.FEED, "com.reddit.frontpage"), driftAfter = Duration.ofSeconds(90))
    private val guessed = DriftMachine.guess(Drift(), LoopId("reply:hash-rahul"), Fixtures.CHATS)

    private fun drifting(playback: Playback = Playback.Playing): Drift {
        var d = DriftMachine.step(guessed, DriftInput.Media(playback), at(16, 12), rules).drift
        d = DriftMachine.step(d, DriftInput.Foreground(Fixtures.FEED), at(16, 12), rules).drift
        return DriftMachine.step(d, DriftInput.Tick, at(16, 13, 30), rules).drift
    }

    @Test
    fun `a feed in front for the drift timer starts drift`() {
        assertEquals(Phase.Drifting, drifting().phase)
        val early = DriftMachine.step(DriftMachine.step(guessed, DriftInput.Foreground(Fixtures.FEED), at(16, 12), rules).drift, DriftInput.Tick, at(16, 13, 29), rules)
        assertEquals(Phase.InFeed, early.drift.phase)
    }

    @Test
    fun `the loop's own app is not drift`() {
        val inChats = DriftMachine.step(guessed, DriftInput.Foreground(Fixtures.CHATS), at(16, 12), rules)
        assertEquals(Phase.Guessed, inChats.drift.phase)
        val back = DriftMachine.step(drifting(), DriftInput.Foreground(Fixtures.CHATS), at(16, 14), rules)
        assertEquals(Phase.Guessed, back.drift.phase)
        assertFalse(back.showPill)
    }

    @Test
    fun `a call holds the pill even at a pause`() {
        val onCall = DriftMachine.step(drifting(), DriftInput.Block(Blocker.Call, true), at(16, 14), rules).drift
        val paused = DriftMachine.step(onCall, DriftInput.Media(Playback.Paused), at(16, 14, 5), rules)
        assertFalse(paused.showPill)
        assertEquals("media Paused, held: Call", paused.reason)
        val hungUp = DriftMachine.step(paused.drift, DriftInput.Block(Blocker.Call, false), at(16, 15), rules)
        assertTrue("the end of the call is the pause", hungUp.showPill)
    }

    @Test
    fun `an app switch is a pause`() {
        assertTrue(DriftMachine.step(drifting(), DriftInput.Foreground("com.reddit.frontpage"), at(16, 14), rules).showPill)
    }

    @Test
    fun `the engine can hold the pill`() {
        val once = DriftMachine.step(drifting(), DriftInput.Media(Playback.Paused), at(16, 14), rules.copy(pillHeld = "its pill already showed for this message"))
        assertFalse(once.showPill)
        assertEquals("media Paused, held: its pill already showed for this message", once.reason)
    }
}
