package com.sweepiesoftie.earmark.listener

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.core.Route

/**
 * Carries out a hand-back. It must run while the pill or chip window is still attached:
 * since Android 15 an overlay app may only start activities while one of its overlays is visible.
 */
class HandBack(private val context: Context, private val wayBacks: WayBackRegistry, private val log: DebugLog) {

    fun open(route: Route) {
        when (route) {
            is Route.Notification -> {
                val intent = wayBacks[route.key]?.contentIntent
                if (intent == null || !send(intent)) {
                    log.add("hand-back: tap action missing or cancelled, opening ${route.packageName} instead")
                    launch(route.packageName)
                }
            }
            is Route.LaunchApp -> launch(route.packageName)
            is Route.SetAlarm -> start(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, route.time.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, route.time.minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
            )
            is Route.OpenUri -> start(Intent(Intent.ACTION_VIEW, Uri.parse(route.uri)))
        }
    }

    /** Sends [text] through the notification's own reply action. Returns false if the action is gone. */
    fun reply(key: String, text: String): Boolean {
        val action = wayBacks[key]?.reply ?: return false.also { log.add("reply: no reply action in memory") }
        val inputs = action.remoteInputs ?: return false
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        val fill = Intent()
        RemoteInput.addResultsToIntent(inputs, fill, results)
        return try {
            action.actionIntent.send(context, 0, fill)
            true
        } catch (e: PendingIntent.CanceledException) {
            log.error("reply send", e)
            false
        }
    }

    private fun send(intent: PendingIntent): Boolean = try {
        intent.send(context, 0, null, null, null, null, backgroundStart())
        true
    } catch (e: PendingIntent.CanceledException) {
        log.error("hand-back send", e)
        false
    }

    private fun launch(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            log.add("hand-back: no launch intent for $packageName")
            return
        }
        start(intent)
    }

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), backgroundStart())
        } catch (e: ActivityNotFoundException) {
            log.error("hand-back start", e)
        } catch (e: SecurityException) {
            log.error("hand-back start", e)
        }
    }

    /** Opts this send into the overlay's background-start privilege (API 34+). */
    private fun backgroundStart(): Bundle {
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        return options.toBundle()
    }
}
