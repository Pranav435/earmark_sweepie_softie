package com.sweepiesoftie.earmark.core

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** The kinds of loop Earmark can open. An Own loop is one you make with the "Earmark this" tile (ADR 0035). */
public enum class LoopKind {
    Reply, Payment, Flow, Routine, LeaveBy, CallBack, Reminder, Email, Own;

    /**
     * Opened by something that came in (a message, call, email, reminder, request or bill), or by you, rather than
     * by the clock, the calendar or an app you left: listed as it arrives (ADR 0025), and boosted at your first unlock after it.
     */
    internal val cameIn: Boolean get() = this != Flow && this != Routine && this != LeaveBy

    /**
     * A person at the other end. How you deal with them teaches Earmark about their app (ADR 0027), and "not this"
     * three times in a row mutes their chat rather than the whole kind (ADR 0033).
     */
    public val withPeople: Boolean get() = this == Reply || this == CallBack || this == Email
}

@JvmInline
public value class LoopId(public val value: String)

/**
 * Where a hand-back goes. The core names the route; the adapter holds the live
 * `PendingIntent`s for [Notification] routes in memory only (ADR 0004).
 */
public sealed interface Route {
    /** Replay the notification's tap action; the adapter falls back to [packageName]'s launch intent. */
    public data class Notification(val key: String, val packageName: String) : Route
    public data class LaunchApp(val packageName: String) : Route
    public data class SetAlarm(val time: LocalTime) : Route
    public data class OpenUri(val uri: String) : Route
}

/** An open loop as the core knows it. Message text lives here only while the loop is open. */
public data class Loop(
    val id: LoopId,
    val kind: LoopKind,
    /** The task in the imperative, as the pill shows it: "Reply to Rahul". */
    val task: String,
    /** Who the loop is with, when it is with a person. */
    val who: String?,
    /** The evidence line on the chip, before the age: "“free at 5?”". */
    val detail: String?,
    /** The loop's own app: being in it is not drift. */
    val packageName: String?,
    /** Hashed conversation key (ADR 0004). */
    val conversation: String?,
    val notificationKey: String?,
    val route: Route,
    val openedAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant,
    /** How soon this is due, 0..1, worked out when the loop opens. */
    val due: Double,
    val canReply: Boolean = false,
    /** What the message asks for is urgent ("call me asap"): it can take over a guess you came for (ADR 0027). */
    val urgent: Boolean = false,
    /** Why it's due, for DebugLog: "urgent", "asks", "mentions you". Never message text. */
    val why: List<String> = emptyList(),
    /** A group conversation, and the group's name when the app gives one (ADR 0028). */
    val inGroup: Boolean = false,
    val groupName: String? = null,
    /** Set when the user went to the loop's app and left without finishing; the verb becomes "Finish". */
    val started: Boolean = false,
    /** A Payment for a bill that is due rather than a person asking: "Pay your Airtel bill ₹499" (ADR 0029). */
    val bill: Boolean = false,
    /** What a Payment is for, so paying someone else a different amount doesn't close it (ADR 0031). */
    val amount: Money? = null,
    /** Ride ("Book") or meeting ("Join") for Leave-by loops. */
    val leaveBy: LeaveByDetail? = null,
    /** The custom trigger that made it, or that marked Earmark's own loop as owed (ADR 0038). */
    val trigger: TriggerRef? = null,
)

public data class LeaveByDetail(val title: String, val place: String?, val startsAt: Instant, val leaveAt: Instant, val isMeeting: Boolean)

