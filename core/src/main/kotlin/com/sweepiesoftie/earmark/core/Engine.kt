package com.sweepiesoftie.earmark.core

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

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

    /** Unlocked and in use: from an unlock, or anything from the foreground poll (it only runs while unlocked), until screen off. */
    private var inUse = false

    /** When the phone was last put down after use. A loop updated since is unseen, so the next unlock boosts it. */
    private var putDownAt: Instant? = null
    private val bookedAt = HashMap<String, Instant>()

    /** Routine and Leave-by loops are rebuilt from the clock and calendar, so remember which already closed. */
    private val closedSituations = HashSet<LoopId>()

    /** Group chatter showing now, by notification key, with its hashed conversation: a tap on it is learned (ADR 0028). */
    private val quiet = HashMap<String, String>()

    /** [interrupted] is set when a pill showed during the visit, so it isn't learned as a usual visit. */
    private data class Visit(val packageName: String, val appLabel: String, val since: Instant, val interrupted: Boolean = false)

    /** How many loops are open, for the home screen. Read it on the engine's thread, like [handle]. */
    public val openLoops: Int get() = book.all.size

    /**
     * The open loops on today's list, in the order an unlock would guess them, for the home screen's "who's
     * waiting" (ADR 0032). Read it on the engine's thread, like [handle].
     */
    public fun waiting(now: Instant): List<Waiting> = book.all
        .filter(::isListed)
        .map { it.loop to Ranker.score(it.loop, now, stats, unseen(it.loop)) }
        .sortedWith(Ranker.unlockOrder.reversed())
        .map { copy.waiting(it.first) }

    /** The message model's weights loaded, so it reads messages beside the word lists (ADR 0031). The first read loads them. */
    public val messageModel: Boolean get() = MessageModel.loaded

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
        is Event.ChipDismissed -> onChipDismissed(event.loopId)
        is Event.ChipFaded -> onChipFaded(event.loopId)
        is Event.ChipActed -> onChipActed(event.loopId)
        is Event.WaitingOpened -> onWaitingOpened(event.loopId)
        is Event.PillTapped -> onPillTapped(event.loopId)
        is Event.PillDismissed -> onPillDismissed(event.loopId)
        is Event.ReplyChosen -> onReplyChosen(event)
        is Event.ReplySent -> close(event.loopId, Outcome.Done, now, "reply sent", replied = true)
        is Event.KindUnmuted -> setKind(event.kind, KindStat(holds = kindStat(event.kind).holds, ignores = kindStat(event.kind).ignores))
        is Event.SettingsChanged -> {
            this.settings = event.settings
            copy = Copy(event.settings)
            val drift = when (event.settings.driftTimer) {
                DriftTimer.RightAway -> "right away"
                DriftTimer.Smart -> "smart"
                DriftTimer.Fixed -> "after ${event.settings.driftAfter.seconds}s"
            }
            listOf(Effect.Log("settings: drift $drift, demo ${event.settings.demoMode}, paused ${event.settings.paused}, ${event.settings.feedApps.size} feed apps, ${event.settings.myNames.size} names for mentions"))
        }
        is Event.StatsLoaded -> {
            // Counts the engine already holds are newer than any snapshot still being written to Room.
            stats = Stats(
                kinds = event.stats.kinds + stats.kinds,
                contacts = event.stats.contacts + stats.contacts,
                weeknightAlarms = event.stats.weeknightAlarms,
                apps = event.stats.apps + stats.apps,
            )
            emptyList()
        }
        Event.Tick -> {
            inUse = true
            expire(now) + closeCleared(now) + stepDrift(DriftInput.Tick, now)
        }
    }

    private fun onPosted(signal: Signal, now: Instant): List<Effect> =
        when (val result = Classifier.classify(signal, settings, stats)) {
            is Classification.Open if isLeaveByAlready(result.loop) -> skip(signal, "its event is already a leave-by")
            is Classification.Open -> {
                quiet -= signal.key
                val before = book[result.loop.id]
                book.open(result.loop)
                val why = result.loop.why.takeIf { it.isNotEmpty() }?.joinToString(prefix = ": ").orEmpty()
                val effects = mutableListOf<Effect>(
                    Effect.Log("${if (before == null) "opened" else "updated"} ${result.loop.id.value} (${result.loop.kind}, due %.2f$why) from ${signal.packageName}".format(result.loop.due)),
                )
                if (before != null && (before.chatOpen || before.clearedAt != null)) {
                    // Its notification is showing again, so the chat is no longer open (ADR 0019).
                    book.update(result.loop.id) { it.copy(chatOpen = false, clearedAt = null) }
                    // The cleared notification won't be removed again, so nothing else would forget its way back.
                    before.loop.notificationKey?.takeIf { it != result.loop.notificationKey }?.let { effects += Effect.Forget(it) }
                }
                if (before == null && result.loop.kind.cameIn) {
                    // On today's list as it arrives, so you can see Earmark has it (ADR 0025).
                    effects += Effect.RecordLoop(entry(book[result.loop.id]!!, outcome = null, closedAt = null))
                }
                if (before != null && before.pillShown && result.loop.updatedAt.isAfter(before.loop.updatedAt)) {
                    // One pill per message, not per conversation: someone writing again is a new chance (ADR 0021).
                    book.update(result.loop.id) { it.copy(pillShown = false) }
                }
                effects + onArrival(result.loop, before, now)
            }
            is Classification.Replied ->
                book.byConversation(result.conversation)?.let { close(it.loop.id, Outcome.Done, now, "you replied in ${signal.packageName}", replied = true) }
                    ?: listOf(Effect.Log("skip ${signal.packageName}: last message is yours"), Effect.Forget(signal.key))
            is Classification.Booked -> {
                bookedAt[result.packageName] = now
                closeWhere(now, "booked in ${result.packageName}") { CloseRules.closedByBooking(it.loop, result.packageName) }
            }
            is Classification.Paid -> closeWhere(now, "paid in ${result.packageName}") { CloseRules.closedByPayment(it.loop, result.packageName, result.amount) }
            is Classification.Quiet -> {
                if (book.byNotificationKey(signal.key) == null) quiet[signal.key] = result.conversation
                skip(signal, "group chatter, not for you")
            }
            is Classification.Skip -> skip(signal, result.reason)
        }

    /** A calendar's own notification for an event Earmark already holds as a Leave-by, which says more: where, and when to leave. */
    private fun isLeaveByAlready(loop: Loop): Boolean =
        loop.kind == LoopKind.Reminder && book.all.any { it.loop.leaveBy?.title.equals(loop.task, ignoreCase = true) }

    /** Its text and intents go at once, unless an open loop still answers to this notification and the app only reposted it. */
    private fun skip(signal: Signal, reason: String): List<Effect> =
        if (book.byNotificationKey(signal.key) != null) {
            listOf(Effect.Log("skip ${signal.packageName}: $reason; its loop stays open"))
        } else {
            listOf(Effect.Log("skip ${signal.packageName}: $reason"), Effect.Forget(signal.key))
        }

    /**
     * A message or request that comes in while you use the phone has had its heads-up, so it gets no chip (ADR 0020).
     * It quietly becomes the guess, so drift can still bring it back, unless the guess is something you came for.
     * Against another arrival or a next up it has to score higher, whichever app each came from (ADR 0027). An
     * urgent one takes over even a guess you came for, which comes back once the urgent one is dealt with.
     */
    private fun onArrival(loop: Loop, before: Tracked?, now: Instant): List<Effect> {
        if (!inUse || settings.paused) return emptyList()
        // Only a new message counts: not an app posting the same one again, or one delivered late.
        if (before != null && !loop.updatedAt.isAfter(before.loop.updatedAt)) return emptyList()
        if (Duration.between(loop.updatedAt, now) > arrivalWindow) return emptyList()
        if (drift.loopId == loop.id && drift.phase != Phase.Left) return emptyList()

        val id = loop.id.value
        val watching = drift.phase in setOf(Phase.Guessed, Phase.InFeed, Phase.Drifting)
        val current = book[drift.loopId]?.takeIf { watching }
        val urgentOver = loop.urgent && current != null && drift.origin == Origin.Unlock && !current.loop.urgent
        val replaceable = drift.phase == Phase.Idle || drift.phase == Phase.Left || (watching && drift.origin != Origin.Unlock) || urgentOver
        if (!replaceable) return listOf(Effect.Log("$id came in during use; ${drift.loopId?.value} stays the guess (${drift.phase})"))
        if (book[loop.id]?.ignored == true) return listOf(Effect.Log("$id came in during use; its chip was swiped this session"))
        if (kindStat(loop.kind).muted) return listOf(Effect.Log("$id came in during use; ${loop.kind} is muted"))
        val score = Ranker.score(loop, now, stats, unseen = true)
        if (score.total <= Ranker.CHIP_THRESHOLD) {
            return listOf(Effect.Log("$id came in during use; scored $score, needs > ${Ranker.CHIP_THRESHOLD}"))
        }
        if (current != null && !urgentOver) {
            val held = Ranker.score(current.loop, now, stats, unseen = true)
            if (held.total >= score.total) {
                return listOf(Effect.Log("$id came in during use and scored $score; ${current.loop.id.value} stays the guess at %.2f".format(held.total)))
            }
        }
        val previous = drift.takeIf { urgentOver }
        drift = DriftMachine.guess(drift, loop.id, home(loop), Origin.Arrival).copy(resume = previous)
        val ahead = previous?.let { " (urgent, ahead of ${it.loopId?.value}, which comes back after)" }.orEmpty()
        return listOf(Effect.Log("$id came in during use and is now the guess, score $score$ahead")) + resumeForeground(now)
    }

    /**
     * The loop's own app, where being is not drift. A feed app never is: an Instagram DM still waits while you
     * scroll Reels, until you open its chat and Instagram clears the notification.
     */
    private fun home(loop: Loop): String? = loop.packageName?.takeUnless { it in settings.feedApps }

    private fun onRemoved(event: Event.NotificationRemoved, now: Instant): List<Effect> {
        val tracked = book.byNotificationKey(event.key)
            ?: return quiet.remove(event.key)?.let { learnOpen(it, event.reason) }.orEmpty() + Effect.Forget(event.key)
        val loop = tracked.loop
        val learned = learnOpen(loop.conversation, event.reason)
        if (opensChat(tracked, event.reason)) return learned + openChat(loop, event.reason, now)
        if (event.reason == RemovalReason.AppCancel && isGuessedReply(tracked)) {
            // Chat apps clear it the moment they open, which can be before the foreground poll sees them.
            book.update(loop.id) { it.copy(clearedAt = now) }
            return listOf(Effect.Log("${loop.id.value}: ${loop.packageName} cleared its notification; closes unless it comes to the front"))
        }
        return learned + close(loop.id, CloseRules.onRemoved(event.reason), now, "notification removed (${event.reason})")
    }

    /** A reply loop with its chat not open yet: its notification going may mean you are opening the chat. */
    private fun canOpenChat(tracked: Tracked): Boolean =
        tracked.loop.kind == LoopKind.Reply && tracked.loop.canReply && !tracked.chatOpen

    private fun isGuessedReply(tracked: Tracked): Boolean = canOpenChat(tracked) && drift.loopId == tracked.loop.id

    /** The notification went because you opened its chat: through Earmark, from the notification, or in its app. */
    private fun opensChat(tracked: Tracked, reason: RemovalReason): Boolean = when (reason) {
        // A tap names the chat, so any reply loop opens; a clear could be the app opening anywhere, so only the guess does.
        RemovalReason.Click -> canOpenChat(tracked)
        RemovalReason.AppCancel ->
            isGuessedReply(tracked) && (drift.phase == Phase.HandedBack || foreground?.packageName == tracked.loop.packageName)
        RemovalReason.UserDismiss, RemovalReason.Other -> false
    }

    /**
     * Chat apps clear the notification as the chat opens. Keep the loop, its reply action and its
     * message text, so the reply strip can show and send; it closes when you reply or leave.
     * A chat you open from its notification becomes the guess, whatever the guess was.
     */
    private fun openChat(loop: Loop, reason: RemovalReason, now: Instant): List<Effect> {
        val effects = mutableListOf<Effect>()
        val handedBack = drift.loopId == loop.id && drift.phase == Phase.HandedBack
        if (drift.loopId != loop.id) {
            // Moving on from an open chat means it was read, and its strip would send to the wrong person.
            drift.loopId?.takeIf { book[it]?.chatOpen == true }?.let { effects += close(it, Outcome.Done, now, "moved to another chat", next = false) }
            // A detour: once this chat closes, the guess it took over from comes back.
            val previous = drift.takeIf { it.loopId != null }
            drift = DriftMachine.guess(drift, loop.id, home(loop)).copy(resume = previous)
        }
        book.update(loop.id) { it.copy(chatOpen = true, clearedAt = null) }
        drift = drift.copy(phase = Phase.HandedBack)
        effects += Effect.Log("${loop.id.value}: chat opened and ${loop.packageName} cleared its notification ($reason); closes when you reply or leave")
        if (!handedBack) {
            // You opened it yourself, so a chip or pill may still be showing over the chat.
            effects += Effect.HideOverlay(Overlay.Chip)
            effects += Effect.HideOverlay(Overlay.Pill)
            if (loop.notificationKey != null) effects += suggestReplies(loop, loop.notificationKey)
        }
        return effects
    }

    private fun onUnlocked(now: Instant): List<Effect> {
        inUse = true
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
            val score = Ranker.score(carry.loop, now, stats, unseen(carry.loop))
            drift = DriftMachine.guess(drift, carry.loop.id, home(carry.loop))
            effects += Effect.Log("unlock: carry-over chip for ${carry.loop.id.value}, score $score")
            effects += Effect.ShowChip(copy.carryOverChip(carry.loop, score.total))
            return effects + resumeForeground(now)
        }

        val candidates = book.all.filter { !kindStat(it.loop.kind).muted }
        val muted = book.all.size - candidates.size
        val best = candidates.map { it to Ranker.score(it.loop, now, stats, unseen(it.loop)) }
            .maxWithOrNull { a, b -> Ranker.unlockOrder.compare(a.first.loop to a.second, b.first.loop to b.second) }
        if (best == null) {
            return effects + Effect.Log("unlock: no chip, no open loops" + if (muted > 0) " ($muted muted)" else "")
        }
        val (tracked, score) = best
        if (score.total <= Ranker.CHIP_THRESHOLD) {
            return effects + Effect.Log("unlock: no chip, best ${tracked.loop.id.value} scored $score, needs > ${Ranker.CHIP_THRESHOLD}")
        }
        // The latest guess time, so a loop guessed again today shows on today's recap.
        val guessed = book.update(tracked.loop.id) { it.copy(guessedAt = now) }!!
        drift = DriftMachine.guess(drift, tracked.loop.id, home(tracked.loop))
        effects += Effect.Log("unlock: chip for ${tracked.loop.id.value}, score $score of ${candidates.size} open")
        effects += Effect.ShowChip(copy.chip(tracked.loop, score.total, now))
        effects += Effect.RecordLoop(entry(guessed, outcome = null, closedAt = null))
        return effects + resumeForeground(now)
    }

    /**
     * Updated since the phone was last put down, so this unlock is your first chance to see it. Before Earmark
     * has seen the phone put down, everything counts: it doesn't know what you saw.
     */
    private fun unseen(loop: Loop): Boolean = putDownAt?.let { loop.updatedAt.isAfter(it) } ?: true

    /** The app in front may have been reported before the unlock: replay it so a feed left open still counts. */
    private fun resumeForeground(now: Instant): List<Effect> =
        foreground?.let { stepDrift(DriftInput.Foreground(it.packageName), now) }.orEmpty()

    private fun onScreenOff(now: Instant): List<Effect> {
        // A screen that only lit up for a notification on the lock screen wasn't in use.
        if (inUse) putDownAt = now
        // Before closing anything, so nothing is lined up next for a phone that was just put down.
        inUse = false
        val left = closeWhere(now, "screen off after the chat app cleared it") { it.chatOpen || it.clearedAt != null }
        val learned = foreground?.let { learnVisit(it, now) }.orEmpty()
        drift = DriftMachine.end(drift)
        foreground = null
        return left + learned + hideAll()
    }

    private fun onForeground(event: Event.ForegroundChanged, now: Instant): List<Effect> {
        inUse = true
        val effects = mutableListOf<Effect>()
        val previous = foreground
        foreground = Visit(event.packageName, event.appLabel, now)
        if (previous != null && previous.packageName != event.packageName) effects += leftApp(previous, now) + learnVisit(previous, now)

        val guess = book[drift.loopId]
        if (guess != null && drift.phase == Phase.HandedBack && event.packageName != guess.loop.packageName &&
            event.packageName != settings.ownPackage
        ) {
            if (guess.chatOpen) {
                effects += close(guess.loop.id, Outcome.Done, now, "left the chat")
            } else {
                // The hand-back is over: a later clear means the chat was read somewhere else, unless its app is in front then.
                drift = drift.copy(phase = Phase.Left)
                effects += Effect.HideOverlay(Overlay.ReplyStrip)
            }
        }
        if (guess?.clearedAt != null && event.packageName == guess.loop.packageName) effects += openChat(guess.loop, RemovalReason.AppCancel, now)
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
        drift = DriftMachine.guess(drift, tracked.loop.id, home(tracked.loop))
        return listOf(Effect.Log("opened ${tracked.loop.id.value} (Flow) and made it the guess"))
    }

    /**
     * Your usual visit to each feed app sets the smart drift timer. Demo visits and paused time aren't you,
     * and a visit a pill cut short would teach the timer to be shorter still.
     */
    private fun learnVisit(visit: Visit, now: Instant): List<Effect> {
        if (visit.packageName !in settings.feedApps || visit.interrupted || settings.demoMode || settings.paused) return emptyList()
        val length = Duration.between(visit.since, now)
        if (length < SmartDrift.shortestVisit) return emptyList()
        val stat = SmartDrift.learn(stats.apps[visit.packageName], length)
        stats = stats.copy(apps = stats.apps + (visit.packageName to stat))
        return listOf(
            Effect.RecordApp(visit.packageName, stat),
            Effect.Log("usual visit to ${visit.packageName}: %d s after %d visits (this one %d s)".format(stat.medianVisitSeconds.roundToLong(), stat.visits, length.seconds)),
        )
    }

    private fun driftTimer(guess: Tracked?): SmartDrift.Timer = when {
        // Right away still lets you pass through a feed app on the way somewhere else.
        settings.driftTimer == DriftTimer.RightAway -> SmartDrift.Timer(SmartDrift.shortestVisit, "right away")
        settings.driftTimer == DriftTimer.Smart && guess != null ->
            SmartDrift.timer(guess.loop, foreground?.let { stats.apps[it.packageName] })
        else -> SmartDrift.Timer(settings.driftAfter, "fixed timer")
    }

    private fun stepDrift(input: DriftInput, now: Instant): List<Effect> {
        val guess = book[drift.loopId]
        val timer = driftTimer(guess)
        val before = drift.phase
        val rules = DriftRules(
            feedApps = settings.feedApps,
            driftAfter = timer.after,
            pillHeld = when {
                guess == null -> "no guess"
                settings.paused -> "Earmark is paused"
                guess.pillShown -> "its pill already showed for this message"
                guess.ignored -> "its chip was swiped"
                guess.clearedAt != null -> "its app cleared the notification"
                else -> null
            },
        )
        val step = DriftMachine.step(drift, input, now, rules)
        drift = step.drift
        val effects = mutableListOf<Effect>()
        if (guess != null && before != Phase.InFeed && drift.phase == Phase.InFeed) {
            effects += Effect.Log("drift ${guess.loop.id.value}: in ${foreground?.packageName}, pill after ${timer.after.seconds} s (${timer.why})")
        }
        step.reason?.let { effects += Effect.Log("drift ${guess?.loop?.id?.value}: $it") }
        if (step.showPill && guess != null) {
            foreground = foreground?.copy(interrupted = true)
            val shown = book.update(guess.loop.id) { it.copy(pillShown = true, guessedAt = it.guessedAt ?: now) }!!
            effects += Effect.HideOverlay(Overlay.Chip)
            // The pill says what the dot was holding, and the dot would peek out from under its corner.
            effects += Effect.HideOverlay(Overlay.Dot)
            effects += Effect.ShowPill(copy.pill(guess.loop, drift.origin))
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
        effects += closeWhere(now, "alarm set") { CloseRules.closedByAlarm(it.loop, next) }
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

    /** A faded chip only leaves the screen. While its loop is still the guess, the dot says Earmark is holding it (ADR 0007, 0032). */
    private fun onChipFaded(id: LoopId): List<Effect> {
        if (drift.loopId != id || book[id] == null) return listOf(Effect.Log("chip faded: ${id.value}, no longer the guess"))
        val holding = drift.phase != Phase.HandedBack
        return listOfNotNull(Effect.HideOverlay(Overlay.Chip), Effect.ShowDot.takeIf { holding }, Effect.Log("chip faded: ${id.value}" + if (holding) "; dot shows" else ""))
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
        // The chip's one button is how you say "yes, this one" (ADR 0032).
        return handBack(tracked, "chip") + Effect.HideOverlay(Overlay.Chip) +
            setKind(tracked.loop.kind, stat.copy(holds = stat.holds + 1, ignoreStreak = 0))
    }

    /**
     * A waiting row tapped on the home screen: you picked it, so it becomes the guess, handed back now. A detour, like
     * a chat opened from its notification: the guess it took over from comes back once it closes. It isn't a guess
     * you confirmed, so nothing is learned about its kind.
     */
    private fun onWaitingOpened(id: LoopId): List<Effect> {
        val tracked = book[id] ?: return listOf(Effect.Log("waiting row ${id.value}: already closed"))
        if (drift.loopId != id) {
            val previous = drift.takeIf { it.loopId != null }
            drift = DriftMachine.guess(drift, id, home(tracked.loop)).copy(resume = previous)
        }
        drift = drift.copy(phase = Phase.HandedBack)
        return handBack(tracked, "home screen") + listOf(Overlay.Chip, Overlay.Pill, Overlay.Dot).map { Effect.HideOverlay(it) }
    }

    private fun onPillTapped(id: LoopId): List<Effect> {
        val tracked = book.update(id) { it.copy(handedBack = true) } ?: return listOf(Effect.HideOverlay(Overlay.Pill))
        drift = drift.copy(phase = Phase.HandedBack)
        return handBack(tracked, "pill") + Effect.HideOverlay(Overlay.Pill)
    }

    private fun handBack(tracked: Tracked, from: String): List<Effect> {
        val loop = tracked.loop
        val effects = mutableListOf<Effect>(Effect.OpenWayBack(loop.id, loop.route), Effect.Log("hand-back from $from: ${loop.id.value} via ${routeName(loop.route)}"))
        if (loop.canReply && loop.notificationKey != null) {
            effects += suggestReplies(loop, loop.notificationKey)
        } else if (loop.kind == LoopKind.Reply) {
            effects += Effect.Log("${loop.id.value}: its notification has no reply action, so no reply strip")
        }
        return effects
    }

    /** A reply in a group goes to the group; a Reply loop always has someone to reply to, so the task is only a fallback. */
    private fun suggestReplies(loop: Loop, key: String): Effect = Effect.SuggestReplies(loop.id, key, to = loop.groupName ?: loop.who ?: loop.task)

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

    /** Its app cleared it and didn't come to the front, so the chat was read somewhere else. */
    private fun closeCleared(now: Instant): List<Effect> =
        closeWhere(now, "notification removed (${RemovalReason.AppCancel})") { it.clearedAt != null && Duration.between(it.clearedAt, now) >= clearWait }

    private fun closeWhere(now: Instant, why: String, predicate: (Tracked) -> Boolean): List<Effect> =
        book.all.filter(predicate).map { it.loop.id }.flatMap { close(it, Outcome.Done, now, why) }

    /**
     * [replied] is true only when you answered, so a tap on the notification doesn't count as a reply. When the
     * guess closes with nothing to go back to, the next loop waiting takes its place, unless [next] is false.
     */
    private fun close(id: LoopId, outcome: Outcome, now: Instant, why: String, replied: Boolean = false, next: Boolean = true): List<Effect> {
        val tracked = book.remove(id) ?: return emptyList()
        if (tracked.loop.kind == LoopKind.Routine || tracked.loop.kind == LoopKind.LeaveBy) closedSituations += id
        val effects = mutableListOf<Effect>(Effect.Log("closed ${id.value}: $outcome, $why"))
        if (isListed(tracked)) effects += Effect.RecordLoop(entry(tracked, outcome, now))
        val conversation = tracked.loop.conversation
        if (replied && conversation != null) effects += recordReply(conversation, Duration.between(tracked.loop.updatedAt, now))
        if (tracked.loop.kind in fromPeople) effects += learnApp(tracked.loop, outcome, now)
        tracked.loop.notificationKey?.let { effects += Effect.Forget(it) }
        if (drift.loopId == id) {
            val back = drift.resume?.takeIf { book[it.loopId] != null }
            drift = if (back != null) DriftMachine.resume(drift, back) else DriftMachine.end(drift)
            // The dot stays while it keeps the guess you go back to.
            effects += Overlay.entries.filter { back == null || it != Overlay.Dot }.map { Effect.HideOverlay(it) }
            back?.let { effects += Effect.Log("back to ${it.loopId?.value} as the guess") }
            if (back == null && next && outcome != Outcome.Expired) effects += nextUp(now)
        }
        return effects
    }

    /**
     * Once you've dealt with the guess, the strongest loop still waiting quietly takes its place, from any app,
     * so drift can bring that one back too (ADR 0027). It needs the score a chip needs, and gets no chip.
     */
    private fun nextUp(now: Instant): List<Effect> {
        if (!inUse || settings.paused) return emptyList()
        val (tracked, score) = book.all
            .filter { !kindStat(it.loop.kind).muted && !it.ignored && !it.pillShown && !it.chatOpen && it.clearedAt == null }
            .map { it to Ranker.score(it.loop, now, stats, unseen(it.loop)) }
            .maxByOrNull { it.second.total } ?: return emptyList()
        if (score.total <= Ranker.CHIP_THRESHOLD) {
            return listOf(Effect.Log("next up: nothing, best ${tracked.loop.id.value} scored $score, needs > ${Ranker.CHIP_THRESHOLD}"))
        }
        drift = DriftMachine.guess(drift, tracked.loop.id, home(tracked.loop), Origin.NextUp)
        return listOf(Effect.Log("next up: ${tracked.loop.id.value}, score $score")) + resumeForeground(now)
    }

    /** Tracks a running median of reply latency with a frugal streaming estimate, so no history is stored. */
    private fun recordReply(conversation: String, latency: Duration): Effect {
        val old = stats.contacts[conversation] ?: ContactStat()
        val median = Affinity.median(old.medianReplyMinutes, old.replies, latency.toMillis() / 60_000.0)
        val stat = old.copy(replies = old.replies + 1, medianReplyMinutes = median)
        stats = stats.copy(contacts = stats.contacts + (conversation to stat))
        return Effect.RecordContact(conversation, stat)
    }

    /** A tap or a swipe on a conversation's notification, kept as two counts under its hashed key (ADR 0028). Demo chats aren't you. */
    private fun learnOpen(conversation: String?, reason: RemovalReason): List<Effect> {
        if (conversation == null || settings.demoMode) return emptyList()
        val stat = Affinity.learnOpen(stats.contacts[conversation], reason) ?: return emptyList()
        stats = stats.copy(contacts = stats.contacts + (conversation to stat))
        return listOf(Effect.RecordContact(conversation, stat))
    }

    /** How you deal with messages in each app, so someone new there starts from it (ADR 0027). */
    private fun learnApp(loop: Loop, outcome: Outcome, now: Instant): List<Effect> {
        val app = loop.packageName?.takeIf { it != settings.ownPackage && !settings.demoMode } ?: return emptyList()
        val stat = Affinity.learnApp(stats.apps[app], outcome, Duration.between(loop.updatedAt, now))
        stats = stats.copy(apps = stats.apps + (app to stat))
        return listOf(
            Effect.RecordApp(app, stat),
            Effect.Log("messages in $app: ${stat.done} of ${stat.done + stat.left} dealt with, usually after %d min".format(stat.medianDoneMinutes.roundToLong())),
        )
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
        listed = isListed(tracked),
    )

    private fun isListed(tracked: Tracked): Boolean = tracked.guessedAt != null || tracked.loop.kind.cameIn

    /** Spelled out because R8 renames classes in release builds. */
    private fun routeName(route: Route): String = when (route) {
        is Route.Notification -> "the notification's tap action"
        is Route.LaunchApp -> "the app's launch intent"
        is Route.SetAlarm -> "set alarm"
        is Route.OpenUri -> "a link"
    }

    private fun hideAll(): List<Effect> = Overlay.entries.map { Effect.HideOverlay(it) }

    private companion object {
        /** Loops with a person at the other end: how you deal with them teaches Earmark about their app (ADR 0027). */
        val fromPeople = setOf(LoopKind.Reply, LoopKind.CallBack, LoopKind.Email)

        /** A few foreground polls, which run once a second. */
        val clearWait: Duration = Duration.ofSeconds(3)

        /** A message older than this when it's posted was delivered late, or replayed when the listener reconnected. */
        val arrivalWindow: Duration = Duration.ofMinutes(2)
        val meetingApps = setOf("us.zoom.videomeetings", "com.google.android.apps.tachyon", "com.google.android.apps.meetings", "com.microsoft.teams", "com.cisco.webex.meetings")
    }
}
