package com.sweepiesoftie.earmark.core

import com.sweepiesoftie.earmark.core.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** The four demo stories (earmarkdemo/01–04) as scripts of events with expected effects. */
class ScenarioTest {

    @Test
    fun `01 the Reply - chip, drift, pill at the reel change, reply sent, loop closed`() = with(Script()) {
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))

        val chip = at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip
        assertEquals("Here to reply to Rahul?", chip.title)
        assertEquals("“free at 5?” · 1 min ago", chip.subtitle)
        assertEquals(null, chip.action)

        val held = at(at(16, 12, 3), Event.ChipHeld(chip.loopId))
        assertTrue(Effect.ShowDot in held)
        assertTrue(Effect.HideOverlay(Overlay.Chip) in held)

        at(at(16, 12, 10), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        at(at(16, 12, 11), Event.PlaybackChanged(Playback.Playing))
        assertEquals("no pill while a reel plays", emptyList<Effect>(), at(at(16, 14), Event.Tick))

        val pill = at(at(16, 15, 30), Event.PlaybackChanged(Playback.TrackChanged)).one<Effect.ShowPill>().pill
        assertEquals("Reply to Rahul", pill.task)
        assertEquals("Do it now", pill.verb)

        val handBack = at(at(16, 16), Event.PillTapped(pill.loopId))
        assertEquals(Route.Notification("0|com.whatsapp|1|rahul|10123", Fixtures.CHATS), handBack.one<Effect.OpenWayBack>().route)
        assertEquals("the strip names who it replies to", "Rahul", handBack.one<Effect.SuggestReplies>().to)
        assertTrue(Effect.HideOverlay(Overlay.Pill) in handBack)

        at(at(16, 16, 1), Event.ForegroundChanged(Fixtures.CHATS, "Chats"))
        val send = at(at(16, 16, 5), Event.ReplyChosen(pill.loopId, "Yes, 5 works!")).one<Effect.SendReply>()
        assertEquals("Yes, 5 works!", send.text)

        val closed = at(at(16, 16, 6), Event.ReplySent(pill.loopId)).one<Effect.RecordLoop>().entry
        assertEquals(Outcome.Done, closed.outcome)
        assertTrue(closed.broughtBack)
        assertEquals("Reply to Rahul", closed.label)

        // Chats then updates the conversation with your message; the loop is already closed.
        val rahul = Fixtures.rahul(at(16, 11))
        val update = rahul.copy(messages = rahul.messages + Message(null, "Yes, 5 works!", at(16, 16, 6), fromUser = true))
        assertTrue(at(at(16, 16, 7), Event.NotificationPosted(update)).none { it is Effect.RecordLoop })
    }

    @Test
    fun `02 the Ride - leave-by chip, left the ride app unbooked, pill says Finish, booked closes it`() =
        with(Script(Fixtures.settings.copy(rideApp = Fixtures.RIDE))) {
            val flight = CalendarEvent(1, "Flight to Lisbon", at(11, 40), "Airport · Terminal 1", null)
            at(at(8, 14), Event.CalendarSoon(listOf(flight)))

            val chip = at(at(8, 14, 5), Event.Unlocked).one<Effect.ShowChip>().chip
            assertEquals("Here to book your ride?", chip.title)
            assertEquals("Airport · 11:40 AM · leave by 9:10 AM", chip.subtitle)
            at(at(8, 14, 8), Event.ChipHeld(chip.loopId))

            at(at(8, 14, 20), Event.ForegroundChanged(Fixtures.RIDE, "Ride"))
            at(at(8, 15), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
            at(at(8, 15, 1), Event.PlaybackChanged(Playback.Playing))
            assertTrue(at(at(8, 17), Event.Tick).isEmpty())

            val pill = at(at(8, 19), Event.PlaybackChanged(Playback.TrackChanged)).one<Effect.ShowPill>().pill
            assertEquals("Book your airport ride", pill.task)
            assertEquals("Finish", pill.verb)

            assertEquals(Route.LaunchApp(Fixtures.RIDE), at(at(8, 19, 5), Event.PillTapped(pill.loopId)).one<Effect.OpenWayBack>().route)
            at(at(8, 19, 6), Event.ForegroundChanged(Fixtures.RIDE, "Ride"))

            val booked = Signal("0|com.ubercab|9|null|10300", Fixtures.RIDE, "Ride", at(8, 19, 40), title = "Ride booked", text = "Arjun is 4 min away")
            val closed = at(at(8, 19, 40), Event.NotificationPosted(booked)).one<Effect.RecordLoop>().entry
            assertEquals(Outcome.Done, closed.outcome)
            assertTrue(closed.broughtBack)
        }

    @Test
    fun `03 Bedtime - alarm chip, pill held back until the video pauses, alarm set closes it`() =
        with(Script(stats = Stats(weeknightAlarms = List(5) { LocalTime.of(6, 30) }))) {
            at(at(23, 40), Event.AlarmChanged(null))

            val chip = at(at(23, 42), Event.Unlocked).one<Effect.ShowChip>().chip
            assertEquals("Set your 6:30 alarm?", chip.title)
            assertEquals("You usually do on weeknights", chip.subtitle)

            // The chip fades untouched; the loop is still watched (ADR 0007).
            at(at(23, 42, 20), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
            at(at(23, 42, 21), Event.PlaybackChanged(Playback.Playing))
            assertTrue("drift, but the video is playing", at(at(23, 44), Event.Tick).isEmpty())
            assertTrue(at(at(23, 47), Event.Tick).isEmpty())

            val pill = at(at(23, 47, 10), Event.PlaybackChanged(Playback.Paused)).one<Effect.ShowPill>().pill
            assertEquals("Set your 6:30 alarm", pill.task)
            assertEquals("Set it", pill.verb)

            assertEquals(Route.SetAlarm(LocalTime.of(6, 30)), at(at(23, 47, 12), Event.PillTapped(pill.loopId)).one<Effect.OpenWayBack>().route)
            val closed = at(at(23, 47, 30), Event.AlarmChanged(at(6, 30, day = 14))).one<Effect.RecordLoop>().entry
            assertEquals(Outcome.Done, closed.outcome)
            assertTrue(closed.broughtBack)
        }

    @Test
    fun `04 Later - pill swiped away, carry-over chip at the next unlock, Pay now closes it`() = with(Script()) {
        at(at(13, 18), Event.NotificationPosted(Fixtures.aisha(at(13, 18))))

        val chip = at(at(13, 18, 20), Event.Unlocked).one<Effect.ShowChip>().chip
        assertEquals("Here to pay Aisha back?", chip.title)
        assertEquals("\$12.00 · Dinner at Nori · just now", chip.subtitle)
        at(at(13, 18, 22), Event.ChipHeld(chip.loopId))

        // A text feed with nothing playing: drift itself is the pause (ADR 0005).
        at(at(13, 18, 30), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        val pill = at(at(13, 20, 1), Event.Tick).one<Effect.ShowPill>().pill
        assertEquals("Pay Aisha back \$12", pill.task)
        assertEquals("Pay now", pill.verb)

        assertTrue(Effect.HideOverlay(Overlay.Pill) in at(at(13, 20, 5), Event.PillDismissed(pill.loopId)))
        at(at(13, 25), Event.ForegroundChanged("com.reddit.frontpage", "Reddit"))
        assertFalse("quiet until the next unlock", at(at(13, 35), Event.Tick).any { it is Effect.ShowPill })
        at(at(13, 40), Event.ScreenOff)

        val carry = at(at(18, 40), Event.Unlocked).one<Effect.ShowChip>().chip
        assertEquals("Pay Aisha back \$12?", carry.title)
        assertEquals("Still on your list from 1:18 PM", carry.subtitle)
        assertEquals("Pay now", carry.action)

        assertEquals(Route.Notification(Fixtures.aisha(at(13, 18)).key, Fixtures.PAY), at(at(18, 40, 4), Event.ChipActed(carry.loopId)).one<Effect.OpenWayBack>().route)
        val closed = at(at(18, 41), Event.NotificationRemoved(Fixtures.aisha(at(13, 18)).key, RemovalReason.AppCancel)).one<Effect.RecordLoop>().entry
        assertEquals(Outcome.Done, closed.outcome)
        assertTrue(closed.broughtBack)
        assertEquals("Pay Aisha back \$12", closed.label)
    }
}
