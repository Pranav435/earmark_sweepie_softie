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
        // 0.35*exp(-132/30) + 0.125 + 0.25*0.5 + 0.075 + 0.15 = 0.4793
        assertTrue(line, line.startsWith("unlock: no chip, best reply:hash-rahul scored 0.48"))
        assertTrue(log.none { "haha" in it })
    }

    @Test
    fun `a plain direct message gets a chip at the first unlock after it arrives`() = with(Script()) {
        at(at(16, 0), Event.ScreenOff)
        at(at(16, 5), Event.NotificationPosted(Fixtures.rahul(at(16, 5), "hey")))
        // 0.35*exp(-5/30) + 0.125 + 0.25*0.5 + 0.075 + 0.15 = 0.7713
        val chip = at(at(16, 10), Event.Unlocked).one<Effect.ShowChip>().chip
        assertEquals("Here to reply to Rahul?", chip.title)
        assertEquals("“hey” · 5 min ago", chip.subtitle)
    }

    @Test
    fun `a plain message doesn't come back as a chip at later unlocks`() = with(Script()) {
        at(at(16, 5), Event.NotificationPosted(Fixtures.rahul(at(16, 5), "hey")))
        assertTrue(at(at(16, 5, 30), Event.Unlocked).any { it is Effect.ShowChip })
        at(at(16, 6), Event.ScreenOff)
        // 0.35*exp(-5/30) + 0.125 + 0.125 + 0.075 = 0.6213, with no boost the second time
        assertTrue(at(at(16, 10), Event.Unlocked).none { it is Effect.ShowChip })
        assertTrue(log.last(), "boost 0.00" in log.last())
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
    fun `tapping the notification and leaving the chat closes the loop, but is not counted as a reply`() = with(Script()) {
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        val effects = at(at(16, 12), Event.NotificationRemoved(Fixtures.rahul(at(16, 11)).key, RemovalReason.Click)) +
            at(at(16, 12, 1), Event.ForegroundChanged(Fixtures.CHATS, "Chats")) +
            at(at(16, 13), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        assertTrue(effects.none { it is Effect.RecordContact })
        assertEquals(0, engine.openLoops)
    }

    @Test
    fun `a message that comes in while you use the phone gets no chip, and drift brings it back`() =
        with(Script(Fixtures.settings.copy(driftAfter = java.time.Duration.ofSeconds(10)))) {
            assertTrue(at(at(16, 0), Event.Unlocked).none { it is Effect.ShowChip })
            at(at(16, 0, 5), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))

            // Its heads-up already showed, so a chip on top would only repeat it.
            assertTrue(at(at(16, 1), Event.NotificationPosted(Fixtures.rahul(at(16, 1), "hey"))).none { it is Effect.ShowChip })
            val pill = at(at(16, 1, 11), Event.Tick).one<Effect.ShowPill>().pill
            assertEquals("Before you forget", pill.label)
            assertEquals("Reply to Rahul", pill.task)
            assertEquals("Do it now", pill.verb)
        }

    @Test
    fun `with the smart timer, an urgent message comes back after half of the usual 45 seconds`() =
        with(Script(Fixtures.settings.copy(smartDrift = true))) {
            at(at(16, 0), Event.Unlocked)
            at(at(16, 0, 5), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
            at(at(16, 1), Event.NotificationPosted(Fixtures.rahul(at(16, 1))))
            assertTrue(log.last(), "pill after 23 s (usual visit 45 s, still learning · urgent ×0.5)" in log.last())

            assertTrue(at(at(16, 1, 22), Event.Tick).none { it is Effect.ShowPill })
            assertEquals("Reply to Rahul", at(at(16, 1, 23), Event.Tick).one<Effect.ShowPill>().pill.task)
        }

    @Test
    fun `with the smart timer, a plain message waits for your usual visit to that feed`() =
        with(Script(Fixtures.settings.copy(smartDrift = true), Stats(apps = mapOf(Fixtures.FEED to AppStat(visits = 6, medianVisitSeconds = 60.0))))) {
            at(at(16, 0), Event.Unlocked)
            at(at(16, 0, 5), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
            at(at(16, 1), Event.NotificationPosted(Fixtures.priya(at(16, 1))))

            assertTrue(at(at(16, 1, 59), Event.Tick).none { it is Effect.ShowPill })
            assertEquals("Reply to Priya", at(at(16, 2), Event.Tick).one<Effect.ShowPill>().pill.task)
        }

    @Test
    fun `holding the chip makes the smart timer a quarter shorter`() = with(Script(Fixtures.settings.copy(smartDrift = true))) {
        at(at(16, 0), Event.NotificationPosted(Fixtures.priya(at(16, 0))))
        val chip = at(at(16, 2), Event.Unlocked).one<Effect.ShowChip>().chip
        at(at(16, 2, 3), Event.ChipHeld(chip.loopId))
        at(at(16, 2, 5), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))

        assertTrue(at(at(16, 2, 38), Event.Tick).none { it is Effect.ShowPill })
        assertEquals("Reply to Priya", at(at(16, 2, 39), Event.Tick).one<Effect.ShowPill>().pill.task)
    }

    @Test
    fun `time in a feed app is learned, but not a visit a pill cut short`() = with(Script(Fixtures.settings.copy(smartDrift = true))) {
        at(at(16, 0), Event.Unlocked)
        at(at(16, 0, 5), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        val learned = at(at(16, 0, 45), Event.ForegroundChanged(Fixtures.CHATS, "Chats")).one<Effect.RecordApp>()
        assertEquals(Effect.RecordApp(Fixtures.FEED, AppStat(visits = 1, medianVisitSeconds = 40.0)), learned)

        // Passing through for a moment isn't a visit.
        at(at(16, 1), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        assertTrue(at(at(16, 1, 2), Event.ForegroundChanged(Fixtures.CHATS, "Chats")).none { it is Effect.RecordApp })

        at(at(16, 2), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        at(at(16, 2, 10), Event.NotificationPosted(Fixtures.rahul(at(16, 2, 10))))
        at(at(16, 2, 33), Event.Tick).one<Effect.ShowPill>()
        assertTrue(at(at(16, 2, 40), Event.ForegroundChanged(Fixtures.CHATS, "Chats")).none { it is Effect.RecordApp })

        // Putting the phone down ends a visit too.
        at(at(16, 3), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        assertEquals(AppStat(visits = 2, medianVisitSeconds = 50.0), at(at(16, 4), Event.ScreenOff).one<Effect.RecordApp>().stat)
    }

    @Test
    fun `demo mode doesn't learn from feed visits`() = with(Script(Fixtures.settings.copy(demoMode = true))) {
        at(at(16, 0), Event.Unlocked)
        at(at(16, 0, 5), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        assertTrue(at(at(16, 0, 45), Event.ForegroundChanged(Fixtures.CHATS, "Chats")).none { it is Effect.RecordApp })
    }

    @Test
    fun `a message that came in while you used the phone doesn't come back as a chip at the next unlock`() = with(Script()) {
        at(at(16, 0), Event.Unlocked)
        at(at(16, 1), Event.NotificationPosted(Fixtures.rahul(at(16, 1), "hey")))
        at(at(16, 2), Event.ScreenOff)
        // 0.35*exp(-3/30) + 0.125 + 0.125 + 0.075 = 0.6417: you saw its heads-up, so no boost
        assertTrue(at(at(16, 4), Event.Unlocked).none { it is Effect.ShowChip })
        assertTrue(log.last(), "boost 0.00" in log.last())

        // One that came in while the phone was down still gets the first unlock after it.
        at(at(16, 5), Event.ScreenOff)
        at(at(16, 6), Event.NotificationPosted(Fixtures.priya(at(16, 6))))
        assertEquals("Here to reply to Priya?", at(at(16, 8), Event.Unlocked).one<Effect.ShowChip>().chip.title)
    }

    @Test
    fun `a message during use doesn't take over a guess you came for`() =
        with(Script(Fixtures.settings.copy(driftAfter = java.time.Duration.ofSeconds(10)))) {
            at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
            val chip = at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip
            at(at(16, 12, 3), Event.ChipHeld(chip.loopId))
            at(at(16, 12, 10), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))

            at(at(16, 12, 15), Event.NotificationPosted(Fixtures.priya(at(16, 12, 15))))
            val pill = at(at(16, 12, 21), Event.Tick).one<Effect.ShowPill>().pill
            assertEquals("You came here to", pill.label)
            assertEquals("Reply to Rahul", pill.task)
        }

    @Test
    fun `a newer message during use takes over from an earlier one`() =
        with(Script(Fixtures.settings.copy(driftAfter = java.time.Duration.ofSeconds(10)))) {
            at(at(16, 0), Event.Unlocked)
            at(at(16, 0, 5), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
            at(at(16, 1), Event.NotificationPosted(Fixtures.rahul(at(16, 1), "hey")))
            at(at(16, 1, 5), Event.NotificationPosted(Fixtures.priya(at(16, 1, 5))))
            assertTrue("drift starts again from the newer message", at(at(16, 1, 11), Event.Tick).none { it is Effect.ShowPill })
            assertEquals("Reply to Priya", at(at(16, 1, 16), Event.Tick).one<Effect.ShowPill>().pill.task)
        }

    @Test
    fun `after the listener reconnects mid-session, the foreground poll's ticks say the phone is in use`() =
        with(Script(Fixtures.settings.copy(driftAfter = java.time.Duration.ofSeconds(10)))) {
            // No unlock and no app switch since the engine started: an update was installed while you were on the phone.
            at(at(16, 0), Event.Tick)
            at(at(16, 1), Event.NotificationPosted(Fixtures.rahul(at(16, 1), "hey")))
            at(at(16, 1, 1), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
            assertEquals("Before you forget", at(at(16, 1, 12), Event.Tick).one<Effect.ShowPill>().pill.label)
        }

    @Test
    fun `an old message delivered late during use doesn't become the guess`() =
        with(Script(Fixtures.settings.copy(driftAfter = java.time.Duration.ofSeconds(10)))) {
            at(at(16, 0), Event.Unlocked)
            at(at(16, 0, 5), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
            // Sent at 15:56 while the phone was offline, or replayed when the listener reconnects.
            // 0.35*exp(-5/30) + 0.125 + 0.125 + 0.075 + 0.15 = 0.7713 would clear the bar, but it isn't new.
            at(at(16, 1), Event.NotificationPosted(Fixtures.rahul(at(15, 56), "hey")))
            assertTrue(at(at(16, 2), Event.Tick).none { it is Effect.ShowPill })
        }

    @Test
    fun `tapping any reply notification opens its chat with the reply strip, not only the guessed one`() = with(Script()) {
        val rahul = Fixtures.rahul(at(16, 0), "hey")
        at(at(16, 0), Event.NotificationPosted(rahul))
        at(at(16, 11, 30), Event.NotificationPosted(Fixtures.priya(at(16, 11, 30))))
        assertEquals("Here to reply to Priya?", at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip.title)

        // You pull down the shade and tap Rahul's message instead.
        val tapped = at(at(16, 12, 20), Event.NotificationRemoved(rahul.key, RemovalReason.Click))
        assertEquals(Effect.SuggestReplies(LoopId("reply:hash-rahul"), rahul.key, "Rahul"), tapped.one<Effect.SuggestReplies>())
        assertTrue(Effect.HideOverlay(Overlay.Chip) in tapped)
        assertTrue(tapped.none { it is Effect.RecordLoop || it is Effect.Forget })

        at(at(16, 12, 21), Event.ForegroundChanged(Fixtures.CHATS, "Chats"))
        assertEquals(rahul.key, at(at(16, 12, 30), Event.ReplyChosen(LoopId("reply:hash-rahul"), "Yes, 5 works!")).one<Effect.SendReply>().notificationKey)
        assertTrue(Effect.Forget(rahul.key) in at(at(16, 12, 31), Event.ReplySent(LoopId("reply:hash-rahul"))))
    }

    @Test
    fun `a new message from someone whose pill already showed gets a pill of its own`() = with(Script()) {
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        val chip = at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip
        at(at(16, 12, 3), Event.ChipHeld(chip.loopId))
        at(at(16, 12, 10), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        assertTrue(at(at(16, 13, 41), Event.Tick).any { it is Effect.ShowPill })
        // You leave the pill and put the phone down without replying.
        at(at(16, 15), Event.ScreenOff)

        at(at(16, 40), Event.NotificationPosted(Fixtures.rahul(at(16, 40), "you there?")))
        at(at(16, 41), Event.Unlocked).one<Effect.ShowChip>()
        at(at(16, 41, 10), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        assertEquals("Reply to Rahul", at(at(16, 42, 41), Event.Tick).one<Effect.ShowPill>().pill.task)
    }

    @Test
    fun `a chat opened from its notification is a detour, and the guess you held comes back after it`() =
        with(Script(Fixtures.settings.copy(driftAfter = java.time.Duration.ofSeconds(10)))) {
            at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
            val chip = at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip
            at(at(16, 12, 3), Event.ChipHeld(chip.loopId))

            val priya = Fixtures.priya(at(16, 12, 30))
            at(at(16, 12, 30), Event.NotificationPosted(priya))
            val tapped = at(at(16, 12, 40), Event.NotificationRemoved(priya.key, RemovalReason.Click))
            assertEquals(LoopId("reply:hash-priya"), tapped.one<Effect.SuggestReplies>().loopId)
            assertTrue("the dot still keeps Rahul", Effect.HideOverlay(Overlay.Dot) !in tapped)

            at(at(16, 12, 41), Event.ForegroundChanged(Fixtures.CHATS, "Chats"))
            at(at(16, 13), Event.ReplyChosen(LoopId("reply:hash-priya"), "Sure"))
            val sent = at(at(16, 13, 1), Event.ReplySent(LoopId("reply:hash-priya")))
            assertTrue(Effect.HideOverlay(Overlay.ReplyStrip) in sent)
            assertTrue(Effect.HideOverlay(Overlay.Dot) !in sent)

            at(at(16, 13, 5), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
            val pill = at(at(16, 13, 16), Event.Tick).one<Effect.ShowPill>().pill
            assertEquals("You came here to", pill.label)
            assertEquals("Reply to Rahul", pill.task)
        }

    @Test
    fun `tapping another chat's notification while a chat is open moves the strip to it`() = with(Script()) {
        val rahul = Fixtures.rahul(at(16, 0), "hey")
        val priya = Fixtures.priya(at(16, 11, 30))
        at(at(16, 0), Event.NotificationPosted(rahul))
        at(at(16, 11, 30), Event.NotificationPosted(priya))
        at(at(16, 12), Event.Unlocked)
        at(at(16, 12, 10), Event.NotificationRemoved(priya.key, RemovalReason.Click))
        at(at(16, 12, 11), Event.ForegroundChanged(Fixtures.CHATS, "Chats"))

        // Rahul's heads-up comes down over Priya's chat, and you tap it.
        val moved = at(at(16, 12, 40), Event.NotificationRemoved(rahul.key, RemovalReason.Click))
        assertEquals(LoopId("reply:hash-rahul"), moved.one<Effect.SuggestReplies>().loopId)
        assertEquals("Priya's chat was read", Outcome.Done, moved.one<Effect.RecordLoop>().entry.outcome)
        assertTrue(Effect.Forget(priya.key) in moved)
        assertEquals(1, engine.openLoops)
    }

    @Test
    fun `when the chat app clears its notification on open, the reply strip stays and still sends`() = with(Script()) {
        val key = Fixtures.rahul(at(16, 11)).key
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        val chip = at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip
        assertTrue(at(at(16, 12, 2), Event.ChipActed(chip.loopId)).any { it is Effect.SuggestReplies })
        at(at(16, 12, 3), Event.ForegroundChanged(Fixtures.CHATS, "Chats"))

        // WhatsApp and Messages clear the notification as soon as the chat opens.
        val cleared = at(at(16, 12, 4), Event.NotificationRemoved(key, RemovalReason.AppCancel))
        assertTrue(cleared.toString(), cleared.none { it is Effect.HideOverlay || it is Effect.Forget || it is Effect.RecordLoop })

        assertEquals(key, at(at(16, 12, 10), Event.ReplyChosen(chip.loopId, "Yes, 5 works!")).one<Effect.SendReply>().notificationKey)
        val sent = at(at(16, 12, 11), Event.ReplySent(chip.loopId))
        assertEquals(Outcome.Done, sent.one<Effect.RecordLoop>().entry.outcome)
        assertTrue(Effect.Forget(key) in sent)
    }

    @Test
    fun `opening the guessed chat from its notification shows the strip, and leaving the app closes the loop`() = with(Script()) {
        val key = Fixtures.rahul(at(16, 11)).key
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        val chip = at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip
        at(at(16, 12, 3), Event.ChipHeld(chip.loopId))

        val tapped = at(at(16, 12, 20), Event.NotificationRemoved(key, RemovalReason.Click))
        assertEquals(Effect.SuggestReplies(chip.loopId, key, "Rahul"), tapped.one<Effect.SuggestReplies>())
        assertTrue(tapped.none { it is Effect.RecordLoop || it is Effect.Forget })
        at(at(16, 12, 21), Event.ForegroundChanged(Fixtures.CHATS, "Chats"))

        val left = at(at(16, 13), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        assertEquals(Outcome.Done, left.one<Effect.RecordLoop>().entry.outcome)
        assertTrue(Effect.HideOverlay(Overlay.ReplyStrip) in left)
        assertTrue(Effect.Forget(key) in left)
        assertTrue("no pill for a chat you just left", at(at(16, 20), Event.Tick).none { it is Effect.ShowPill })
    }

    @Test
    fun `turning the screen off with the chat open closes the loop`() = with(Script()) {
        val key = Fixtures.rahul(at(16, 11)).key
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        val chip = at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip
        at(at(16, 12, 2), Event.ChipActed(chip.loopId))
        at(at(16, 12, 4), Event.NotificationRemoved(key, RemovalReason.AppCancel))
        assertEquals(Outcome.Done, at(at(16, 14), Event.ScreenOff).one<Effect.RecordLoop>().entry.outcome)
        assertTrue(at(at(16, 30), Event.Unlocked).none { it is Effect.ShowChip })
    }

    @Test
    fun `an app clearing the guessed notification while you are elsewhere still closes it`() = with(Script()) {
        val key = Fixtures.rahul(at(16, 11)).key
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        at(at(16, 12), Event.Unlocked)
        at(at(16, 12, 5), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        // Read on another device, say: the chat app clears it, but you never opened the chat here.
        assertTrue(at(at(16, 13), Event.NotificationRemoved(key, RemovalReason.AppCancel)).none { it is Effect.SuggestReplies })
        // It waits a moment, in case the chat app is in front but the foreground poll hasn't seen it yet.
        assertTrue(at(at(16, 13, 1), Event.Tick).none { it is Effect.RecordLoop })
        val closed = at(at(16, 13, 3), Event.Tick)
        assertEquals(Outcome.Done, closed.one<Effect.RecordLoop>().entry.outcome)
        assertTrue(Effect.Forget(key) in closed)
        assertTrue(closed.none { it is Effect.SuggestReplies })
    }

    @Test
    fun `opening the chat app yourself shows the strip, even when it clears the notification before it is seen in front`() = with(Script()) {
        val key = Fixtures.rahul(at(16, 11)).key
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        at(at(16, 12), Event.Unlocked)
        at(at(16, 12, 5), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))

        // You switch to WhatsApp from Recents, and it clears the notification before the next foreground poll.
        val cleared = at(at(16, 12, 30), Event.NotificationRemoved(key, RemovalReason.AppCancel))
        assertTrue(cleared.toString(), cleared.none { it is Effect.RecordLoop || it is Effect.Forget })
        val opened = at(at(16, 12, 31), Event.ForegroundChanged(Fixtures.CHATS, "Chats"))
        assertEquals(Effect.SuggestReplies(LoopId("reply:hash-rahul"), key, "Rahul"), opened.one<Effect.SuggestReplies>())
        assertTrue(Effect.HideOverlay(Overlay.Chip) in opened)
        assertTrue("open in the chat after the wait", at(at(16, 12, 40), Event.Tick).none { it is Effect.RecordLoop })
    }

    @Test
    fun `turning the screen off while waiting to see the chat app closes the loop`() = with(Script()) {
        val key = Fixtures.rahul(at(16, 11)).key
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        at(at(16, 12), Event.Unlocked)
        at(at(16, 12, 5), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        at(at(16, 12, 30), Event.NotificationRemoved(key, RemovalReason.AppCancel))
        assertEquals(Outcome.Done, at(at(16, 12, 31), Event.ScreenOff).one<Effect.RecordLoop>().entry.outcome)
        assertEquals(0, engine.openLoops)
    }

    @Test
    fun `after a hand-back, leaving the chat app means a later clear closes the loop`() = with(Script()) {
        val key = Fixtures.rahul(at(16, 11)).key
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        val chip = at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip
        at(at(16, 12, 2), Event.ChipActed(chip.loopId))
        at(at(16, 12, 3), Event.ForegroundChanged(Fixtures.CHATS, "Chats"))
        // The app kept its notification, and you left without replying.
        assertTrue(Effect.HideOverlay(Overlay.ReplyStrip) in at(at(16, 13), Event.ForegroundChanged(Fixtures.FEED, "Instagram")))

        // Read on WhatsApp Web: the app clears it while you are still elsewhere.
        at(at(16, 14), Event.NotificationRemoved(key, RemovalReason.AppCancel))
        assertEquals(Outcome.Done, at(at(16, 14, 3), Event.Tick).one<Effect.RecordLoop>().entry.outcome)
    }

    @Test
    fun `opening the guessed chat from its notification hides the pill that was showing`() = with(Script()) {
        val key = Fixtures.rahul(at(16, 11)).key
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        at(at(16, 12), Event.Unlocked)
        at(at(16, 12, 10), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        assertTrue(at(at(16, 14), Event.Tick).any { it is Effect.ShowPill })

        val tapped = at(at(16, 14, 30), Event.NotificationRemoved(key, RemovalReason.Click))
        assertTrue(tapped.toString(), Effect.HideOverlay(Overlay.Pill) in tapped && Effect.HideOverlay(Overlay.Chip) in tapped)
        assertTrue(tapped.any { it is Effect.SuggestReplies })
        assertTrue(tapped.none { it is Effect.RecordLoop })
    }

    @Test
    fun `a new message after the chat opened shows its notification again, so the loop isn't closed at screen off`() = with(Script()) {
        val key = Fixtures.rahul(at(16, 11)).key
        at(at(16, 11), Event.NotificationPosted(Fixtures.rahul(at(16, 11))))
        val chip = at(at(16, 12), Event.Unlocked).one<Effect.ShowChip>().chip
        at(at(16, 12, 2), Event.ChipActed(chip.loopId))
        at(at(16, 12, 4), Event.NotificationRemoved(key, RemovalReason.AppCancel))

        // You moved to another chat in the same app, and Rahul wrote again under a new notification.
        val again = at(at(16, 13), Event.NotificationPosted(Fixtures.rahul(at(16, 13), "still free at 5?", key = "k-rahul-2")))
        assertTrue("the cleared notification's way back is dropped", Effect.Forget(key) in again)

        assertTrue(at(at(16, 14), Event.ScreenOff).none { it is Effect.RecordLoop })
        assertEquals(1, engine.openLoops)
    }

    @Test
    fun `a removed notification Earmark never tracked is forgotten`() = with(Script()) {
        assertEquals(listOf<Effect>(Effect.Forget("k-unknown")), at(at(16, 0), Event.NotificationRemoved("k-unknown", RemovalReason.AppCancel)))
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
