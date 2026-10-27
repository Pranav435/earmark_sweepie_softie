package com.sweepiesoftie.earmark.core

import java.time.Duration
import java.time.Instant

/**
 * The functional core: one event in, a list of effects out. Not thread-safe by design:
 * the listener feeds it from a single dispatcher, so it needs no locks.
 */
public class Engine(settings: Settings, stats: Stats = Stats()) {
    private var settings: Settings = settings
    private var stats: Stats = stats
    private var copy: Copy = Copy(settings)
    private val book = LoopBook()
    private var drift = Drift()
    private var nextAlarm: Instant? = null
    private var calendar: List<CalendarEvent> = emptyList()
    private var foreground: Visit? = null
    private var lastUnlock: Instant? = null
    private val bookedAt = HashMap<String, Instant>()

    /** Routine and Leave-by loops are rebuilt from the clock and calendar, so remember which already closed. */
    private val closedSituations = HashSet<LoopId>()

    private data class Visit(val packageName: String, val appLabel: String, val since: Instant)

    /** How many loops are open, for the home screen. Read it on the engine's thread, like [handle]. */
    public val openLoops: Int get() = book.all.size

    public fun handle(event: Event, now: Instant): List<Effect> = when (event) {
        is Event.NotificationPosted -> onPosted(event.signal, now)
        is Event.NotificationRemoved -> onRemoved(event, now)
        Event.Unlocked -> onUnlocked(now)
        Event.ScreenOff -> onScreenOff(now)
        is Event.ForegroundChanged -> onForeground(event, now)
        is Event.PlaybackChanged -> stepDrift(DriftInput.Media(event.playback), now)
        is Event.BlockerChanged -> stepDrift(DriftInput.Block(event.blocker, event.active), now)
        is Event.AlarmChanged -> onAlarm(event.nextAlarm, now)
        is Event.CalendarSoon -> onCalendar(event.events, now)
        is Event.ChipHeld -> onChipHeld(event.loopId)
        is Event.ChipDismissed -> onChipDismissed(event.loopId)
        is Event.ChipActed -> onChipActed(event.loopId)
        is Event.PillTapped -> onPillTapped(event.loopId)
        is Event.PillDismissed -> onPillDismissed(event.loopId)
        is Event.ReplyChosen -> onReplyChosen(event)
        is Event.ReplySent -> close(event.loopId, Outcome.Done, now, "reply sent", replied = true)
        is Event.KindUnmuted -> setKind(event.kind, KindStat(holds = kindStat(event.kind).holds, ignores = kindStat(event.kind).ignores))
        is Event.SettingsChanged -> {
            this.settings = event.settings
            copy = Copy(event.settings)
            listOf(Effect.Log("settings: drift after ${event.settings.driftAfter.seconds}s, demo ${event.settings.demoMode}, paused ${event.settings.paused}, ${event.settings.feedApps.size} feed apps"))
        }
        is Event.StatsLoaded -> {
            // Counts the engine already holds are newer than any snapshot still being written to Room.
            stats = Stats(
                kinds = event.stats.kinds + stats.kinds,
                contacts = event.stats.contacts + stats.contacts,
                weeknightAlarms = event.stats.weeknightAlarms,
            )
            emptyList()
        }
        Event.Tick -> expire(now) + stepDrift(DriftInput.Tick, now)
    }

    private fun onPosted(signal: Signal, now: Instant): List<Effect> =
        when (val result = Classifier.classify(signal, settings)) {
            is Classification.Open -> {
                val isNew = book[result.loop.id] == null
                book.open(result.loop)
                listOf(Effect.Log("${if (isNew) "opened" else "updated"} ${result.loop.id.value} (${result.loop.kind}, due %.2f) from ${signal.packageName}".format(result.loop.due)))
            }
            is Classification.Replied ->
                book.byConversation(result.conversation)?.let { close(it.loop.id, Outcome.Done, now, "you replied in ${signal.packageName}", replied = true) }
                    ?: listOf(Effect.Log("skip ${signal.packageName}: last message is yours"), Effect.Forget(signal.key))
            is Classification.Booked -> {
                bookedAt[result.packageName] = now
                closeWhere(now, "booked in ${result.packageName}") { CloseRules.closedByBooking(it, result.packageName) }
            }
            is Classification.Paid -> closeWhere(now, "paid in ${result.packageName}") { CloseRules.closedByPayment(it, result.packageName) }
            is Classification.Skip -> listOf(Effect.Log("skip ${signal.packageName}: ${result.reason}"), Effect.Forget(signal.key))
        }

