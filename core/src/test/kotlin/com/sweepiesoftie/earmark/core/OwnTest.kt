package com.sweepiesoftie.earmark.core

import com.sweepiesoftie.earmark.core.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Own loops from the "Earmark this" tile in Quick Settings (ADR 0035). */
class OwnTest {
    private val chrome = "com.android.chrome"
    private val launcher = "com.google.android.apps.nexuslauncher"
    private val settings = Fixtures.settings.copy(homeApps = setOf(launcher), driftTimer = DriftTimer.RightAway)

    /** Unlocked with Chrome in front, then the tile tapped. */
    private fun Script.earmarkChrome(at: java.time.Instant): List<Effect> {
        at(at, Event.Unlocked)
        at(at.plusSeconds(2), Event.ForegroundChanged(chrome, "Chrome"))
        return at(at.plusSeconds(5), Event.Earmarked)
    }

    @Test
    fun `the tile holds the app in front, and the pill hands it back`() = with(Script(settings)) {
        at(at(16, 0), Event.Unlocked)
        at(at(16, 0, 2), Event.ForegroundChanged(chrome, "Chrome"))
        assertEquals(TileState(holding = null, canHold = "Chrome"), engine.tile)

        val entry = at(at(16, 0, 10), Event.Earmarked).one<Effect.RecordLoop>().entry
        assertEquals(LoopKind.Own, entry.kind)
        assertEquals("Get back to Chrome", entry.label)
        assertTrue("on today's list as you make it", entry.listed)
        assertEquals("Chrome", engine.tile.holding)
        val note = engine.held()!!.note
        assertEquals("Chrome", note.who)
        assertEquals("You earmarked it", note.words)
        assertEquals("Open", note.verb)
        assertFalse("an app, so its icon", note.person)
        assertEquals("Chrome", engine.waiting(at(16, 0, 11)).single().note.who)

        at(at(16, 0, 12), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        val pill = at(at(16, 0, 16), Event.Tick).one<Effect.ShowPill>().pill
        assertEquals("You came here to get back to it", pill.why)
        assertEquals("Open", pill.note.verb)
        assertEquals(Route.LaunchApp(chrome), at(at(16, 0, 20), Event.PillTapped(pill.loopId)).one<Effect.OpenWayBack>().route)

        at(at(16, 0, 21), Event.ForegroundChanged(chrome, "Chrome"))
        val closed = at(at(16, 1, 30), Event.ForegroundChanged(launcher, "Home")).one<Effect.RecordLoop>().entry
        assertEquals(Outcome.Done, closed.outcome)
        assertTrue(closed.broughtBack)
        assertEquals(TileState(holding = null, canHold = null), engine.tile)
    }

    @Test
    fun `the visit you earmarked it in never closes it, and going back to it later does`() = with(Script(settings)) {
        earmarkChrome(at(16, 0))
        // Still reading for a while after the tap, then off to a feed: that's what the tile is for.
        assertTrue(at(at(16, 1), Event.ForegroundChanged(launcher, "Home")).none { it is Effect.RecordLoop })
        assertEquals("Chrome", engine.tile.holding)

        at(at(16, 2), Event.ForegroundChanged(chrome, "Chrome"))
        assertTrue("passing through isn't doing it", at(at(16, 2, 5), Event.ForegroundChanged(launcher, "Home")).none { it is Effect.RecordLoop })
        at(at(16, 3), Event.ForegroundChanged(chrome, "Chrome"))
        val closed = at(at(16, 3, 40), Event.ForegroundChanged(launcher, "Home")).one<Effect.RecordLoop>().entry
        assertEquals(Outcome.Done, closed.outcome)
        assertFalse("you went back on your own", closed.broughtBack)
    }

    @Test
    fun `leaving after earmarking keeps it, and the next unlock offers it`() = with(Script(settings)) {
        earmarkChrome(at(16, 0))
        assertTrue(at(at(16, 0, 8), Event.ForegroundChanged(launcher, "Home")).none { it is Effect.RecordLoop })
        at(at(16, 0, 10), Event.ScreenOff)
        assertEquals(1, engine.openLoops)

        val chip = at(at(16, 10, 5), Event.Unlocked).one<Effect.ShowChip>().chip
        assertEquals("Chrome", chip.note.who)
        assertEquals("You earmarked it · 10 min ago", chip.note.words)
        assertEquals("Here to get back to it?", chip.why)
        assertEquals("Open", chip.note.verb)

        // You said you'd come back to it, so it waits at every unlock for a while, not only the first.
        at(at(16, 10, 8), Event.ChipFaded(chip.loopId))
        at(at(16, 11), Event.ScreenOff)
        assertTrue(at(at(17, 0), Event.Unlocked).any { it is Effect.ShowChip })
    }

    @Test
    fun `the tile can't hold a feed, the home screen or Earmark`() = with(Script(settings)) {
        at(at(16, 0), Event.Unlocked)
        assertNull("nothing reported in front yet", engine.tile.canHold)
        listOf(Fixtures.FEED to "Instagram", launcher to "Home", Fixtures.OWN to "Earmark").forEachIndexed { i, (app, label) ->
            at(at(16, 0, 10 * i + 1), Event.ForegroundChanged(app, label))
            assertNull(label, engine.tile.canHold)
            assertTrue(label, at(at(16, 0, 10 * i + 5), Event.Earmarked).none { it is Effect.RecordLoop })
        }
        assertEquals(0, engine.openLoops)
        at(at(16, 1), Event.ForegroundChanged(chrome, "Chrome"))
        at(at(16, 1, 5), Event.ScreenOff)
        assertNull("locked", engine.tile.canHold)
    }

    @Test
    fun `tapping the tile again lets it go`() = with(Script(settings)) {
        earmarkChrome(at(16, 0))
        val closed = at(at(16, 0, 7), Event.Earmarked).one<Effect.RecordLoop>().entry
        assertEquals(Outcome.Dismissed, closed.outcome)
        assertEquals(TileState(holding = null, canHold = "Chrome"), engine.tile)
        assertNull(engine.held())
    }

    @Test
    fun `not this on its chip lets it go, and never mutes earmarking`() = with(Script(settings)) {
        repeat(4) { day ->
            earmarkChrome(at(16, 0, day = 13 + day))
            at(at(16, 0, 8, day = 13 + day), Event.ScreenOff)
            val chip = at(at(16, 5, day = 13 + day), Event.Unlocked).one<Effect.ShowChip>().chip
            val effects = at(at(16, 5, 2, day = 13 + day), Event.ChipDismissed(chip.loopId))
            assertEquals(Outcome.Dismissed, effects.one<Effect.RecordLoop>().entry.outcome)
            assertTrue(effects.none { it is Effect.RecordKind })
            at(at(16, 6, day = 13 + day), Event.ScreenOff)
        }
    }

    @Test
    fun `earmarking takes over a guess you came for, which comes back after`() = with(Script(settings)) {
        at(at(16, 0), Event.ScreenOff)
        at(at(16, 5), Event.NotificationPosted(Fixtures.rahul(at(16, 5))))
        val chip = at(at(16, 6), Event.Unlocked).one<Effect.ShowChip>().chip
        at(at(16, 6, 8), Event.ChipFaded(chip.loopId))
        at(at(16, 6, 10), Event.ForegroundChanged(chrome, "Chrome"))
        at(at(16, 6, 12), Event.Earmarked)
        assertEquals("Chrome", engine.held()?.note?.who)

        at(at(16, 6, 14), Event.Earmarked)
        assertEquals("Rahul", engine.held()?.note?.who)
    }
}
