package com.sweepiesoftie.earmark.core

import com.sweepiesoftie.earmark.core.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** Custom triggers from Advanced (ADR 0038). Tuesday 13 October 2026, as in the other stories. */
class TriggersTest {
    private val bank = "com.snapwork.hdfc"
    private val irctc = "cris.org.in.prs.ima"
    private val duolingo = "com.duolingo"
    private val launcher = "com.google.android.apps.nexuslauncher"

    private fun settings(vararg triggers: Trigger): Settings =
        Fixtures.settings.copy(triggers = triggers.toList(), homeApps = setOf(launcher), driftTimer = DriftTimer.RightAway)

    private fun hdfc(at: Instant, text: String, key: String = "0|$bank|3|null|10400"): Signal =
        Signal(key = key, packageName = bank, appLabel = "HDFC Bank", postedAt = at, title = "HDFC Bank", text = text)

    private val kyc = Trigger("t1", bank, "HDFC Bank", Watch.NotificationWords, words = listOf("KYC", "due"))
    private val proceed = Trigger("t2", irctc, "IRCTC", Watch.ScreenWords, words = listOf("Proceed to pay"), kind = LoopKind.Flow)
    private val streak = Trigger("t3", duolingo, "Duolingo", Watch.NotOpenedBy, by = LocalTime.of(21, 0))

    /** The listener reads today's usage history for time triggers' apps as it starts, before anything else happens. */
    private fun timed(trigger: Trigger = streak): Script =
        Script(settings(trigger)).also { it.at(at(0, 1), Event.AppVisits(setOf(trigger.packageName), emptyMap())) }

    @Test
    fun `words match whole, in any case, any one of them`() {
        val words = listOf("due", "Proceed to pay", "KYC")
        assertNull("not inside a longer word", Triggers.match(words, "Your bill is OVERDUE"))
        assertEquals("due", Triggers.match(words, "Payment DUE tomorrow"))
        assertEquals("a phrase across any gap", "Proceed to pay", Triggers.match(words, "Review journey\nproceed  to\nPAY"))
        assertEquals("KYC", Triggers.match(words, "kyc pending."))
        assertEquals("बिल", Triggers.match(listOf("बिल"), "आपका बिल देय है"))
        assertNull("no words, no match", Triggers.match(listOf(" ", ""), "anything"))
        assertEquals(listOf("KYC", "due", "proceed to pay"), Trigger.words("KYC, due,,\n proceed to pay , due"))
    }

    @Test
    fun `a notification with one of the words becomes the kind you picked, quoting what it said`() = with(Script(settings(kyc))) {
        val posted = at(at(10, 0), Event.NotificationPosted(hdfc(at(10, 0), "Complete your KYC by Friday to keep your account active")))
        val entry = posted.one<Effect.RecordLoop>().entry
        assertEquals(LoopKind.Reminder, entry.kind)
        assertEquals("Check HDFC Bank", entry.label)
        assertTrue("on today's list as it arrives", entry.listed)
        assertEquals(at(10, 0), posted.one<Effect.RecordTrigger>().stat.lastMatched)

        val note = engine.waiting(at(10, 1)).single().note
        assertEquals("HDFC Bank", note.who)
        assertEquals("“Complete your KYC by Friday to keep your account active”", note.words)
        assertEquals("Open", note.verb)
        assertFalse("an app, so its icon", note.person)

        assertTrue("no words, no loop", at(at(10, 2), Event.NotificationPosted(hdfc(at(10, 2), "Your statement is ready", key = "k2"))).none { it is Effect.RecordLoop })
        val closed = at(at(10, 5), Event.NotificationRemoved("0|$bank|3|null|10400", RemovalReason.Click)).one<Effect.RecordLoop>().entry
        assertEquals(Outcome.Done, closed.outcome)
    }

    @Test
    fun `its label stands in for what it saw, and one notification is one loop`() = with(Script(settings(kyc.copy(label = "Update KYC at the branch")))) {
        at(at(10, 0), Event.NotificationPosted(hdfc(at(10, 0), "KYC pending")))
        at(at(10, 1), Event.NotificationPosted(hdfc(at(10, 1), "Card payment due on 30 Oct", key = "k2")))
        assertEquals(2, engine.openLoops)
        val notes = engine.waiting(at(10, 2)).map { it.note.words }
        assertEquals(listOf("Update KYC at the branch", "Update KYC at the branch"), notes)
        assertEquals("Update KYC at the branch", engine.waiting(at(10, 2)).first().task)
    }

