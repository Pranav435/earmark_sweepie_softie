package com.sweepiesoftie.earmark.core

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every word a chip, pill or waiting row says. It lives in the core, not in strings.xml, so the
 * scenario tests can hold it to the demo copy. v0.1 is English only, like Smart Reply.
 */
internal class Copy(private val settings: Settings) {
    private val clock: DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (settings.use24Hour) "H:mm" else "h:mm a", Locale.US)
    private val alarmClock: DateTimeFormatter = DateTimeFormatter.ofPattern(if (settings.use24Hour) "H:mm" else "h:mm", Locale.US)

    fun chip(loop: Loop, score: Double, now: Instant): ChipCard {
        // The clock and the calendar are their own evidence; anything that came in says how long ago.
        val clocked = loop.kind == LoopKind.Routine || loop.kind == LoopKind.LeaveBy || loop.trigger?.watch == Watch.NotOpenedBy
        val ago = ago(Duration.between(loop.updatedAt, now)).takeUnless { clocked }
        val note = note(loop).let { it.copy(words = listOfNotNull(it.words, ago).joinToString(" · ").ifEmpty { null }) }
        return ChipCard(loop.id, loop.kind, note, why = "Here to ${aim(loop)}?", task = loop.task, score = score)
    }

    /** The chip for a loop whose pill was swiped away, saying how long it has waited. */
    fun carryOverChip(loop: Loop, score: Double): ChipCard =
        ChipCard(loop.id, loop.kind, note(loop), why = "Still on your list from ${time(loop.openedAt)}", task = loop.task, score = score)

    /** The small line says why the task is back: you came for it, it came in while you were on the phone, or it was waiting after another. */
    fun pill(loop: Loop, origin: Origin): PillCard {
        val why = when (origin) {
            Origin.Unlock -> "You came here to ${aim(loop)}"
            Origin.Arrival -> "Before you forget"
            Origin.NextUp -> "Next up"
        }
        return PillCard(loop.id, loop.kind, note(loop), why, loop.task)
    }

    fun waiting(loop: Loop): Waiting = Waiting(loop.id, loop.kind, note(loop), loop.task, loop.updatedAt)

    /** Who it's from and their own words, the way a friend would pass it on (ADR 0032). */
    fun note(loop: Loop): Note {
        if (loop.trigger?.made == true) return triggered(loop)
        val person = loop.kind in people || (loop.kind == LoopKind.Payment && !loop.bill)
        val (who, words) = when (loop.kind) {
            LoopKind.Reply -> "${loop.who}${loop.groupName?.let { " in $it" }.orEmpty()}" to loop.detail
            LoopKind.Payment if loop.bill -> "${loop.who ?: "Your"} bill" to loop.detail
            LoopKind.Routine -> ((loop.route as? Route.SetAlarm)?.let { "${alarmTime(it.time)} alarm" } ?: loop.task) to "You usually set one on weeknights"
            LoopKind.Flow -> (loop.who ?: loop.task) to "You left before it went through"
            LoopKind.LeaveBy -> leaveBy(loop)
            // The reminder's own words are the note.
            LoopKind.Reminder -> loop.task.trimEnd('?', '.') to loop.detail
            LoopKind.Payment, LoopKind.CallBack, LoopKind.Email -> (loop.who ?: loop.task) to loop.detail
            LoopKind.Own -> (loop.who ?: loop.task) to "You earmarked it"
        }
        return Note(who, words, verb(loop), person, loop.packageName, faceKey = loop.notificationKey.takeIf { person })
    }

    /**
     * A custom trigger's loop: your label, or what it saw, from the person who wrote for a Reply or a Call back, and
     * otherwise from its app. A time trigger says when you wanted to open the app, which is when its loop opened (ADR 0038).
     */
    private fun triggered(loop: Loop): Note {
        val person = loop.kind.withPeople && loop.notificationKey != null
        val words = loop.detail ?: "You wanted to open it by ${time(loop.openedAt)}".takeIf { loop.trigger?.watch == Watch.NotOpenedBy }
        return Note(loop.who ?: loop.task, words, verb(loop), person, loop.packageName, faceKey = loop.notificationKey.takeIf { person })
    }

    /** What you came to do, as it follows "You came here to": "reply", "settle up". */
    private fun aim(loop: Loop): String = when (loop.kind) {
        LoopKind.Reply -> "reply"
        LoopKind.Email -> "reply to this email"
        LoopKind.CallBack -> "call back"
        LoopKind.Payment -> if (loop.bill) "pay this bill" else "settle up"
        LoopKind.Routine -> "set your alarm"
        LoopKind.Flow -> "finish"
        LoopKind.Reminder -> "get this done"
        LoopKind.Own -> "get back to it"
        LoopKind.LeaveBy -> when {
            loop.leaveBy?.isMeeting == true -> "join"
            loop.route is Route.LaunchApp -> "book your ride"
            else -> "leave"
        }
    }

    fun verb(loop: Loop): String = when (loop.kind) {
        LoopKind.Reply, LoopKind.Email -> "Reply"
        // A call back opens the call log rather than dialling, so a stray tap never rings anyone (ADR 0029).
        LoopKind.CallBack -> "Call back"
        LoopKind.Reminder, LoopKind.Own -> "Open"
        LoopKind.Payment -> "Pay"
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

    private fun leaveBy(loop: Loop): Pair<String, String?> {
        val detail = loop.leaveBy ?: return loop.task to null
        val start = time(detail.startsAt)
        val words = if (detail.isMeeting) {
            "Starts at $start"
        } else {
            listOfNotNull(detail.place?.substringBefore(" · ")?.substringBefore(","), start, "leave by ${time(detail.leaveAt)}").joinToString(" · ")
        }
        return detail.title to words
    }

    companion object {
        /** Kinds that are always someone you know; a Payment is too, unless it's a bill. */
        private val people = setOf(LoopKind.Reply, LoopKind.CallBack, LoopKind.Email)

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
