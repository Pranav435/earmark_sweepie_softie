package com.sweepiesoftie.earmark.core

import com.sweepiesoftie.earmark.core.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    @Test
    fun `no chip below the bar, and the log says why without message text`() = with(Script()) {
        at(at(14, 0), Event.NotificationPosted(Fixtures.rahul(at(14, 0), "haha nice")))
        assertTrue(at(at(16, 12), Event.Unlocked).none { it is Effect.ShowChip })
        val line = log.last()
        // 0.35*exp(-132/30) + 0.125 + 0 + 0.075 = 0.2043
        assertTrue(line, line.startsWith("unlock: no chip, best reply:hash-rahul scored 0.20"))
        assertTrue(log.none { "haha" in it })
    }

    @Test
    fun `three ignores in a row mute the kind`() = with(Script()) {
        repeat(3) { day ->
            val posted = at(16, 11, day = 13 + day)
            at(posted, Event.NotificationPosted(Fixtures.rahul(posted, key = "k$day").copy(conversationKey = "c$day")))
            val chip = at(posted.plusSeconds(30), Event.Unlocked).one<Effect.ShowChip>().chip
            at(posted.plusSeconds(32), Event.ChipDismissed(chip.loopId))
        }
        val posted = at(16, 11, day = 16)
        at(posted, Event.NotificationPosted(Fixtures.rahul(posted, key = "k3").copy(conversationKey = "c3")))
        assertTrue(at(posted.plusSeconds(30), Event.Unlocked).none { it is Effect.ShowChip })

        at(posted.plusSeconds(40), Event.KindUnmuted(LoopKind.Reply))
        assertTrue(at(posted.plusSeconds(50), Event.Unlocked).any { it is Effect.ShowChip })
    }

    @Test
    fun `a guessed reply loop expires after 12 hours`() = with(Script()) {
        at(at(9, 0), Event.NotificationPosted(Fixtures.rahul(at(9, 0))))
        at(at(9, 0, 30), Event.Unlocked)
        val expired = at(at(21, 0), Event.Tick).one<Effect.RecordLoop>().entry
        assertEquals(Outcome.Expired, expired.outcome)
    }

    @Test
    fun `swiping the notification away dismisses the loop`() = with(Script()) {
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        at(at(16, 11, 30), Event.Unlocked)
        val entry = at(at(16, 12), Event.NotificationRemoved(Fixtures.rahul(at(16, 11)).key, RemovalReason.UserDismiss)).one<Effect.RecordLoop>().entry
        assertEquals(Outcome.Dismissed, entry.outcome)
    }

    @Test
    fun `a paid notification closes the payment request`() = with(Script()) {
        at(at(13, 18), Event.NotificationPosted(Fixtures.aisha(at(13, 18))))
        at(at(13, 18, 20), Event.Unlocked)
        val paid = Signal("pay-2", Fixtures.PAY, "Pay", at(18, 41), title = "\$12.00 paid to Aisha")
        assertEquals(Outcome.Done, at(at(18, 41), Event.NotificationPosted(paid)).one<Effect.RecordLoop>().entry.outcome)
    }

    @Test
    fun `paused Earmark shows nothing`() = with(Script(Fixtures.settings.copy(paused = true))) {
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        assertTrue(at(at(16, 12), Event.Unlocked).none { it is Effect.ShowChip })
    }

    @Test
    fun `an abandoned ride booking becomes the guess and drift brings it back`() = with(Script(Fixtures.settings.copy(driftAfter = java.time.Duration.ofSeconds(10)))) {
        at(at(19, 0), Event.Unlocked)
        at(at(19, 0, 5), Event.ForegroundChanged(Fixtures.RIDE, "Uber"))
        at(at(19, 1), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        val pill = at(at(19, 1, 11), Event.Tick).one<Effect.ShowPill>().pill
        assertEquals("Finish in Uber", pill.task)
        assertEquals("Finish", pill.verb)
    }

    @Test
    fun `unlocking straight into a feed that was already open still counts as drift`() = with(Script(Fixtures.settings.copy(driftAfter = java.time.Duration.ofSeconds(10)))) {
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        // The watcher reports the app in front a moment before the engine sees the unlock.
        at(at(16, 12), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        at(at(16, 12), Event.Unlocked)
        assertEquals("Reply to Rahul", at(at(16, 12, 11), Event.Tick).one<Effect.ShowPill>().pill.task)
    }

    @Test
    fun `a long message is clamped so the chip always shows its age`() = with(Script()) {
        val long = "can you send me the slides from yesterday's offsite, the budget sheet, and the notes Priya took, before the 4:30 sync?"
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11), long)))
        val subtitle = at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip.subtitle
        assertTrue(subtitle, subtitle.endsWith("…” · 1 min ago"))
        assertTrue(subtitle, subtitle.length <= 100)
    }

    @Test
    fun `a closed leave-by loop does not come back at the next unlock`() = with(Script(Fixtures.settings.copy(rideApp = Fixtures.RIDE))) {
        val flight = CalendarEvent(1, "Flight to Lisbon", at(11, 40), "Airport · Terminal 1", null)
        at(at(8, 14), Event.CalendarSoon(listOf(flight)))
        at(at(8, 14, 5), Event.Unlocked)
        at(at(8, 20), Event.NotificationPosted(Signal("k", Fixtures.RIDE, "Ride", at(8, 20), title = "Ride booked")))
        at(at(8, 30), Event.CalendarSoon(listOf(flight)))
        assertTrue(at(at(8, 31), Event.Unlocked).none { it is Effect.ShowChip })
    }

    @Test
    fun `a loop guessed again today is recorded with today's guess time`() = with(Script()) {
        at(at(16, 11, day = 12), Event.NotificationPosted(Fixtures.rahul(at(16, 11, day = 12))))
        at(at(16, 11, 30, day = 12), Event.Unlocked)
        at(at(17, 0, day = 12), Event.NotificationPosted(Fixtures.rahul(at(9, 0, day = 13), "still free at 5?")))
        val entry = at(at(9, 0, 20), Event.Unlocked).one<Effect.RecordLoop>().entry
        assertEquals(at(9, 0, 20), entry.guessedAt)
    }

    @Test
    fun `tapping the notification closes the loop but is not counted as a reply`() = with(Script()) {
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        val effects = at(at(16, 12), Event.NotificationRemoved(Fixtures.rahul(at(16, 11)).key, RemovalReason.Click))
        assertTrue(effects.none { it is Effect.RecordContact })
    }

    @Test
    fun `tapping the chip counts as a hold`() = with(Script()) {
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        val chip = at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip
        assertEquals(KindStat(holds = 1), at(at(16, 12, 2), Event.ChipActed(chip.loopId)).one<Effect.RecordKind>().stat)
    }

    @Test
    fun `closing the guessed loop hides its chip and forgets its message text`() = with(Script()) {
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        val chip = at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip
        val closed = at(at(16, 12, 1), Event.ReplySent(chip.loopId))
        assertTrue(Effect.HideOverlay(Overlay.Chip) in closed)
        assertTrue(Effect.Forget(Fixtures.rahul(at(16, 11)).key) in closed)
    }

    @Test
    fun `a skipped notification's text is forgotten at once`() = with(Script()) {
        val code = Fixtures.rahul(at(16, 11)).copy(messages = listOf(Message("HDFC", "482913 is your OTP", at(16, 11))))
        assertTrue(Effect.Forget(code.key) in at(at(16, 11), Event.NotificationPosted(code)))
    }

    @Test
    fun `a silently adopted flow only becomes a thing when its pill shows`() = with(Script(Fixtures.settings.copy(driftAfter = java.time.Duration.ofSeconds(10)))) {
        at(at(19, 0), Event.Unlocked)
        at(at(19, 0, 5), Event.ForegroundChanged(Fixtures.RIDE, "Uber"))
        assertTrue(at(at(19, 1), Event.ForegroundChanged(Fixtures.FEED, "Instagram")).none { it is Effect.RecordLoop })
        assertEquals(at(19, 1, 11), at(at(19, 1, 11), Event.Tick).one<Effect.RecordLoop>().entry.guessedAt)
    }
}