    @Test
    fun `any notification from the app counts, but never a one-time code or an ongoing one`() =
        with(Script(settings(kyc.copy(watch = Watch.AnyNotification, words = emptyList())))) {
            assertTrue(at(at(10, 0), Event.NotificationPosted(hdfc(at(10, 0), "Your statement is ready"))).any { it is Effect.RecordLoop })
            assertTrue(at(at(10, 1), Event.NotificationPosted(hdfc(at(10, 1), "Your OTP is 482913. Do not share it.", key = "otp"))).none { it is Effect.RecordLoop })
            val ongoing = hdfc(at(10, 2), "Uploading documents", key = "up").copy(isOngoing = true)
            assertTrue(at(at(10, 2), Event.NotificationPosted(ongoing)).none { it is Effect.RecordLoop })
            assertEquals(1, engine.openLoops)
        }

    @Test
    fun `a message Earmark already reads as a reply stays a reply, and is owed for hours`() {
        val chats = Trigger("t4", Fixtures.CHATS, "WhatsApp", Watch.AnyNotification)
        fun chipAfterAWhile(settings: Settings): Effect.ShowChip? = with(Script(settings)) {
            at(at(9, 0), Event.Unlocked)
            at(at(9, 1), Event.ScreenOff)
            at(at(9, 5), Event.NotificationPosted(Fixtures.priya(at(9, 5), "on my way home")))
            at(at(10, 35), Event.Unlocked).filterIsInstance<Effect.ShowChip>().singleOrNull()
        }
        assertNull("chatter fades in half an hour", chipAfterAWhile(settings()))
        val chip = chipAfterAWhile(settings(chats))?.chip
        assertNotNull("owed, so it's still offered", chip)
        assertEquals(LoopKind.Reply, chip!!.kind)
        assertEquals("Reply", chip.note.verb)
        assertEquals("Priya", chip.note.who)
    }

    @Test
    fun `an ignore wins over Earmark's own reading and over an earmark trigger`() {
        val sms = Fixtures.SMS
        val ignore = Trigger("t5", sms, "Messages", Watch.NotificationWords, words = listOf("airtel"), ignore = true)
        val earmark = Trigger("t6", sms, "Messages", Watch.AnyNotification)
        with(Script(settings(earmark, ignore))) {
            val posted = at(at(10, 0), Event.NotificationPosted(Fixtures.airtel(at(10, 0))))
            assertTrue(posted.none { it is Effect.RecordLoop })
            assertEquals("t5", posted.one<Effect.RecordTrigger>().id)
            assertTrue(log.any { "ignored by trigger t5" in it })
            assertEquals(0, engine.openLoops)
        }
    }

    @Test
    fun `ignoring a whole app stops its flows and the tile`() {
        val uber = Trigger("t7", Fixtures.RIDE, "Uber", Watch.AnyNotification, ignore = true)
        with(Script(settings(uber))) {
            at(at(16, 0), Event.Unlocked)
            at(at(16, 0, 2), Event.ForegroundChanged(Fixtures.RIDE, "Uber"))
            assertEquals(TileState(ignored = "Uber"), engine.tile)
            at(at(16, 0, 30), Event.ForegroundChanged(launcher, "Home"))
            assertEquals("no flow", 0, engine.openLoops)
            assertTrue(log.any { "trigger t7 ignores it" in it })
        }
        with(Script(settings())) {
            at(at(16, 0), Event.Unlocked)
            at(at(16, 0, 2), Event.ForegroundChanged(Fixtures.RIDE, "Uber"))
            at(at(16, 0, 30), Event.ForegroundChanged(launcher, "Home"))
            assertEquals("without it, a flow", 1, engine.openLoops)
        }
    }