/** A notification described in plain values by the listener adapter. */
public data class Signal(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val postedAt: Instant,
    val title: String? = null,
    val text: String? = null,
    /** The expanded text, when the app gives one: an email's subject and the start of its body. */
    val bigText: String? = null,
    val messages: List<Message> = emptyList(),
    /** Hashed by the adapter before it reaches the core (ADR 0004). */
    val conversationKey: String? = null,
    val isGroupConversation: Boolean = false,
    /** The group's name, for a group conversation. */
    val conversationTitle: String? = null,
    /** How the app names you in its own conversation (WhatsApp says "You"), for an @mention of you. */
    val selfName: String? = null,
    val hasReplyAction: Boolean = false,
    val isOngoing: Boolean = false,
    val isGroupSummary: Boolean = false,
    val isOwn: Boolean = false,
    val category: String? = null,
)

/** [mentionsYou] is set when the app itself marked the message as mentioning you (ADR 0028). */
public data class Message(
    val sender: String?,
    val text: String,
    val at: Instant,
    val fromUser: Boolean = false,
    val isBot: Boolean = false,
    val mentionsYou: Boolean = false,
)

public data class CalendarEvent(
    val id: Long,
    val title: String,
    val startsAt: Instant,
    val place: String?,
    val meetingLink: String?,
)

public enum class RemovalReason { Click, AppCancel, UserDismiss, Other }

public enum class Playback { Playing, Paused, TrackChanged, None }

public enum class Blocker { Call, Navigation, DoNotDisturb }

public enum class Overlay { Chip, Dot, Pill, ReplyStrip }

public enum class Outcome { Done, Dismissed, Expired }

/** How long a feed app can be in front before it is drift. */
public enum class DriftTimer {
    /** As soon as you're in the feed, not just passing through it (ADR 0030). */
    RightAway,

    /** Your usual visit to the feed app, sooner when the loop is urgent (ADR 0023). */
    Smart,

    /** [Settings.driftAfter]. */
    Fixed,
}

public data class Settings(
    val ownPackage: String,
    val feedApps: Set<String> = CuratedApps.feeds,
    val driftTimer: DriftTimer = DriftTimer.Fixed,
    val driftAfter: Duration = Duration.ofSeconds(90),
    val demoMode: Boolean = false,
    /** No chips or pills before this time; [Instant.MAX] until you resume (ADR 0036). */
    val pausedUntil: Instant? = null,
    val use24Hour: Boolean = false,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Package that answers `ACTION_SET_ALARM`, so time spent setting an alarm is not drift. */
    val clockPackage: String? = null,
    /** Installed ride app used as the way back for Leave-by loops with a place. */
    val rideApp: String? = null,
    /** Your name and nicknames from your own contact card, so a group message that mentions you counts (ADR 0028). Never stored or logged. */
    val myNames: List<String> = emptyList(),
    /** Launchers: the home screen is no app to earmark (ADR 0035). */
    val homeApps: Set<String> = emptySet(),
    /** Custom triggers from Advanced, on or off (ADR 0038). */
    val triggers: List<Trigger> = emptyList(),
) {
    public fun paused(now: Instant): Boolean = pausedUntil?.isAfter(now) == true
}

public data class KindStat(val holds: Int = 0, val ignores: Int = 0, val ignoreStreak: Int = 0) {
    val muted: Boolean get() = ignoreStreak >= MUTE_AFTER_IGNORES

    public companion object {
        public const val MUTE_AFTER_IGNORES: Int = 3
    }
}

/**
 * How you answer one conversation, keyed by its hashed key: replies and how fast, and how often you open its
 * notifications rather than swipe them away (ADR 0028). [ignoreStreak] counts "not this" on its chips in a row;
 * three mute just this chat (ADR 0033).
 */
public data class ContactStat(
    val replies: Int = 0,
    val medianReplyMinutes: Double = 0.0,
    val opened: Int = 0,
    val swiped: Int = 0,
    val ignoreStreak: Int = 0,
) {
    val muted: Boolean get() = ignoreStreak >= KindStat.MUTE_AFTER_IGNORES

    /** Opened rather than swiped away, smoothed so a conversation with no taps reads 0.5. */
    val openRate: Double get() = (opened + 1.0) / (opened + swiped + 2.0)

    /** You open this conversation's notifications more often than not, so a question in it is for you too. */
    val opensOften: Boolean get() = opened >= OPENS_OFTEN && openRate >= OPEN_RATE

    public companion object {
        public const val OPENS_OFTEN: Int = 3
        private const val OPEN_RATE = 0.6
    }
}

