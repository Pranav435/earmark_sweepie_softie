package com.sweepiesoftie.earmark.core

import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The drift timer when it is Smart (ADR 0023): staying in a feed longer than you usually do is
 * drift, and it comes back sooner when the loop is urgent or due soon.
 */
internal object SmartDrift {
    val shortest: Duration = Duration.ofSeconds(15)
    val longest: Duration = Duration.ofMinutes(2)

    /** A visit shorter than this is passing through on the way somewhere else. */
    val shortestVisit: Duration = Duration.ofSeconds(3)

    /** One very long session shouldn't drag the usual visit far, so visits count up to this. */
    private val longestVisit: Duration = Duration.ofMinutes(10)
    private const val START_SECONDS = 45.0
    private const val LEARN_AFTER_VISITS = 5
    private const val STEP_SECONDS = 10.0

    data class Timer(val after: Duration, val why: String)

    fun timer(loop: Loop, app: AppStat?): Timer {
        val learned = app != null && app.visits >= LEARN_AFTER_VISITS
        val usual = if (learned) app!!.medianVisitSeconds else START_SECONDS
        val why = mutableListOf("usual visit ${usual.roundToLong()} s" + if (learned) "" else ", still learning")
        var seconds = usual
        when {
            loop.due >= 1.0 -> { seconds *= 0.5; why += "urgent ×0.5" }
            loop.due >= 0.75 -> { seconds *= 0.75; why += "due soon ×0.75" }
        }
        val after = Duration.ofSeconds(seconds.roundToLong())
        return when {
            after < shortest -> Timer(shortest, (why + "at least ${shortest.seconds} s").joinToString(" · "))
            after > longest -> Timer(longest, (why + "at most 2 min").joinToString(" · "))
            else -> Timer(after, why.joinToString(" · "))
        }
    }

    /** A running median that moves at most 10 s a visit, like the reply latency estimate: no list of visits is kept. */
    fun learn(old: AppStat?, visit: Duration): AppStat {
        val seconds = visit.coerceAtMost(longestVisit).seconds.toDouble()
        // The app's other numbers, such as how you deal with its messages, carry over.
        val stat = old ?: AppStat()
        if (stat.visits == 0) return stat.copy(visits = 1, medianVisitSeconds = seconds)
        val step = minOf(STEP_SECONDS, abs(seconds - stat.medianVisitSeconds))
        val median = if (seconds > stat.medianVisitSeconds) stat.medianVisitSeconds + step else stat.medianVisitSeconds - step
        return stat.copy(visits = stat.visits + 1, medianVisitSeconds = median)
    }
}
