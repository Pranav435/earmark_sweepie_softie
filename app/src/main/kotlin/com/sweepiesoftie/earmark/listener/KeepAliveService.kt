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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sweepiesoftie.earmark.AppGraph
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.ui.MainActivity

/**
 * Holds Earmark's process at foreground priority so the phone doesn't freeze it while other apps are
 * in front (ADR 0018). Samsung's One UI freezes background apps that have no foreground service, and
 * a frozen listener only hears about notifications and unlocks once Earmark is opened again. It does
 * no work of its own; the listener in the same process does.
 */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Called first in every case: a service started with startForegroundService must call it, even to stop.
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type())
        } catch (e: RuntimeException) {
            AppGraph.debugLog.error("KeepAliveService startForeground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Permissions.notificationAccess(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun type(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.keep_alive_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_glyph)
            .setContentTitle(getString(R.string.keep_alive_title))
            .setContentText(getString(R.string.keep_alive_body))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
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

        /**
         * Safe from anywhere. From the background Android allows it only with an exemption (after boot or an
         * update, with battery optimisation off, or while an overlay shows); a refusal is logged, not thrown.
         */
        fun start(context: Context) {
            if (!Permissions.notificationAccess(context)) return
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
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
