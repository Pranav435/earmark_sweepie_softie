package com.sweepiesoftie.earmark.listener

import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.core.Event
import java.time.Instant

/** Runtime-only broadcasts: they can't be declared in the manifest, so the listener registers them. */
class SystemReceivers(
    private val context: Context,
    private val log: DebugLog,
    private val onUnlock: () -> Unit,
    private val onScreenOff: () -> Unit,
    private val dispatch: (Event) -> Unit,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var watchingSince = 0L

    /**
     * Some builds don't deliver USER_PRESENT to runtime receivers, so after SCREEN_ON we also
     * watch the keyguard every 100 ms until it opens. Bounded to the lock screen and two minutes.
     */
    private val keyguardWatch = object : Runnable {
        override fun run() {
            when {
                !keyguard.isKeyguardLocked -> onUnlock()
                SystemClock.elapsedRealtime() - watchingSince < KEYGUARD_WATCH_MILLIS -> handler.postDelayed(this, KEYGUARD_POLL_MILLIS)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    handler.removeCallbacks(keyguardWatch)
                    onUnlock()
                }
                Intent.ACTION_SCREEN_ON -> {
                    watchingSince = SystemClock.elapsedRealtime()
                    handler.removeCallbacks(keyguardWatch)
                    keyguardWatch.run()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(keyguardWatch)
                    onScreenOff()
                }
                AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> dispatch(nextAlarm())
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        try {
            // System broadcasts still reach a not-exported receiver; nothing else can.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            registered = true
        } catch (e: RuntimeException) {
            log.error("SystemReceivers register", e)
        }
    }

    fun stop() {
        handler.removeCallbacks(keyguardWatch)
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            log.error("SystemReceivers unregister", e)
        }
        registered = false
    }

    fun nextAlarm(): Event.AlarmChanged =
        Event.AlarmChanged(alarms.nextAlarmClock?.triggerTime?.let { Instant.ofEpochMilli(it) })

    private companion object {
        const val KEYGUARD_POLL_MILLIS = 100L
        const val KEYGUARD_WATCH_MILLIS = 120_000L
    }
}
