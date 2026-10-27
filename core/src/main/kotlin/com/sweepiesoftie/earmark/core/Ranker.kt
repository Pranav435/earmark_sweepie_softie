package com.sweepiesoftie.earmark.core

import java.time.Duration
import java.time.Instant
import kotlin.math.exp

internal data class Score(val recent: Double, val who: Double, val due: Double, val history: Double, val boost: Double, val whoFrom: String = "new") {
    val total: Double get() = 0.35 * recent + 0.25 * who + 0.25 * due + 0.15 * history + boost

    /** For DebugLog: the breakdown, never the loop's text. */
    override fun toString(): String =
        "%.2f (recent %.2f · who %.2f from %s · due %.2f · history %.2f · boost %.2f)".format(total, recent, who, whoFrom, due, history, boost)
}

/** Scores open loops at unlock, and messages as they come in during use, with the starting weights from architecture.md. */
internal object Ranker {
    const val CHIP_THRESHOLD: Double = 0.70
    private const val UNLOCK_BOOST = 0.15
    private const val RECENT_HALF_LIFE_MINUTES = 30.0

    /** Something you owe that came in while the phone was down is still owed hours later (ADR 0031). */
    private const val OWED_HALF_LIFE_MINUTES = 360.0
    private val owedKinds = setOf(LoopKind.CallBack, LoopKind.Payment, LoopKind.Reminder)

    /**
     * [unseen] is true when this is your first chance to act on the loop's latest message or request: it came in
     * while the phone was put down and this is the first unlock since, however long after, or it is arriving now.
     */
    fun score(loop: Loop, now: Instant, stats: Stats, unseen: Boolean = true): Score {
        val ageMinutes = Duration.between(loop.updatedAt, now).toMillis().coerceAtLeast(0) / 60_000.0
        // Alarms and events are about now, not about when Earmark noticed them.
        val recent = when {
            loop.kind == LoopKind.Routine || loop.kind == LoopKind.LeaveBy -> 1.0
            unseen && owes(loop) -> exp(-ageMinutes / OWED_HALF_LIFE_MINUTES)
            else -> exp(-ageMinutes / RECENT_HALF_LIFE_MINUTES)
        }
        // Your first chance to act on a message, call, reminder or request is the one most likely to be for it.
        val boosted = unseen && loop.kind.cameIn
        val who = Affinity.who(loop, stats)
        return Score(
            recent = recent,
            who = who.value,
            due = loop.due,
            history = history(stats.kinds[loop.kind]),
            boost = if (boosted) UNLOCK_BOOST else 0.0,
            whoFrom = who.from,
        )
    }

    /** A missed call, a request for money, a bill, a reminder, or a message or email that asks or is urgent. Not chatter, and not a calendar event, which is over once its time has passed. */
    fun owes(loop: Loop): Boolean = (loop.kind in owedKinds && "event" !in loop.why) || loop.urgent || "asks" in loop.why

    /** At an unlock, an urgent loop that clears the bar comes before one that isn't urgent (ADR 0027), then the higher score. */
    val unlockOrder: Comparator<Pair<Loop, Score>> =
        compareBy<Pair<Loop, Score>> { (loop, score) -> loop.urgent && score.total > CHIP_THRESHOLD }.thenBy { it.second.total }

    /** Confirm rate with one smoothing hold and ignore, so a kind with no history scores 0.5. */
    private fun history(stat: KindStat?): Double {
        val holds = stat?.holds ?: 0
        val ignores = stat?.ignores ?: 0
        return (holds + 1.0) / (holds + ignores + 2.0)
    }
}
