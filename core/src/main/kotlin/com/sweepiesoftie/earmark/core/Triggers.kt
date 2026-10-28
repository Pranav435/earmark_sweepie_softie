package com.sweepiesoftie.earmark.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What a custom trigger watches in its app (ADR 0038). */
public enum class Watch {
    /** Its notifications that say one of the trigger's words. */
    NotificationWords,

    /** Every notification it posts; for an Ignore, everything from the app. */
    AnyNotification,

    /** Its screen saying one of the words. Needs screen reading, which is off until you turn it on. */
    ScreenWords,

    /** It not opened for 20 s or more by a time of day. */
    NotOpenedBy,
}

/** When a screen trigger fires: as you leave the app with its words still showing, or as soon as they show. */
public enum class ScreenTiming { OnLeaving, AsSoonAsShown }

/**
 * A custom trigger from Advanced (ADR 0038): watch one app for something, then earmark it as [kind] or, with [ignore],
 * leave it alone, which beats every earmark, Earmark's own included. [words] match whole, any one of them, in any case.
 * [label] is what the note says instead of what Earmark saw.
 */
public data class Trigger(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val watch: Watch,
    val words: List<String> = emptyList(),
    val timing: ScreenTiming = ScreenTiming.OnLeaving,
    /** For [Watch.NotOpenedBy]: the time of day, on [days]. */
    val by: LocalTime? = null,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val kind: LoopKind = LoopKind.Reminder,
    val ignore: Boolean = false,
    val label: String? = null,
    val on: Boolean = true,
) {
    public companion object {
        /** The kinds a trigger can earmark as, in the order the form offers them. */
        public val kinds: List<LoopKind> = listOf(LoopKind.Reminder, LoopKind.Payment, LoopKind.Reply, LoopKind.CallBack, LoopKind.Flow, LoopKind.Own)

        /** "KYC, due" as typed in the form: commas or line breaks between words or phrases. */
        public fun words(typed: String): List<String> = typed.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
}

/** The custom trigger behind a loop: it [made] the loop, or marked Earmark's own loop as owed (ADR 0038). */
public data class TriggerRef(val id: String, val watch: Watch, val made: Boolean = true)

/**
 * When a trigger last matched, for its row in Advanced, and how many "not this" in a row on loops it made; three mute
 * it, as they mute a chat (ADR 0033, 0038).
 */
public data class TriggerStat(val lastMatched: Instant? = null, val ignoreStreak: Int = 0) {
    val muted: Boolean get() = ignoreStreak >= KindStat.MUTE_AFTER_IGNORES
}

/** A trigger and the word it found. Null for a trigger that matches anything. */
internal data class Hit(val trigger: Trigger, val word: String?)

/** Matching triggers, and the loops they make (ADR 0038). */
internal object Triggers {
    /** How long in an app counts as opening it, for a time trigger: the same as going back to an Own loop (ADR 0035). */
    val visit: Duration = Situations.ownDwell

    /** The word or phrase of [words] that [text] says, whole and in any case, or null. A phrase's spaces match any gap. */
    fun match(words: List<String>, text: String): String? {
        val wanted = words.filter { it.isNotBlank() }
        if (wanted.isEmpty()) return null
        val alternatives = wanted.joinToString("|") { word -> "(" + word.trim().split(gaps).joinToString("""\s+""") { Regex.escape(it) } + ")" }
        val found = Regex("""(?<![\p{L}\p{M}\p{N}])(?:$alternatives)(?![\p{L}\p{M}\p{N}])""", RegexOption.IGNORE_CASE).find(text) ?: return null
        return wanted[found.groups.drop(1).indexOfFirst { it != null }]
    }

    /**
     * The notification triggers in force that [signal] matches. Ongoing notifications, group summaries, Earmark's own
     * and one-time codes are never something to watch for.
     */
    fun hits(signal: Signal, triggers: List<Trigger>): List<Hit> {
        if (signal.isOngoing || signal.isGroupSummary || signal.isOwn) return emptyList()
        val text = text(signal)
        if (Classifier.quotesOneTimeCode(text)) return emptyList()
        return triggers.filter { it.on && it.packageName == signal.packageName }.mapNotNull { trigger ->
            when (trigger.watch) {
                Watch.AnyNotification -> Hit(trigger, null)
                Watch.NotificationWords -> match(trigger.words, text)?.let { Hit(trigger, it) }
                Watch.ScreenWords, Watch.NotOpenedBy -> null
            }
        }
    }

    /** Everything a notification says that a trigger reads: who it's from, its text, and its newest message. */
    private fun text(signal: Signal): String =
        listOfNotNull(signal.title, signal.text, signal.bigText, signal.messages.lastOrNull()?.text).joinToString("\n")

    /**
     * A notification trigger's loop: one per notification, closed when it goes, with its tap as the way back. It quotes
     * the part that says the word, or its newest words when a phrase ran from one part into the next.
     */
    fun fromNotification(trigger: Trigger, signal: Signal, word: String?): Loop {
        val last = signal.messages.lastOrNull()
        val parts = listOfNotNull(last?.text, signal.bigText, signal.text, signal.title).filter { it.isNotBlank() }
        val said = parts.firstOrNull { word == null || match(listOf(word), it) != null } ?: parts.firstOrNull()
        val sender = (last?.sender ?: signal.title)?.removePrefix("~")?.trim()?.takeIf { it.isNotEmpty() }
        return loop(
            trigger,
            id = "${trigger.id}:${signal.key}",
            who = sender.takeIf { trigger.kind.withPeople } ?: trigger.appLabel,
            words = said?.let { "“${Gist.of(it)}”" },
            at = signal.postedAt,
            route = Route.Notification(signal.key, signal.packageName),
            amount = said?.let(MoneyParser::find),
        ).copy(
            notificationKey = signal.key,
            // Keyed as the classifier keys a chat, so your reply in it closes the loop.
            conversation = (signal.conversationKey ?: signal.key).takeIf { trigger.kind.withPeople },
            canReply = trigger.kind == LoopKind.Reply && signal.hasReplyAction,
            updatedAt = last?.at ?: signal.postedAt,
        )
    }

    /**
     * A screen trigger's loop, one at a time: it closes when you leave the app without its words showing. It quotes
     * the trigger's own word and never the screen, so a Pay says no amount: its task is kept for the recap.
     */
    fun fromScreen(trigger: Trigger, word: String, at: Instant): Loop =
        loop(trigger, id = trigger.id, who = trigger.appLabel, words = "“$word”", at = at, route = Route.LaunchApp(trigger.packageName))

    /** A time trigger's loop for [day], from its time until midnight; the note says when you wanted to open it. */
    fun fromTime(trigger: Trigger, day: LocalDate, zone: ZoneId): Loop {
        val at = day.atTime(trigger.by ?: LocalTime.MIDNIGHT).atZone(zone).toInstant()
        val midnight = day.plusDays(1).atStartOfDay(zone).toInstant()
        return loop(trigger, id = "${trigger.id}:$day", who = trigger.appLabel, words = null, at = at, route = Route.LaunchApp(trigger.packageName))
            .copy(expiresAt = midnight)
    }

    /** The trigger's label stands in for what it saw. It's fully due and owed: you asked for it. */
    private fun loop(trigger: Trigger, id: String, who: String, words: String?, at: Instant, route: Route, amount: Money? = null): Loop {
        val app = trigger.appLabel
        val owed = amount?.takeIf { trigger.kind == LoopKind.Payment && it.amount.signum() > 0 }
        return Loop(
            id = LoopId("trigger:$id"),
            kind = trigger.kind,
            task = when (trigger.kind) {
                LoopKind.Reply -> "Reply to $who"
                LoopKind.CallBack -> "Call $who back"
                LoopKind.Payment -> if (owed != null) "Pay ${owed.short} in $app" else "Pay in $app"
                LoopKind.Flow -> "Finish in $app"
                LoopKind.Own -> "Get back to $app"
                else -> trigger.label ?: "Check $app"
            },
            who = who,
            detail = trigger.label ?: words,
            packageName = trigger.packageName,
            conversation = null,
            notificationKey = null,
            route = route,
            openedAt = at,
            updatedAt = at,
            expiresAt = at.plus(lifetime(trigger.kind)),
            due = 1.0,
            why = listOf("trigger"),
            amount = owed,
            trigger = TriggerRef(trigger.id, trigger.watch),
        )
    }

    /** As long as Earmark keeps the same kind of loop of its own. */
    private fun lifetime(kind: LoopKind): Duration = when (kind) {
        LoopKind.Payment -> Classifier.paymentLifetime
        LoopKind.Flow -> Situations.flowLifetime
        LoopKind.Own -> Situations.ownLifetime
        else -> Classifier.replyLifetime
    }

    private val gaps = Regex("""\s+""")
}
