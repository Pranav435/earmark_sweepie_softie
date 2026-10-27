package com.sweepiesoftie.earmark.core

import java.time.Duration
import kotlin.math.abs
import kotlin.math.exp

/**
 * How much you answer someone, learned on the phone as counts and running medians, never a list of messages
 * (ADR 0027). Your replies to the person count most, then how often you open their notifications, then how you
 * treat messages in their app, so a new person on WhatsApp and a new person on Instagram can still differ.
 */
internal object Affinity {
    /** [from] names the evidence for DebugLog: "replies", "opens", "app" or "new". */
    data class Who(val value: Double, val from: String)

    /** Opens and swipes, or closed messages in an app, before either counts. */
    private const val LEARN_OPENS = 3
    private const val LEARN_APP = 5

    /** A group you haven't answered is owed a little less than a person. */
    private const val GROUP = 0.8

    fun who(loop: Loop, stats: Stats): Who {
        val stat = loop.conversation?.let { stats.contacts[it] }
        if (stat != null && stat.replies > 0) {
            // More replies raise it, slow replies (median over 30 min) take a quarter off.
            val often = (0.4 + 0.06 * stat.replies).coerceAtMost(1.0)
            return Who(if (stat.medianReplyMinutes <= 30.0) often else often * 0.75, "replies")
        }
        if (stat != null && stat.opened + stat.swiped >= LEARN_OPENS) return Who(0.3 + 0.4 * stat.openRate, "opens")
        val app = loop.packageName?.let { stats.apps[it] }?.takeIf { it.done + it.left >= LEARN_APP }
        val group = if (loop.inGroup) GROUP else 1.0
        if (app == null) return Who(0.5 * group, "new")
        // How often you deal with messages there, and how soon: 0.3 for an app you ignore, up to 0.7.
        val rate = (app.done + 1.0) / (app.done + app.left + 2.0)
        val soon = exp(-app.medianDoneMinutes / 60.0)
        return Who((0.3 + 0.2 * rate + 0.2 * soon) * group, "app")
    }

    /** A tap on a conversation's notification says you open it; swiping it away says you don't. Other removals say nothing. */
    fun learnOpen(old: ContactStat?, reason: RemovalReason): ContactStat? {
        val stat = old ?: ContactStat()
        return when (reason) {
            RemovalReason.Click -> stat.copy(opened = stat.opened + 1)
            RemovalReason.UserDismiss -> stat.copy(swiped = stat.swiped + 1)
            RemovalReason.AppCancel, RemovalReason.Other -> null
        }
    }

    /** A message in this app closed: done counts how soon, swiped away or expired counts as left. */
    fun learnApp(old: AppStat?, outcome: Outcome, latency: Duration): AppStat {
        val stat = old ?: AppStat()
        if (outcome != Outcome.Done) return stat.copy(left = stat.left + 1)
        val minutes = latency.toMillis().coerceAtLeast(0) / 60_000.0
        return stat.copy(done = stat.done + 1, medianDoneMinutes = median(stat.medianDoneMinutes, stat.done, minutes))
    }

    /** A frugal streaming median that moves at most 2 minutes a sample, so no history is stored. */
    fun median(old: Double, count: Int, sample: Double): Double {
        if (count == 0) return sample
        val step = minOf(2.0, abs(sample - old))
        return if (sample > old) old + step else old - step
    }
}
