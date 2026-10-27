package com.sweepiesoftie.earmark.core

import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** Loops that come from the time and place you are in rather than from a notification: Routine, Leave-by, Flow and Own. */
internal object Situations {
    private val bedtime: LocalTime = LocalTime.of(22, 30)
    private val routineEnds: LocalTime = LocalTime.of(4, 0)
    private const val USUAL_AFTER_NIGHTS = 3
    private val leaveByWindow: Duration = Duration.ofHours(2)
    val flowDwell: Duration = Duration.ofMinutes(2)
    val flowLifetime: Duration = Duration.ofHours(2)

    /** How long back in an earmarked app counts as doing what you earmarked it for, not passing through, and how long one is kept (ADR 0035). */
    val ownDwell: Duration = Duration.ofSeconds(20)
    val ownLifetime: Duration = Duration.ofHours(6)
    private val airport = Regex("""(?i)\b(flight|airport|terminal)\b""")

    /**
     * After 22:30 on a weeknight with no alarm for the next morning, given you usually set one.
     * Demo mode drops the bedtime window so the Bedtime story can run at any hour (ADR 0006).
     */
    fun routine(now: Instant, settings: Settings, nextAlarm: Instant?, weeknightAlarms: List<LocalTime>): Loop? {
        val usual = weeknightAlarms.groupingBy { it }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value >= USUAL_AFTER_NIGHTS }?.key ?: return null
        val local = now.atZone(settings.zone)
        val inWindow = !local.toLocalTime().isBefore(bedtime) || local.toLocalTime().isBefore(routineEnds)
        if (!inWindow && !settings.demoMode) return null
        val morning = if (local.toLocalTime().isBefore(routineEnds)) local.toLocalDate() else local.toLocalDate().plusDays(1)
        if (morning.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) return null
        val morningNoon = morning.atTime(12, 0).atZone(settings.zone).toInstant()
        if (nextAlarm != null && nextAlarm.isAfter(now) && nextAlarm.isBefore(morningNoon)) return null
        val time = Copy(settings).alarmTime(usual)
        return Loop(
            id = LoopId("routine:alarm:$morning"),
            kind = LoopKind.Routine,
            task = "Set your $time alarm",
            who = null,
            detail = null,
            packageName = settings.clockPackage,
            conversation = null,
            notificationKey = null,
            route = Route.SetAlarm(usual),
            openedAt = now,
            updatedAt = now,
            expiresAt = morning.atTime(routineEnds).atZone(settings.zone).toInstant(),
            due = 1.0,
        )
    }

    /** True when an alarm lands on a weekday morning, so it belongs in the weeknight history. */
    fun isWeekdayMorning(alarm: ZonedDateTime): Boolean =
        alarm.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && alarm.toLocalTime().isBefore(LocalTime.NOON)

    /** Events with a place or a meeting link whose leave-by time is within the next 2 hours. */
    fun leaveBy(events: List<CalendarEvent>, now: Instant, settings: Settings): List<Loop> = events.mapNotNull { event ->
        val isMeeting = event.meetingLink != null
        if (!isMeeting && event.place.isNullOrBlank()) return@mapNotNull null
        val buffer = when {
            isMeeting -> Duration.ofMinutes(5)
            airport.containsMatchIn(event.title + " " + event.place) -> Duration.ofMinutes(150)
            else -> Duration.ofMinutes(30)
        }
        val leaveAt = event.startsAt.minus(buffer)
        if (Duration.between(now, leaveAt) > leaveByWindow || !event.startsAt.isAfter(now)) return@mapNotNull null
        val route = when {
            event.meetingLink != null -> Route.OpenUri(event.meetingLink)
            settings.rideApp != null -> Route.LaunchApp(settings.rideApp)
            else -> Route.OpenUri("geo:0,0?q=" + URLEncoder.encode(event.place, Charsets.UTF_8))
        }
        val task = when {
            isMeeting -> "Join ${event.title}"
            route is Route.LaunchApp -> rideTask(event.place)
            else -> "Leave for ${event.title}"
        }
        val minutesToLeave = Duration.between(now, leaveAt).toMinutes().toDouble()
        Loop(
            id = LoopId("leaveby:${event.id}"),
            kind = LoopKind.LeaveBy,
            task = task,
            who = null,
            detail = null,
            packageName = (route as? Route.LaunchApp)?.packageName,
            conversation = null,
            notificationKey = null,
            route = route,
            openedAt = now,
            updatedAt = now,
            expiresAt = event.startsAt,
            // Fully due within the hour before leaving, fading to nothing three hours out.
            due = ((180.0 - minutesToLeave) / 120.0).coerceIn(0.0, 1.0),
            leaveBy = LeaveByDetail(event.title, event.place, event.startsAt, leaveAt, isMeeting),
        )
    }

    /** "Airport · Terminal 1" → "Book your airport ride"; a longer place name reads better left out. */
    private fun rideTask(place: String?): String {
        val word = place?.substringBefore(" · ")?.substringBefore(",")?.trim()
        return if (word != null && word.isNotEmpty() && ' ' !in word) "Book your ${word.lowercase()} ride" else "Book your ride"
    }

    fun flow(packageName: String, appLabel: String, leftAt: Instant): Loop = Loop(
        id = LoopId("flow:$packageName"),
        kind = LoopKind.Flow,
        task = "Finish in $appLabel",
        who = appLabel,
        detail = null,
        packageName = packageName,
        conversation = null,
        notificationKey = null,
        route = Route.LaunchApp(packageName),
        openedAt = leftAt,
        updatedAt = leftAt,
        expiresAt = leftAt.plus(flowLifetime),
        due = 0.5,
    )

    /** The app in front, held from the "Earmark this" tile. You asked for it, so it's fully due. */
    fun own(packageName: String, appLabel: String, at: Instant): Loop = Loop(
        id = LoopId("own:$packageName"),
        kind = LoopKind.Own,
        task = "Get back to $appLabel",
        who = appLabel,
        detail = null,
        packageName = packageName,
        conversation = null,
        notificationKey = null,
        route = Route.LaunchApp(packageName),
        openedAt = at,
        updatedAt = at,
        expiresAt = at.plus(ownLifetime),
        due = 1.0,
    )
}