    @Test
    fun `a screen trigger fires as you leave with its words showing, and closes when you leave without them`() = with(Script(settings(proceed))) {
        at(at(16, 0), Event.Unlocked)
        at(at(16, 0, 2), Event.ForegroundChanged(irctc, "IRCTC"))
        assertTrue("not while you're still there", at(at(16, 0, 30), Event.ScreenRead(irctc, "Review journey\nTotal ₹1,240\nProceed to Pay")).none { it is Effect.RecordLoop })

        val entry = at(at(16, 1), Event.ForegroundChanged(Fixtures.FEED, "Instagram")).one<Effect.RecordLoop>().entry
        assertEquals(LoopKind.Flow, entry.kind)
        assertEquals("Finish in IRCTC", entry.label)
        val pill = at(at(16, 1, 4), Event.Tick).one<Effect.ShowPill>().pill
        assertEquals("IRCTC", pill.note.who)
        assertEquals("“Proceed to pay”", pill.note.words)
        assertEquals("Finish", pill.note.verb)
        assertEquals("Before you forget", pill.why)
        assertEquals(Route.LaunchApp(irctc), at(at(16, 1, 6), Event.PillTapped(pill.loopId)).one<Effect.OpenWayBack>().route)

        at(at(16, 1, 8), Event.ForegroundChanged(irctc, "IRCTC"))
        at(at(16, 2), Event.ScreenRead(irctc, "Booking confirmed. PNR 4521897"))
        val closed = at(at(16, 2, 30), Event.ForegroundChanged(launcher, "Home")).one<Effect.RecordLoop>().entry
        assertEquals(Outcome.Done, closed.outcome)
        assertTrue(closed.broughtBack)
    }

    @Test
    fun `a pay trigger on a screen keeps the amount it showed`() = with(Script(settings(proceed.copy(kind = LoopKind.Payment)))) {
        at(at(16, 0), Event.Unlocked)
        at(at(16, 0, 2), Event.ForegroundChanged(irctc, "IRCTC"))
        at(at(16, 0, 30), Event.ScreenRead(irctc, "Total ₹1,240\nProceed to Pay"))
        assertEquals("Pay ₹1,240 in IRCTC", at(at(16, 1), Event.ForegroundChanged(launcher, "Home")).one<Effect.RecordLoop>().entry.label)
    }

    @Test
    fun `a screen trigger can fire as soon as its words show, and stays while they do`() =
        with(Script(settings(proceed.copy(timing = ScreenTiming.AsSoonAsShown)))) {
            at(at(16, 0), Event.Unlocked)
            at(at(16, 0, 2), Event.ForegroundChanged(irctc, "IRCTC"))
            assertEquals("Finish in IRCTC", at(at(16, 0, 30), Event.ScreenRead(irctc, "Proceed to Pay")).one<Effect.RecordLoop>().entry.label)
            assertTrue("the same words again open nothing new", at(at(16, 0, 31), Event.ScreenRead(irctc, "Proceed to Pay ")).isEmpty())
            assertTrue(at(at(16, 1), Event.ForegroundChanged(launcher, "Home")).none { it is Effect.RecordLoop })
            assertEquals(1, engine.openLoops)
        }

    @Test
    fun `a visit screen reading didn't see leaves the loop alone`() = with(Script(settings(proceed))) {
        at(at(16, 0), Event.Unlocked)
        at(at(16, 0, 2), Event.ForegroundChanged(irctc, "IRCTC"))
        at(at(16, 0, 30), Event.ScreenRead(irctc, "Proceed to Pay"))
        at(at(16, 1), Event.ForegroundChanged(launcher, "Home"))
        at(at(16, 2), Event.ForegroundChanged(irctc, "IRCTC"))
        assertTrue(at(at(16, 3), Event.ForegroundChanged(launcher, "Home")).none { it is Effect.RecordLoop })
        assertEquals(1, engine.openLoops)
    }

    @Test
    fun `a read that arrives before the foreground poll sees the switch still counts`() = with(Script(settings(proceed))) {
        at(at(16, 0), Event.Unlocked)
        at(at(16, 0, 1), Event.ScreenRead(irctc, "Proceed to Pay"))
        at(at(16, 0, 2), Event.ForegroundChanged(irctc, "IRCTC"))
        assertEquals("Finish in IRCTC", at(at(16, 1), Event.ForegroundChanged(launcher, "Home")).one<Effect.RecordLoop>().entry.label)
    }

    @Test
    fun `an ignore's words on the screen mean leaving there makes nothing`() {
        val booked = Trigger("t8", Fixtures.RIDE, "Uber", Watch.ScreenWords, words = listOf("confirmed"), ignore = true)
        val pay = Trigger("t9", Fixtures.RIDE, "Uber", Watch.ScreenWords, words = listOf("Pay now"), kind = LoopKind.Payment)
        with(Script(settings(booked, pay))) {
            at(at(16, 0), Event.Unlocked)
            at(at(16, 0, 2), Event.ForegroundChanged(Fixtures.RIDE, "Uber"))
            at(at(16, 0, 20), Event.ScreenRead(Fixtures.RIDE, "Your ride is confirmed\nPay now"))
            at(at(16, 0, 40), Event.ForegroundChanged(launcher, "Home"))
            assertEquals("no flow and no pay", 0, engine.openLoops)
        }
    }

