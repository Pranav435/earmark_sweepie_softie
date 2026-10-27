package com.sweepiesoftie.earmark.listener

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.media.AudioManager
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.core.Blocker
import com.sweepiesoftie.earmark.core.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Polls `ACTIVITY_RESUMED` once a second, only between unlock and screen-off: no wakelocks,
 * no alarms. Each poll also ticks the engine and checks the audio mode for calls.
 *
 * Each poll reads the last few seconds, not just the time since the previous poll. The system
 * stamps an app switch from a clock snapshot it only corrects past a 2-second drift, and records
 * it a moment later, so a switch can carry a time the previous poll had already read past. Read
 * back to back, such a switch is never seen. On a Galaxy A34, 34 seconds in Instagram went
 * unnoticed, probably this way.
 */
class ForegroundWatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val apps: InstalledApps,
    private val log: DebugLog,
    private val dispatch: (Event) -> Unit,
) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var job: Job? = null
    private var current: String? = null
    private var onCall = false

    fun start() {
        if (job?.isActive == true) return
        if (!Permissions.usageAccess(context)) {
            log.add("ForegroundWatcher idle: no usage access")
            return
        }
        current = null
        job = scope.launch(Dispatchers.IO) {
            // Far enough back to find the app in front even if it opened before the watcher started,
            // as when the listener reconnects mid-session after an update.
            var lookBack = FIRST_LOOKBACK_MILLIS
            while (isActive) {
                val now = System.currentTimeMillis()
                latestResumed(now - lookBack, now + LOOK_AHEAD_MILLIS)?.let { (pkg, at) ->
                    if (pkg != current) {
                        current = pkg
                        log.add("in front: $pkg, seen ${now - at} ms after its switch")
                        dispatch(Event.ForegroundChanged(pkg, apps.label(pkg)))
                    }
                }
                lookBack = OVERLAP_MILLIS
                checkCall()
                dispatch(Event.Tick)
                delay(POLL_MILLIS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * When the last visit of 20 s or more to each of [packages] ended between [from] and [to], from usage history, so
     * time triggers know about visits before the listener started (ADR 0038). A visit runs from an app coming to the
     * front until another app does or the screen goes off; one still going on isn't counted here.
     */
    fun longVisits(packages: Set<String>, from: Long, to: Long): Map<String, Instant> {
        if (packages.isEmpty() || !Permissions.usageAccess(context)) return emptyMap()
        val found = HashMap<String, Instant>()
        try {
            val events = usage.queryEvents(from, to)
            val event = UsageEvents.Event()
            var front: String? = null
            var since = 0L
            fun end(at: Long) {
                val app = front ?: return
                if (app in packages && at - since >= LONG_VISIT_MILLIS) found[app] = Instant.ofEpochMilli(at)
            }
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> if (event.packageName != front) {
                        end(event.timeStamp)
                        front = event.packageName
                        since = event.timeStamp
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        end(event.timeStamp)
                        front = null
                    }
                }
            }
        } catch (e: RuntimeException) {
            log.error("queryEvents for visits", e)
        }
        return found
    }

    /** The package and time of the newest switch in the window. Events come in time order. */
    private fun latestResumed(from: Long, to: Long): Pair<String, Long>? = try {
        val events = usage.queryEvents(from, to)
        val event = UsageEvents.Event()
        var latest: Pair<String, Long>? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) latest = event.packageName to event.timeStamp
        }
        latest
    } catch (e: RuntimeException) {
        log.error("queryEvents", e)
        null
    }

    private fun checkCall() {
        val call = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
        if (call != onCall) {
            onCall = call
            dispatch(Event.BlockerChanged(Blocker.Call, call))
        }
    }

    private companion object {
        const val POLL_MILLIS = 1_000L

        /** The engine's bar for a visit that counts as opening an app (Triggers.visit). */
        const val LONG_VISIT_MILLIS = 20_000L
        const val FIRST_LOOKBACK_MILLIS = 3_600_000L

        /** Covers the system's 2-second clock tolerance and a slow recording, many times over. */
        const val OVERLAP_MILLIS = 10_000L

        /** A switch stamped slightly ahead of this clock is seen now, not a poll later. */
        const val LOOK_AHEAD_MILLIS = 5_000L
    }
}
