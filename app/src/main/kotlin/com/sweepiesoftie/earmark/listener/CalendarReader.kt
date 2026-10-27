package com.sweepiesoftie.earmark.listener

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.core.CalendarEvent
import java.time.Instant

/** Optional: upcoming events with a place or a meeting link. Without READ_CALENDAR it stays idle. */
class CalendarReader(private val context: Context, private val log: DebugLog) {
    private var loggedIdle = false
    private val meetingLink = Regex("""https://(meet\.google\.com|[\w.-]*zoom\.us|teams\.microsoft\.com|teams\.live\.com|[\w.-]*webex\.com)/\S+""")

    /** Events starting in the next 4 hours; the core keeps those whose leave-by time is within 2 hours. Null when not allowed. */
    fun soon(): List<CalendarEvent>? {
        if (!Permissions.calendar(context)) {
            if (!loggedIdle) log.add("CalendarReader idle: no calendar permission")
            loggedIdle = true
            return null
        }
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + WINDOW_MILLIS)
        }.build()
        val columns = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY,
        )
        return try {
            context.contentResolver.query(uri, columns, null, null, CalendarContract.Instances.BEGIN)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(5) == 1) continue
                        val location = c.getString(3)?.takeIf { it.isNotBlank() }
                        val link = meetingLink.find(listOfNotNull(location, c.getString(4)).joinToString(" "))?.value
                        add(
                            CalendarEvent(
                                id = c.getLong(0),
                                title = c.getString(1) ?: "Event",
                                startsAt = Instant.ofEpochMilli(c.getLong(2)),
                                place = location?.takeIf { link == null || !it.startsWith("http") },
                                meetingLink = link,
                            ),
                        )
                    }
                }
            } ?: emptyList()
        } catch (e: RuntimeException) {
            log.error("calendar query", e)
            null
        }
    }

    private companion object {
        const val WINDOW_MILLIS = 4 * 60 * 60 * 1000L
    }
}