    @Test
    fun `a time trigger opens at its time when the app wasn't opened, and closes after a proper visit`() = with(timed()) {
        at(at(20, 0), Event.Unlocked)
        assertEquals("not before its time", 0, engine.openLoops)
        at(at(20, 5), Event.ScreenOff)

        val chip = at(at(21, 30), Event.Unlocked).one<Effect.ShowChip>().chip
        assertEquals("Duolingo", chip.note.who)
        assertEquals("the clock is its own evidence, so no age", "You wanted to open it by 9:00 PM", chip.note.words)
        assertEquals("Open", chip.note.verb)
        assertEquals("Here to get this done?", chip.why)
        assertEquals(Route.LaunchApp(duolingo), at(at(21, 30, 2), Event.ChipActed(chip.loopId)).one<Effect.OpenWayBack>().route)

        at(at(21, 30, 4), Event.ForegroundChanged(duolingo, "Duolingo"))
        assertTrue("a quick look doesn't count", at(at(21, 30, 14), Event.ForegroundChanged(launcher, "Home")).none { it is Effect.RecordLoop })
        at(at(21, 31), Event.ForegroundChanged(duolingo, "Duolingo"))
        val closed = at(at(21, 35), Event.ForegroundChanged(launcher, "Home")).one<Effect.RecordLoop>().entry
        assertEquals(Outcome.Done, closed.outcome)
        at(at(21, 36), Event.ScreenOff)
        assertTrue("done for today", at(at(22, 0), Event.Unlocked).none { it is Effect.ShowChip })
        assertEquals(0, engine.openLoops)
    }

    @Test
    fun `a time trigger opens while you use the phone, and can bring you back from a feed`() = with(timed()) {
        at(at(20, 58), Event.Unlocked)
        at(at(20, 58, 2), Event.ForegroundChanged(Fixtures.FEED, "Instagram"))
        assertEquals(1, at(at(21, 0, 1), Event.Tick).count { it is Effect.RecordLoop })
        val pill = at(at(21, 0, 6), Event.Tick).one<Effect.ShowPill>().pill
        assertEquals("Duolingo", pill.note.who)
        assertEquals("Before you forget", pill.why)
    }

    @Test
    fun `a time trigger leaves you alone after a proper visit earlier, a quick look doesn't count, and it keeps to its days`() {
        with(timed()) {
            at(at(10, 0), Event.Unlocked)
            at(at(10, 0, 2), Event.ForegroundChanged(duolingo, "Duolingo"))
            at(at(10, 5), Event.ForegroundChanged(launcher, "Home"))
            at(at(10, 6), Event.ScreenOff)
            at(at(21, 30), Event.Unlocked)
            assertEquals(0, engine.openLoops)
        }
        with(timed()) {
            at(at(10, 0), Event.Unlocked)
            at(at(10, 0, 2), Event.ForegroundChanged(duolingo, "Duolingo"))
            at(at(10, 0, 12), Event.ForegroundChanged(launcher, "Home"))
            at(at(10, 6), Event.ScreenOff)
            at(at(21, 30), Event.Unlocked)
            assertEquals(1, engine.openLoops)
        }
        with(Script(settings(streak))) {
            at(at(21, 30), Event.Tick)
            assertEquals("after a restart, it waits for usage history", 0, engine.openLoops)
            at(at(21, 30, 1), Event.AppVisits(setOf(duolingo), mapOf(duolingo to at(9, 0))))
            at(at(21, 30, 2), Event.Unlocked)
            assertEquals("which says you were in it this morning", 0, engine.openLoops)
        }
        with(timed(streak.copy(days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))) {
            at(at(21, 30), Event.Unlocked)
            assertEquals("it's a Tuesday", 0, engine.openLoops)
        }
        with(timed(streak.copy(on = false))) {
            at(at(21, 30), Event.Unlocked)
            assertEquals("off", 0, engine.openLoops)
        }
    }

