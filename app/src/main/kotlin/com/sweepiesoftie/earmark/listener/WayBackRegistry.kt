package com.sweepiesoftie.earmark.listener

import android.app.Notification
import android.app.PendingIntent
import android.graphics.drawable.Icon
import java.util.concurrent.ConcurrentHashMap

/** One message kept for Smart Reply. Memory only (ADR 0004). */
data class RecentMessage(val text: String, val atMillis: Long, val fromUser: Boolean, val senderKey: String)

/**
 * The live route back into a notification: its tap action, its reply action and its last ≤ 10 messages. [face]
 * is the sender's photo the app put in the notification, for the chip and pill (ADR 0032); memory only, like the text.
 */
class WayBack(
    val packageName: String,
    val contentIntent: PendingIntent?,
    val reply: Notification.Action?,
    val messages: List<RecentMessage>,
    val face: Icon? = null,
)

/** In-memory only, keyed by notification key. Lost on reboot, which is why HandBack falls back to the launch intent. */
class WayBackRegistry {
    private val wayBacks = ConcurrentHashMap<String, WayBack>()

    operator fun get(key: String): WayBack? = wayBacks[key]

    fun put(key: String, wayBack: WayBack) {
        wayBacks[key] = wayBack
    }

    fun remove(key: String) {
        wayBacks.remove(key)
    }

    /** Removes [key] only while it still maps to [expected], so a notification posted again under the same key keeps its new way back. */
    fun remove(key: String, expected: WayBack) {
        wayBacks.remove(key, expected)
    }

    fun clear() {
        wayBacks.clear()
    }
}
