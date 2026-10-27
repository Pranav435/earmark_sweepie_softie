package com.sweepiesoftie.earmark.core

import java.time.Duration
import java.time.Instant
import kotlin.math.exp

internal data class Score(val recent: Double, val who: Double, val due: Double, val history: Double, val boost: Double) {
    val total: Double get() = 0.35 * recent + 0.25 * who + 0.25 * due + 0.15 * history + boost

    /** For DebugLog: the breakdown, never the loop's text. */
    override fun toString(): String =
        "%.2f (recent %.2f · who %.2f · due %.2f · history %.2f · boost %.2f)".format(total, recent, who, due, history, boost)
}

/** Scores open loops at unlock, and messages as they come in during use, with the starting weights from architecture.md. */
internal object Ranker {
    const val CHIP_THRESHOLD: Double = 0.70
    private const val UNLOCK_BOOST = 0.15
    private const val RECENT_HALF_LIFE_MINUTES = 30.0

    /**
     * [unseen] is true when this is your first chance to act on the loop's latest message or request: it came in
     * while the phone was put down and this is the first unlock since, however long after, or it is arriving now.
     */
    fun score(loop: Loop, now: Instant, stats: Stats, unseen: Boolean = true): Score {
        val ageMinutes = Duration.between(loop.updatedAt, now).toMillis().coerceAtLeast(0) / 60_000.0
        // Alarms and events are about now, not about when Earmark noticed them.
        val recent = when (loop.kind) {
            LoopKind.Routine, LoopKind.LeaveBy -> 1.0
            else -> exp(-ageMinutes / RECENT_HALF_LIFE_MINUTES)
        }
        // Your first chance to act on a message or request is the one most likely to be for it.
        val boosted = unseen && loop.kind in setOf(LoopKind.Reply, LoopKind.Payment)
        return Score(
            recent = recent,
            who = who(loop, stats),
            due = loop.due,
            history = history(stats.kinds[loop.kind]),
            boost = if (boosted) UNLOCK_BOOST else 0.0,
        )
    }

    /** Reply affinity: more replies raise it, slow replies (median over 30 min) take a quarter off. */
    private fun who(loop: Loop, stats: Stats): Double {
        val stat = loop.conversation?.let { stats.contacts[it] } ?: return 0.5
        if (stat.replies == 0) return 0.5
        val often = (0.4 + 0.06 * stat.replies).coerceAtMost(1.0)
        return if (stat.medianReplyMinutes <= 30.0) often else often * 0.75
    }

    /** Confirm rate with one smoothing hold and ignore, so a kind with no history scores 0.5. */
    private fun history(stat: KindStat?): Double {
        val holds = stat?.holds ?: 0
        val ignores = stat?.ignores ?: 0
        return (holds + 1.0) / (holds + ignores + 2.0)
    }
}