    private fun onRemoved(event: Event.NotificationRemoved, now: Instant): List<Effect> {
        val tracked = book.byNotificationKey(event.key) ?: return listOf(Effect.Forget(event.key))
        if (!opensChat(tracked, event.reason)) {
            return close(tracked.loop.id, CloseRules.onRemoved(event.reason), now, "notification removed (${event.reason})")
        }
        // Chat apps clear the notification as the chat opens. Keep the loop, its reply action and its
        // message text, so the reply strip can show and send; it closes when you reply or leave.
        val loop = tracked.loop
        val handedBack = drift.phase == Phase.HandedBack
        book.update(loop.id) { it.copy(chatOpen = true) }
        drift = drift.copy(phase = Phase.HandedBack)
        val effects = mutableListOf<Effect>(Effect.Log("${loop.id.value}: chat opened and ${loop.packageName} cleared its notification (${event.reason}); closes when you reply or leave"))
        if (!handedBack && loop.notificationKey != null) effects += Effect.SuggestReplies(loop.id, loop.notificationKey)
        return effects
    }

    /** The guessed reply's notification went because you opened its chat: through Earmark, from the notification, or in its app. */
    private fun opensChat(tracked: Tracked, reason: RemovalReason): Boolean {
        val loop = tracked.loop
        if (loop.kind != LoopKind.Reply || !loop.canReply || tracked.chatOpen || drift.loopId != loop.id) return false
        return when (reason) {
            RemovalReason.Click -> true
            RemovalReason.AppCancel -> drift.phase == Phase.HandedBack || foreground?.packageName == loop.packageName
            RemovalReason.UserDismiss, RemovalReason.Other -> false
        }
    }

    private fun onUnlocked(now: Instant): List<Effect> {
        val previousUnlock = lastUnlock
        lastUnlock = now
        val effects = mutableListOf<Effect>()
        effects += hideAll()
        drift = DriftMachine.end(drift)
        effects += expire(now)
        book.all.toList().forEach { book.update(it.loop.id) { t -> t.copy(ignored = false) } }
        effects += openSituations(now)
        if (settings.paused) return effects + Effect.Log("unlock: Earmark is paused")

        val carry = book.all.filter { it.carryOver }.maxByOrNull { it.loop.updatedAt }
        if (carry != null) {
            book.update(carry.loop.id) { it.copy(carryOver = false, guessedAt = now) }
            val score = Ranker.score(carry.loop, now, stats, previousUnlock)
            drift = DriftMachine.guess(drift, carry.loop.id, carry.loop.packageName)
            effects += Effect.Log("unlock: carry-over chip for ${carry.loop.id.value}, score $score")
            effects += Effect.ShowChip(copy.carryOverChip(carry.loop, score.total))
            return effects + resumeForeground(now)
        }

        val candidates = book.all.filter { !kindStat(it.loop.kind).muted }
        val muted = book.all.size - candidates.size
        val best = candidates.map { it to Ranker.score(it.loop, now, stats, previousUnlock) }.maxByOrNull { it.second.total }
        if (best == null) {
            return effects + Effect.Log("unlock: no chip, no open loops" + if (muted > 0) " ($muted muted)" else "")
        }
        val (tracked, score) = best
        if (score.total <= Ranker.CHIP_THRESHOLD) {
            return effects + Effect.Log("unlock: no chip, best ${tracked.loop.id.value} scored $score, needs > ${Ranker.CHIP_THRESHOLD}")
        }
        // The latest guess time, so a loop guessed again today shows on today's recap.
        val guessed = book.update(tracked.loop.id) { it.copy(guessedAt = now) }!!
        drift = DriftMachine.guess(drift, tracked.loop.id, tracked.loop.packageName)
        effects += Effect.Log("unlock: chip for ${tracked.loop.id.value}, score $score of ${candidates.size} open")
        effects += Effect.ShowChip(copy.chip(tracked.loop, score.total, now))
        effects += Effect.RecordLoop(entry(guessed, outcome = null, closedAt = null))
        return effects + resumeForeground(now)
    }