/**
 * Per app, keyed by package; nothing about what you did there. How long you usually stay in a feed app, for the
 * smart drift timer, and how you deal with messages in a chat app, so a new person there starts from that (ADR 0027).
 */
public data class AppStat(
    val visits: Int = 0,
    val medianVisitSeconds: Double = 0.0,
    /** Messages you dealt with, and ones you swiped away or left to expire. */
    val done: Int = 0,
    val left: Int = 0,
    /** How long after a message you usually deal with it. */
    val medianDoneMinutes: Double = 0.0,
)

/** What the store hands the engine at start-up. */
public data class Stats(
    val kinds: Map<LoopKind, KindStat> = emptyMap(),
    val contacts: Map<String, ContactStat> = emptyMap(),
    /** Alarm times seen on recent weeknights, newest first. */
    val weeknightAlarms: List<LocalTime> = emptyList(),
    val apps: Map<String, AppStat> = emptyMap(),
    /** By trigger id (ADR 0038). */
    val triggers: Map<String, TriggerStat> = emptyMap(),
)

/**
 * What Earmark says about a loop, in the order a note from a friend reads (ADR 0032): who it's from, their own
 * words, and one verb. [person] is true for someone you know, so the overlay shows their photo from the
 * notification [faceKey], or their initial; otherwise it shows [packageName]'s icon.
 */
public data class Note(
    val who: String,
    val words: String?,
    val verb: String,
    val person: Boolean,
    val packageName: String?,
    val faceKey: String? = null,
)

/** [why] is the small line under the note: "Here to reply?", or "Still on your list from 1:18 PM" for a carry-over. [task] is for TalkBack. */
public data class ChipCard(val loopId: LoopId, val kind: LoopKind, val note: Note, val why: String, val task: String, val score: Double)

/**
 * [why] says why the task is back: "You came here to reply", "Before you forget" for something that came in while
 * you were using the phone, or "Next up". [task] is for TalkBack: "Reply to Rahul".
 */
public data class PillCard(val loopId: LoopId, val kind: LoopKind, val note: Note, val why: String, val task: String)

/** An open loop on the home screen, in the order Earmark would guess them (ADR 0032). [at] is when it last came in. */
public data class Waiting(val loopId: LoopId, val kind: LoopKind, val note: Note, val task: String, val at: Instant)

/**
 * What the "Earmark this" tile says (ADR 0035): the app an Own loop holds, and the app in front that a tap would
 * hold, or null for the home screen, a feed, Earmark itself or a locked phone. [feed] names a feed app in front,
 * so the tile can say why it won't hold it (ADR 0037), and [ignored] an app a trigger ignores (ADR 0038).
 */
public data class TileState(val holding: String? = null, val canHold: String? = null, val feed: String? = null, val ignored: String? = null)

/** One row of the recap, upserted by loop id. Labels are trimmed to kind and time after 24 h by the store. */
public data class LoopEntry(
    val loopId: LoopId,
    val kind: LoopKind,
    val label: String,
    val openedAt: Instant,
    val guessedAt: Instant?,
    val closedAt: Instant?,
    val outcome: Outcome?,
    val broughtBack: Boolean,
    /** On today's list: a message or request Earmark picked up, or a loop it showed as a chip or pill (ADR 0025). */
    val listed: Boolean,
)

