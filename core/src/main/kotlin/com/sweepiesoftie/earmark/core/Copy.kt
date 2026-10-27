package com.sweepiesoftie.earmark.core

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every word a chip or pill says. It lives in the core, not in strings.xml, so the
 * scenario tests can hold it to the demo copy. v0.1 is English only, like Smart Reply.
 */
internal class Copy(private val settings: Settings) {
    private val clock: DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (settings.use24Hour) "H:mm" else "h:mm a", Locale.US)
    private val alarmClock: DateTimeFormatter = DateTimeFormatter.ofPattern(if (settings.use24Hour) "H:mm" else "h:mm", Locale.US)

    fun chip(loop: Loop, score: Double, now: Instant): ChipCard {
        val ago = ago(Duration.between(loop.updatedAt, now))
        val (title, subtitle) = when (loop.kind) {
            LoopKind.Reply -> "Here to reply to ${loop.who}?" to "${loop.detail} · $ago"
            LoopKind.Payment -> "Here to pay ${loop.who} back?" to "${loop.detail} · $ago"
            LoopKind.Routine -> "${loop.task}?" to "You usually do on weeknights"
            LoopKind.Flow -> "Here to finish in ${loop.who}?" to "You left before it went through · $ago"
            LoopKind.LeaveBy -> leaveByChip(loop)
        }
        return ChipCard(loop.id, loop.kind, title, subtitle, action = null, score = score)
    }

    /** The chip for a loop whose pill was swiped away: the task itself, with a verb instead of ✓. */
    fun carryOverChip(loop: Loop, score: Double): ChipCard = ChipCard(
        loopId = loop.id,
        kind = loop.kind,
        title = "${loop.task}?",
        subtitle = "Still on your list from ${time(loop.openedAt)}",
        action = verb(loop),
        score = score,
    )

    fun pill(loop: Loop): PillCard = PillCard(loop.id, loop.kind, loop.task, verb(loop))

    fun verb(loop: Loop): String = when (loop.kind) {
        LoopKind.Reply -> "Do it now"
        LoopKind.Payment -> "Pay now"
        LoopKind.Routine -> "Set it"
        LoopKind.Flow -> "Finish"
        LoopKind.LeaveBy -> when {
            loop.leaveBy?.isMeeting == true -> "Join"
            loop.started -> "Finish"
            loop.route is Route.LaunchApp -> "Book"
            else -> "Directions"
        }
    }

    fun time(instant: Instant): String = clock.format(instant.atZone(settings.zone))

    fun alarmTime(time: LocalTime): String = alarmClock.format(time)

    private fun leaveByChip(loop: Loop): Pair<String, String> {
        val detail = loop.leaveBy ?: return "${loop.task}?" to ""
        val title = when {
            detail.isMeeting -> "Here to join ${detail.title}?"
            loop.route is Route.LaunchApp -> "Here to book your ride?"
            else -> "Time to leave for ${detail.title}?"
        }
        val start = time(detail.startsAt)
        val subtitle = if (detail.isMeeting) {
            "Starts at $start"
        } else {
            listOfNotNull(detail.place?.substringBefore(" · ")?.substringBefore(","), start, "leave by ${time(detail.leaveAt)}").joinToString(" · ")
        }
        return title to subtitle
    }

    companion object {
        fun ago(elapsed: Duration): String {
            val minutes = elapsed.toMinutes()
            return when {
                minutes < 1 -> "just now"
                minutes < 60 -> "$minutes min ago"
                else -> "${elapsed.toHours()} h ago"
            }
        }
    }
}