    /** The app in front may have been reported before the unlock: replay it so a feed left open still counts. */
    private fun resumeForeground(now: Instant): List<Effect> =
        foreground?.let { stepDrift(DriftInput.Foreground(it.packageName), now) }.orEmpty()

    private fun onScreenOff(now: Instant): List<Effect> {
        val left = book.all.filter { it.chatOpen }.map { it.loop.id }.flatMap { close(it, Outcome.Done, now, "screen off with the chat open") }
        drift = DriftMachine.end(drift)
        foreground = null
        return left + hideAll()
    }

    private fun onForeground(event: Event.ForegroundChanged, now: Instant): List<Effect> {
        val effects = mutableListOf<Effect>()
        val previous = foreground
        foreground = Visit(event.packageName, event.appLabel, now)
        if (previous != null && previous.packageName != event.packageName) effects += leftApp(previous, now)

        val guess = book[drift.loopId]
        if (guess != null && drift.phase == Phase.HandedBack && event.packageName != guess.loop.packageName &&
            event.packageName != settings.ownPackage
        ) {
            effects += if (guess.chatOpen) close(guess.loop.id, Outcome.Done, now, "left the chat") else listOf(Effect.HideOverlay(Overlay.ReplyStrip))
        }
        if (guess?.loop?.kind == LoopKind.LeaveBy && guess.loop.leaveBy?.isMeeting == true && event.packageName in meetingApps) {
            effects += close(guess.loop.id, Outcome.Done, now, "meeting app opened")
        }
        return effects + stepDrift(DriftInput.Foreground(event.packageName), now)
    }

    /** Leaving a ride, food or shopping app within 2 minutes, with nothing booked, leaves a flow unfinished. */
    private fun leftApp(visit: Visit, now: Instant): List<Effect> {
        val flowApps = CuratedApps.flows + listOfNotNull(settings.rideApp)
        if (visit.packageName !in flowApps) return emptyList()
        if (Duration.between(visit.since, now) >= Situations.flowDwell) return emptyList()
        val booked = bookedAt[visit.packageName]
        if (booked != null && !booked.isBefore(visit.since)) return emptyList()

        val guess = book[drift.loopId]
        if (guess != null && guess.loop.packageName == visit.packageName) {
            book.update(guess.loop.id) { it.copy(loop = it.loop.copy(started = true)) }
            return listOf(Effect.Log("left ${visit.packageName} unfinished; ${guess.loop.id.value} is now a Finish"))
        }
        if (book[LoopId("flow:${visit.packageName}")] != null) return emptyList()
        val tracked = book.open(Situations.flow(visit.packageName, visit.appLabel, now))
        if (drift.phase != Phase.Idle || settings.paused || kindStat(LoopKind.Flow).muted) {
            return listOf(Effect.Log("opened ${tracked.loop.id.value} (Flow)"))
        }
        // You were just there, so no chip: the flow quietly becomes this session's guess. It only
        // counts as a thing on the recap once its pill shows.
        drift = DriftMachine.guess(drift, tracked.loop.id, tracked.loop.packageName)
        return listOf(Effect.Log("opened ${tracked.loop.id.value} (Flow) and made it the guess"))
    }

    private fun stepDrift(input: DriftInput, now: Instant): List<Effect> {
        val guess = book[drift.loopId]
        val rules = DriftRules(
            feedApps = settings.feedApps,
            driftAfter = settings.driftAfter,
            pillAllowed = guess != null && !guess.pillShown && !guess.ignored && !settings.paused,
            cooldown = settings.pillCooldown,
        )
        val step = DriftMachine.step(drift, input, now, rules)
        drift = step.drift
        val effects = mutableListOf<Effect>()
        step.reason?.let { effects += Effect.Log("drift ${guess?.loop?.id?.value}: $it") }
        if (step.showPill && guess != null) {
            val shown = book.update(guess.loop.id) { it.copy(pillShown = true, guessedAt = it.guessedAt ?: now) }!!
            effects += Effect.HideOverlay(Overlay.Chip)
            effects += Effect.ShowPill(copy.pill(guess.loop))
            if (guess.guessedAt == null) effects += Effect.RecordLoop(entry(shown, outcome = null, closedAt = null))
        }
        return effects
    }