    @Test
    fun `not this three times mutes the trigger, not the kind, and unmuting brings it back`() = with(Script(settings(kyc))) {
        for (round in 0 until 3) {
            val start = at(10 + round, 0)
            at(start, Event.NotificationPosted(hdfc(start, "KYC pending", key = "k$round")))
            val chip = at(start.plusSeconds(60), Event.Unlocked).one<Effect.ShowChip>().chip
            at(start.plusSeconds(65), Event.ChipDismissed(chip.loopId))
            at(start.plusSeconds(70), Event.ScreenOff)
        }
        assertTrue(log.any { "trigger t1 muted after 3 in a row" in it })
        at(at(13, 0), Event.NotificationPosted(hdfc(at(13, 0), "KYC pending", key = "k3")))
        assertTrue("muted", at(at(13, 1), Event.Unlocked).none { it is Effect.ShowChip })
        assertTrue("still listed as waiting", engine.waiting(at(13, 2)).isNotEmpty())
        at(at(13, 3), Event.ScreenOff)

        at(at(13, 10), Event.NotificationPosted(Fixtures.reminder(at(13, 10))))
        assertEquals("Earmark's own reminders carry on", LoopKind.Reminder, at(at(13, 11), Event.Unlocked).one<Effect.ShowChip>().chip.kind)
        assertEquals("Submit lab report", engine.held()!!.note.who)
        at(at(13, 12), Event.ScreenOff)

        assertEquals(0, at(at(13, 20), Event.TriggerUnmuted("t1")).one<Effect.RecordTrigger>().stat.ignoreStreak)
        at(at(13, 21), Event.NotificationPosted(hdfc(at(13, 21), "KYC pending", key = "k4")))
        assertEquals("HDFC Bank", at(at(13, 22), Event.Unlocked).one<Effect.ShowChip>().chip.note.who)
    }

    @Test
    fun `a pay trigger reads the amount, and closes when it's paid`() =
        with(Script(settings(kyc.copy(watch = Watch.AnyNotification, words = emptyList(), kind = LoopKind.Payment)))) {
            val entry = at(at(10, 0), Event.NotificationPosted(hdfc(at(10, 0), "Gas booking: pay Rs 950 to confirm your cylinder"))).one<Effect.RecordLoop>().entry
            assertEquals("Pay ₹950 in HDFC Bank", entry.label)
            assertEquals("Pay", engine.waiting(at(10, 1)).single().note.verb)
            val closed = at(at(10, 5), Event.NotificationPosted(hdfc(at(10, 5), "Payment successful: Rs 950 paid to Indane", key = "paid"))).one<Effect.RecordLoop>().entry
            assertEquals(Outcome.Done, closed.outcome)
        }

    @Test
    fun `a trigger's last match is written at most once a minute`() = with(Script(settings(kyc))) {
        assertEquals(1, at(at(10, 0), Event.NotificationPosted(hdfc(at(10, 0), "KYC pending"))).count { it is Effect.RecordTrigger })
        assertEquals(0, at(at(10, 0, 30), Event.NotificationPosted(hdfc(at(10, 0, 30), "KYC pending", key = "k2"))).count { it is Effect.RecordTrigger })
        assertEquals(1, at(at(10, 2), Event.NotificationPosted(hdfc(at(10, 2), "KYC pending", key = "k3"))).count { it is Effect.RecordTrigger })
    }

    @Test
    fun `a match before the stored stats arrive keeps the trigger's mute`() {
        val stored = Stats(triggers = mapOf("t1" to TriggerStat(at(9, 0), ignoreStreak = 3)))
        with(Script(settings(kyc))) {
            // After a restart the listener replays the shade before Room's stats have loaded.
            at(at(10, 0), Event.NotificationPosted(hdfc(at(10, 0), "KYC pending")))
            val loaded = at(at(10, 0, 1), Event.StatsLoaded(stored))
            assertEquals("written again with its streak", TriggerStat(at(10, 0), ignoreStreak = 3), loaded.one<Effect.RecordTrigger>().stat)
            assertTrue("still muted", at(at(10, 1), Event.Unlocked).none { it is Effect.ShowChip })
        }
        with(Script(settings(kyc))) {
            at(at(10, 0), Event.TriggerUnmuted("t1"))
            val loaded = at(at(10, 0, 1), Event.StatsLoaded(stored))
            assertEquals("an unmute before then stands, with its last match", TriggerStat(at(9, 0), ignoreStreak = 0), loaded.one<Effect.RecordTrigger>().stat)
        }
    }

