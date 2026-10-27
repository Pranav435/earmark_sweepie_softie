package com.sweepiesoftie.earmark.listener

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sweepiesoftie.earmark.AppGraph
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.core.Waiting
import com.sweepiesoftie.earmark.store.UserSettings
import com.sweepiesoftie.earmark.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Holds Earmark's process at foreground priority so the phone doesn't freeze it while other apps are
 * in front (ADR 0018). Samsung's One UI freezes background apps that have no foreground service, and
 * a frozen listener only hears about notifications and unlocks once Earmark is opened again. It does
 * no work of its own; the listener in the same process does.
 *
 * Its notification says what Earmark is holding for you, so the dot has words TalkBack can read and a
 * button that hands it back (ADR 0034), and how long a pause lasts (ADR 0036).
 */
class KeepAliveService : Service() {
    private var scope: CoroutineScope? = null

    /** What the notification says: a pause and when it ends, else the held guess, else just that Earmark is on. */
    private data class Shown(val held: Waiting? = null, val pausedUntil: Long? = null)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Called first in every case: a service started with startForegroundService must call it, even to stop.
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(Shown()), type())
        } catch (e: RuntimeException) {
            AppGraph.debugLog.error("KeepAliveService startForeground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Permissions.notificationAccess(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        if (scope == null) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { it.launch { shown().collect(::post) } }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    /** Changes only when what it says changes: the engine publishes after every event, about once a second while the phone is in use. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun shown(): Flow<Shown> = combine(
        AppGraph.listener.map { it.held }.distinctUntilChanged(),
        AppGraph.settings.state.map { it.pausedUntil }.distinctUntilChanged(),
    ) { held, until -> held to until }
        .transformLatest { (held, until) ->
            val wait = until?.let { it - System.currentTimeMillis() } ?: 0L
            if (wait <= 0L) {
                emit(Shown(held))
                return@transformLatest
            }
            emit(Shown(pausedUntil = until))
            // The pause ends by itself, and the notification should stop saying so then.
            if (until != UserSettings.UNTIL_RESUMED) {
                delay(wait)
                emit(Shown(held))
            }
        }
        .distinctUntilChanged()

    private fun post(shown: Shown) {
        if (!Permissions.postNotifications(this)) return
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(shown))
        } catch (e: SecurityException) {
            AppGraph.debugLog.error("KeepAliveService notify", e)
        }
    }

    private fun type(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun notification(shown: Shown): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.keep_alive_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val plain = builder(getString(R.string.keep_alive_title), getString(R.string.keep_alive_body), open)
        val held = shown.held
        return when {
            shown.pausedUntil != null -> {
                val until = if (shown.pausedUntil == UserSettings.UNTIL_RESUMED) {
                    getString(R.string.keep_alive_paused_forever)
                } else {
                    getString(R.string.keep_alive_paused_until, DateFormat.getTimeFormat(this).format(Date(shown.pausedUntil)))
                }
                val resume = PendingIntent.getBroadcast(this, 0, Intent(this, Resume::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder(getString(R.string.keep_alive_paused), until, open).addAction(0, getString(R.string.keep_alive_resume), resume).build()
            }
            held != null -> {
                val handBack = PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, HandBackActivity::class.java).putExtra(HandBackActivity.EXTRA_LOOP, held.loopId.value).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                // Their words stay off the lock screen, which only says Earmark is on.
                builder(getString(R.string.keep_alive_holding, held.note.who), held.note.words ?: held.task, handBack)
                    .addAction(0, held.note.verb, handBack)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(plain.build())
                    .build()
            }
            else -> plain.build()
        }
    }

    private fun builder(title: String, text: String, tap: PendingIntent): NotificationCompat.Builder = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_glyph)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(tap)
        .setOngoing(true)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_MIN)

    /** "Resume" on the notification while Earmark is paused. */
    class Resume : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppGraph.settings.pause(null)
        }
    }

    /** Restarts the service after a reboot or an update, the two moments Android always allows it. */
    class Restarter : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) start(context)
        }
    }

    companion object {
        private const val CHANNEL = "running"
        private const val NOTIFICATION_ID = 3

        /** In the foreground now, so opening Earmark doesn't post the "Earmark is on" notification again. */
        @Volatile
        private var running = false

        /**
         * Safe from anywhere. From the background Android allows it only with an exemption (after boot or an
         * update, with battery optimisation off, or while an overlay shows); a refusal is logged, not thrown.
         */
        fun start(context: Context) {
            if (running || !Permissions.notificationAccess(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException on API 31+ is an IllegalStateException.
                AppGraph.debugLog.error("KeepAliveService start", e)
            } catch (e: SecurityException) {
                AppGraph.debugLog.error("KeepAliveService start", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (e: IllegalStateException) {
                AppGraph.debugLog.error("KeepAliveService stop", e)
            } catch (e: SecurityException) {
                AppGraph.debugLog.error("KeepAliveService stop", e)
            }
        }
    }
}