public sealed interface Event {
    public data class NotificationPosted(val signal: Signal) : Event
    public data class NotificationRemoved(val key: String, val reason: RemovalReason) : Event
    public data object Unlocked : Event
    public data object ScreenOff : Event
    public data class ForegroundChanged(val packageName: String, val appLabel: String) : Event
    public data class PlaybackChanged(val playback: Playback) : Event
    public data class BlockerChanged(val blocker: Blocker, val active: Boolean) : Event
    public data class AlarmChanged(val nextAlarm: Instant?) : Event
    public data class CalendarSoon(val events: List<CalendarEvent>) : Event
    public data class ChipDismissed(val loopId: LoopId) : Event
    /** The chip ran out its timer untouched: Earmark keeps watching its loop, and the dot says so (ADR 0007). */
    public data class ChipFaded(val loopId: LoopId) : Event
    /** The chip or its verb button was tapped: hand the loop back now. It's the "yes, this one" Earmark learns from. */
    public data class ChipActed(val loopId: LoopId) : Event
    public data class PillTapped(val loopId: LoopId) : Event
    public data class PillDismissed(val loopId: LoopId) : Event
    /** A waiting row on the home screen was tapped: open its way back, as a chip tap would. */
    public data class WaitingOpened(val loopId: LoopId) : Event
    /** "Already done" on a waiting row: you dealt with it where Earmark can't see (ADR 0036). */
    public data class WaitingDone(val loopId: LoopId) : Event
    /** The verb on the "Earmark is on" notification while it names the held guess (ADR 0034). */
    public data class HeldOpened(val loopId: LoopId) : Event
    /** The "Earmark this" tile was tapped: hold the app in front, or let go of the one held (ADR 0035). */
    public data object Earmarked : Event
    public data class ReplyChosen(val loopId: LoopId, val text: String) : Event
    public data class ReplySent(val loopId: LoopId) : Event
    public data class KindUnmuted(val kind: LoopKind) : Event
    /** Every chat muted by "not this" can be guessed again (ADR 0033). */
    public data object ChatsUnmuted : Event
    /** A trigger muted by "not this" can be guessed again (ADR 0038). */
    public data class TriggerUnmuted(val id: String) : Event
    /**
     * [packageName]'s window, read for screen triggers (ADR 0038). Only apps with a screen trigger are read; the text is
     * matched against their words and dropped, never kept or logged.
     */
    public data class ScreenRead(val packageName: String, val text: String) : Event
    /**
     * Today's usage history for [apps]: when the last visit of 20 s or more to each ended. A time trigger waits for its
     * app's history, so a restart doesn't open a loop for an app you already opened today (ADR 0038).
     */
    public data class AppVisits(val apps: Set<String>, val lastLongVisit: Map<String, Instant>) : Event
    public data class SettingsChanged(val settings: Settings) : Event
    public data class StatsLoaded(val stats: Stats) : Event
    public data object Tick : Event
}

public sealed interface Effect {
    public data class ShowChip(val chip: ChipCard) : Effect
    public data object ShowDot : Effect
    public data class ShowPill(val pill: PillCard) : Effect
    public data class HideOverlay(val overlay: Overlay) : Effect
    public data class OpenWayBack(val loopId: LoopId, val route: Route) : Effect
    /** [to] is who the replies go to: the strip names them, because it stays while you move between chats in the app. */
    public data class SuggestReplies(val loopId: LoopId, val notificationKey: String, val to: String) : Effect
    public data class SendReply(val loopId: LoopId, val notificationKey: String, val text: String) : Effect
    /** Drop the notification's intents and message text from adapter memory (ADR 0004). */
    public data class Forget(val notificationKey: String) : Effect
    public data class RecordLoop(val entry: LoopEntry) : Effect
    public data class RecordKind(val kind: LoopKind, val stat: KindStat) : Effect
    public data class RecordContact(val conversation: String, val stat: ContactStat) : Effect
    public data class RecordApp(val packageName: String, val stat: AppStat) : Effect
    public data class RecordAlarm(val time: LocalTime, val on: java.time.LocalDate) : Effect
    public data class RecordTrigger(val id: String, val stat: TriggerStat) : Effect
    /** One decision line for DebugLog. Never carries message text. */
    public data class Log(val line: String) : Effect
}