    private fun onAlarm(next: Instant?, now: Instant): List<Effect> {
        nextAlarm = next
        val effects = mutableListOf<Effect>(Effect.Log("next alarm ${next?.let { copy.time(it) } ?: "none"}"))
        if (next == null || !next.isAfter(now)) return effects
        val local = next.atZone(settings.zone)
        if (Situations.isWeekdayMorning(local)) effects += Effect.RecordAlarm(local.toLocalTime(), local.toLocalDate())
        effects += closeWhere(now, "alarm set") { CloseRules.closedByAlarm(it, next) }
        return effects
    }

    private fun onCalendar(events: List<CalendarEvent>, now: Instant): List<Effect> {
        calendar = events
        return openSituations(now)
    }

    /** Routine and Leave-by loops depend on the clock, so they are re-checked at unlock and when the calendar changes. */
    private fun openSituations(now: Instant): List<Effect> {
        val loops = Situations.leaveBy(calendar, now, settings) +
            listOfNotNull(Situations.routine(now, settings, nextAlarm, stats.weeknightAlarms))
        return loops.filter { it.id !in closedSituations }.mapNotNull { loop ->
            if (book[loop.id] != null) {
                book.open(loop)
                null
            } else {
                book.open(loop)
                Effect.Log("opened ${loop.id.value} (${loop.kind}, due %.2f)".format(loop.due))
            }
        }
    }

    private fun onChipHeld(id: LoopId): List<Effect> {
        val tracked = book[id] ?: return emptyList()
        val stat = kindStat(tracked.loop.kind)
        return listOf(Effect.HideOverlay(Overlay.Chip), Effect.ShowDot, Effect.Log("chip held: ${id.value}")) +
            setKind(tracked.loop.kind, stat.copy(holds = stat.holds + 1, ignoreStreak = 0))
    }

    private fun onChipDismissed(id: LoopId): List<Effect> {
        val tracked = book.update(id) { it.copy(ignored = true, guessedAt = null) } ?: return emptyList()
        if (drift.loopId == id) drift = DriftMachine.end(drift)
        val stat = kindStat(tracked.loop.kind)
        val next = stat.copy(ignores = stat.ignores + 1, ignoreStreak = stat.ignoreStreak + 1)
        val effects = mutableListOf(
            Effect.HideOverlay(Overlay.Chip),
            Effect.HideOverlay(Overlay.Dot),
            // A rejected guess is not a thing you came for.
            Effect.RecordLoop(entry(tracked, outcome = null, closedAt = null)),
            Effect.Log("chip dismissed: ${id.value}" + if (next.muted) "; ${tracked.loop.kind} muted after ${next.ignoreStreak} ignores" else ""),
        )
        return effects + setKind(tracked.loop.kind, next)
    }

    private fun onChipActed(id: LoopId): List<Effect> {
        val tracked = book[id] ?: return emptyList()
        // Only the carry-over chip brings a loop back; a first chip tap is you doing what you came for.
        val carried = tracked.pillShown
        book.update(id) { it.copy(handedBack = it.handedBack || carried) }
        drift = drift.copy(phase = Phase.HandedBack)
        val stat = kindStat(tracked.loop.kind)
        // Acting on a chip says "yes, this one" as clearly as ✓ does.
        return handBack(tracked, "chip") + Effect.HideOverlay(Overlay.Chip) +
            setKind(tracked.loop.kind, stat.copy(holds = stat.holds + 1, ignoreStreak = 0))
    }

    private fun onPillTapped(id: LoopId): List<Effect> {
        val tracked = book.update(id) { it.copy(handedBack = true) } ?: return listOf(Effect.HideOverlay(Overlay.Pill))
        drift = drift.copy(phase = Phase.HandedBack)
        return handBack(tracked, "pill") + Effect.HideOverlay(Overlay.Pill)
    }

    private fun handBack(tracked: Tracked, from: String): List<Effect> {
        val loop = tracked.loop
        val effects = mutableListOf<Effect>(Effect.OpenWayBack(loop.id, loop.route), Effect.Log("hand-back from $from: ${loop.id.value} via ${routeName(loop.route)}"))
        if (loop.canReply && loop.notificationKey != null) effects += Effect.SuggestReplies(loop.id, loop.notificationKey)
        return effects
    }

