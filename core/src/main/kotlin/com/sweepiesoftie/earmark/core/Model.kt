package com.sweepiesoftie.earmark.core

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** The kinds of loop v0.1 can open. "Own" waits for the Quick Settings tile in v0.2. */
public enum class LoopKind { Reply, Payment, Flow, Routine, LeaveBy }

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
    /** Set when the user went to the loop's app and left without finishing; the verb becomes "Finish". */
    val started: Boolean = false,
    /** Ride ("Book") or meeting ("Join") for Leave-by loops. */
    val leaveBy: LeaveByDetail? = null,
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
    val messages: List<Message> = emptyList(),
    /** Hashed by the adapter before it reaches the core (ADR 0004). */
    val conversationKey: String? = null,
    val isGroupConversation: Boolean = false,
    val mentionsUser: Boolean = false,
    val hasReplyAction: Boolean = false,
    val isOngoing: Boolean = false,
    val isGroupSummary: Boolean = false,
    val isOwn: Boolean = false,
    val category: String? = null,
)

public data class Message(val sender: String?, val text: String, val at: Instant, val fromUser: Boolean = false, val isBot: Boolean = false)

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

public data class Settings(
    val ownPackage: String,
    val feedApps: Set<String> = CuratedApps.feeds,
    val driftAfter: Duration = Duration.ofSeconds(90),
    /** Minimum time between any two pills. Demo mode sets it to zero so stories can run back to back. */
    val pillCooldown: Duration = Duration.ofMinutes(10),
    val demoMode: Boolean = false,
    val paused: Boolean = false,
    val use24Hour: Boolean = false,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Package that answers `ACTION_SET_ALARM`, so time spent setting an alarm is not drift. */
    val clockPackage: String? = null,
    /** Installed ride app used as the way back for Leave-by loops with a place. */
    val rideApp: String? = null,
)

public data class KindStat(val holds: Int = 0, val ignores: Int = 0, val ignoreStreak: Int = 0) {
    val muted: Boolean get() = ignoreStreak >= MUTE_AFTER_IGNORES

    public companion object {
        public const val MUTE_AFTER_IGNORES: Int = 3
    }
}

public data class ContactStat(val replies: Int = 0, val medianReplyMinutes: Double = 0.0)

/** What the store hands the engine at start-up. */
public data class Stats(
    val kinds: Map<LoopKind, KindStat> = emptyMap(),
    val contacts: Map<String, ContactStat> = emptyMap(),
    /** Alarm times seen on recent weeknights, newest first. */
    val weeknightAlarms: List<LocalTime> = emptyList(),
)

public data class ChipCard(
    val loopId: LoopId,
    val kind: LoopKind,
    val title: String,
    val subtitle: String,
    /** A verb button replaces ✓ on carry-over chips ("Pay now"). */
    val action: String?,
    val score: Double,
)

public data class PillCard(val loopId: LoopId, val kind: LoopKind, val task: String, val verb: String)

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
    public data class ChipHeld(val loopId: LoopId) : Event
    public data class ChipDismissed(val loopId: LoopId) : Event
    /** The chip body or its verb button was tapped: hand the loop back now. */
    public data class ChipActed(val loopId: LoopId) : Event
    public data class PillTapped(val loopId: LoopId) : Event
    public data class PillDismissed(val loopId: LoopId) : Event
    public data class ReplyChosen(val loopId: LoopId, val text: String) : Event
    public data class ReplySent(val loopId: LoopId) : Event
    public data class KindUnmuted(val kind: LoopKind) : Event
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
    public data class RecordAlarm(val time: LocalTime, val on: java.time.LocalDate) : Effect
    /** One decision line for DebugLog. Never carries message text. */
    public data class Log(val line: String) : Effect
}