    @Test
    fun `a chat a trigger marked owed stays owed when they write again without the words`() {
        val parcel = Trigger("t10", Fixtures.CHATS, "WhatsApp", Watch.NotificationWords, words = listOf("parcel"))
        with(Script(settings(parcel))) {
            at(at(9, 0), Event.Unlocked)
            at(at(9, 1), Event.ScreenOff)
            at(at(9, 5), Event.NotificationPosted(Fixtures.priya(at(9, 5), "the parcel came for you")))
            at(at(9, 6), Event.NotificationPosted(Fixtures.priya(at(9, 6), "on my way home")))
            assertEquals(LoopKind.Reply, at(at(10, 36), Event.Unlocked).one<Effect.ShowChip>().chip.kind)
        }
    }

    @Test
    fun `an ignore doesn't hide that something was paid`() {
        val offers = Trigger("t11", Fixtures.SMS, "Messages", Watch.NotificationWords, words = listOf("offer"), ignore = true)
        with(Script(settings(offers))) {
            at(at(10, 0), Event.NotificationPosted(Fixtures.airtel(at(10, 0))))
            assertEquals(1, engine.openLoops)
            val paid = at(at(10, 5), Event.NotificationPosted(Fixtures.airtel(at(10, 5), "Rs 499 paid to Airtel. Get a cashback offer on your next recharge")))
            assertEquals(Outcome.Done, paid.one<Effect.RecordLoop>().entry.outcome)
            assertEquals(0, engine.openLoops)
        }
    }

    @Test
    fun `a phrase that runs from the title into the text quotes the notification, not a time`() =
        with(Script(settings(kyc.copy(words = listOf("KYC due"))))) {
            at(at(10, 0), Event.NotificationPosted(hdfc(at(10, 0), "due by Friday").copy(title = "KYC")))
            assertEquals("“due by Friday”", engine.waiting(at(10, 1)).single().note.words)
        }

    @Test
    fun `a reply trigger's loop closes when you reply, from an app that gives no conversation`() {
        val parcel = Trigger("t12", Fixtures.SMS, "Messages", Watch.NotificationWords, words = listOf("parcel"), kind = LoopKind.Reply)
        fun courier(at: Instant, text: String, fromUser: Boolean = false): Signal = Signal(
            key = "0|${Fixtures.SMS}|8|bludrt|10200",
            packageName = Fixtures.SMS,
            appLabel = "Messages",
            postedAt = at,
            title = "AX-BLUDRT",
            text = text,
            messages = listOf(Message(if (fromUser) null else "AX-BLUDRT", text, at, fromUser = fromUser)),
            category = "msg",
        )
        with(Script(settings(parcel))) {
            at(at(10, 0), Event.NotificationPosted(courier(at(10, 0), "Your parcel is at the front desk")))
            assertEquals(1, engine.openLoops)
            at(at(10, 5), Event.NotificationPosted(courier(at(10, 5), "Thanks, I'll collect it at 6", fromUser = true)))
            assertEquals(0, engine.openLoops)
        }
    }

    @Test
    fun `a trigger's loop teaches nothing about its app`() = with(timed(streak.copy(kind = LoopKind.Reply))) {
        val chip = at(at(21, 30), Event.Unlocked).one<Effect.ShowChip>().chip
        at(at(21, 30, 2), Event.ChipActed(chip.loopId))
        at(at(21, 30, 4), Event.ForegroundChanged(duolingo, "Duolingo"))
        val closed = at(at(21, 31), Event.ForegroundChanged(launcher, "Home"))
        assertEquals(Outcome.Done, closed.one<Effect.RecordLoop>().entry.outcome)
        assertTrue(closed.none { it is Effect.RecordApp })
    }

    @Test
    fun `a get back to trigger is its own, so the tile still holds an app`() =
        with(Script(settings(kyc.copy(kind = LoopKind.Own)))) {
            at(at(10, 0), Event.NotificationPosted(hdfc(at(10, 0), "KYC pending")))
            at(at(10, 1), Event.Unlocked)
            at(at(10, 1, 2), Event.ForegroundChanged("com.android.chrome", "Chrome"))
            assertEquals(TileState(canHold = "Chrome"), engine.tile)
            at(at(10, 1, 5), Event.Earmarked)
            assertEquals(2, engine.openLoops)
        }
}
