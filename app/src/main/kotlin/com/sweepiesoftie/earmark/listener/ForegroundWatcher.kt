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

/**
 * Polls `ACTIVITY_RESUMED` once a second, only between unlock and screen-off: no wakelocks,
 * no alarms. Each poll also ticks the engine and checks the audio mode for calls.
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
            var since = System.currentTimeMillis() - LOOKBACK_MILLIS
            while (isActive) {
                val now = System.currentTimeMillis()
                latestResumed(since, now)?.let { pkg ->
                    if (pkg != current) {
                        current = pkg
                        dispatch(Event.ForegroundChanged(pkg, apps.label(pkg)))
                    }
                }
                since = now
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

    private fun latestResumed(from: Long, to: Long): String? = try {
        val events = usage.queryEvents(from, to)
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) latest = event.packageName
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
        const val LOOKBACK_MILLIS = 10_000L
    }
}