    private fun onPillDismissed(id: LoopId): List<Effect> {
        book.update(id) { it.copy(carryOver = true) }
        drift = drift.copy(phase = Phase.Dismissed)
        return listOf(Effect.HideOverlay(Overlay.Pill), Effect.Log("pill dismissed: ${id.value}; carry over to next unlock"))
    }

    private fun onReplyChosen(event: Event.ReplyChosen): List<Effect> {
        val key = book[event.loopId]?.loop?.notificationKey ?: return listOf(Effect.HideOverlay(Overlay.ReplyStrip))
        return listOf(Effect.SendReply(event.loopId, key, event.text), Effect.Log("reply chosen for ${event.loopId.value}"))
    }

    private fun expire(now: Instant): List<Effect> =
        book.expired(now).flatMap { close(it.loop.id, Outcome.Expired, now, "expired") }

    private fun closeWhere(now: Instant, why: String, predicate: (Loop) -> Boolean): List<Effect> =
        book.all.filter { predicate(it.loop) }.map { it.loop.id }.flatMap { close(it, Outcome.Done, now, why) }

    /** [replied] is true only when you answered, so a tap on the notification doesn't count as a reply. */
    private fun close(id: LoopId, outcome: Outcome, now: Instant, why: String, replied: Boolean = false): List<Effect> {
        val tracked = book.remove(id) ?: return emptyList()
        if (tracked.loop.kind == LoopKind.Routine || tracked.loop.kind == LoopKind.LeaveBy) closedSituations += id
        val effects = mutableListOf<Effect>(Effect.Log("closed ${id.value}: $outcome, $why"))
        if (tracked.guessedAt != null) effects += Effect.RecordLoop(entry(tracked, outcome, now))
        val conversation = tracked.loop.conversation
        if (replied && conversation != null) effects += recordReply(conversation, Duration.between(tracked.loop.updatedAt, now))
        tracked.loop.notificationKey?.let { effects += Effect.Forget(it) }
        if (drift.loopId == id) {
            drift = DriftMachine.end(drift)
            effects += Overlay.entries.map { Effect.HideOverlay(it) }
        }
        return effects
    }

    /** Tracks a running median of reply latency with a frugal streaming estimate, so no history is stored. */
    private fun recordReply(conversation: String, latency: Duration): Effect {
        val old = stats.contacts[conversation] ?: ContactStat()
        val minutes = latency.toMillis() / 60_000.0
        val median = when {
            old.replies == 0 -> minutes
            minutes > old.medianReplyMinutes -> old.medianReplyMinutes + minOf(2.0, minutes - old.medianReplyMinutes)
            else -> old.medianReplyMinutes - minOf(2.0, old.medianReplyMinutes - minutes)
        }
        val stat = ContactStat(replies = old.replies + 1, medianReplyMinutes = median)
        stats = stats.copy(contacts = stats.contacts + (conversation to stat))
        return Effect.RecordContact(conversation, stat)
    }

    private fun kindStat(kind: LoopKind): KindStat = stats.kinds[kind] ?: KindStat()

    private fun setKind(kind: LoopKind, stat: KindStat): List<Effect> {
        stats = stats.copy(kinds = stats.kinds + (kind to stat))
        return listOf(Effect.RecordKind(kind, stat))
    }

    private fun entry(tracked: Tracked, outcome: Outcome?, closedAt: Instant?): LoopEntry = LoopEntry(
        loopId = tracked.loop.id,
        kind = tracked.loop.kind,
        label = tracked.loop.task,
        openedAt = tracked.loop.openedAt,
        guessedAt = tracked.guessedAt,
        closedAt = closedAt,
        outcome = outcome,
        broughtBack = outcome == Outcome.Done && tracked.handedBack,
    )

    /** Spelled out because R8 renames classes in release builds. */
    private fun routeName(route: Route): String = when (route) {
        is Route.Notification -> "the notification's tap action"
        is Route.LaunchApp -> "the app's launch intent"
        is Route.SetAlarm -> "set alarm"
        is Route.OpenUri -> "a link"
    }

    private fun hideAll(): List<Effect> = Overlay.entries.map { Effect.HideOverlay(it) }

    private companion object {
        val meetingApps = setOf("us.zoom.videomeetings", "com.google.android.apps.tachyon", "com.google.android.apps.meetings", "com.microsoft.teams", "com.cisco.webex.meetings")
    }
}
