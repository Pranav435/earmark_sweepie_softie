package com.sweepiesoftie.earmark.listener

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.core.Message
import com.sweepiesoftie.earmark.core.RemovalReason
import com.sweepiesoftie.earmark.core.Signal
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

/**
 * Turns a StatusBarNotification into a plain [Signal] for the core, and keeps its live
 * intents in the [WayBackRegistry]. Conversation keys are salted and hashed here, before
 * anything can reach Room (ADR 0004).
 */
class NotificationReader(context: Context, private val wayBacks: WayBackRegistry, private val log: DebugLog) {
    private val ownPackage = context.packageName
    private val salt: String = run {
        val prefs = context.getSharedPreferences("install", Context.MODE_PRIVATE)
        prefs.getString(SALT, null) ?: ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex().also {
            prefs.edit().putString(SALT, it).apply()
        }
    }

    fun read(sbn: StatusBarNotification): Signal? = try {
        build(sbn)
    } catch (e: RuntimeException) {
        // A malformed extras bundle from another app must not take the listener down.
        log.error("NotificationReader(${sbn.packageName})", e)
        null
    }

    fun isNavigation(sbn: StatusBarNotification): Boolean =
        sbn.isOngoing && sbn.notification.category == Notification.CATEGORY_NAVIGATION

    fun forget(key: String) = wayBacks.remove(key)

    private fun build(sbn: StatusBarNotification): Signal {
        val n = sbn.notification
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val user = style?.user
        val messages = style?.messages.orEmpty().takeLast(MAX_MESSAGES).mapNotNull { m ->
            val text = m.text?.toString() ?: return@mapNotNull null
            val fromUser = m.person == null || (user?.key != null && m.person?.key == user.key) ||
                (m.person?.key == null && m.person?.name != null && m.person?.name == user?.name)
            Message(
                sender = m.person?.name?.toString(),
                text = text,
                at = Instant.ofEpochMilli(m.timestamp),
                fromUser = fromUser,
                isBot = m.person?.isBot == true,
            )
        }
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val reply = n.actions?.firstOrNull { action ->
            action.remoteInputs?.any { it.allowFreeFormInput } == true &&
                (action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY || action.semanticAction == Notification.Action.SEMANTIC_ACTION_NONE)
        }
        val lastSender = messages.lastOrNull { !it.fromUser }?.sender
        val conversation = n.shortcutId ?: style?.conversationTitle?.toString() ?: lastSender ?: sbn.tag ?: sbn.id.toString()
        val userName = user?.name?.toString()
        val lastText = messages.lastOrNull()?.text.orEmpty()

        wayBacks.put(
            sbn.key,
            WayBack(
                packageName = sbn.packageName,
                contentIntent = n.contentIntent,
                reply = reply,
                messages = messages.map { RecentMessage(it.text, it.at.toEpochMilli(), it.fromUser, it.sender ?: "them") },
            ),
        )
        return Signal(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = sbn.packageName,
            postedAt = Instant.ofEpochMilli(sbn.postTime),
            title = title,
            text = text,
            messages = messages,
            conversationKey = hash(sbn.packageName + "|" + conversation),
            isGroupConversation = style?.isGroupConversation == true,
            mentionsUser = userName != null && lastText.contains("@$userName", ignoreCase = true),
            hasReplyAction = reply != null,
            isOngoing = sbn.isOngoing,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOwn = sbn.packageName == ownPackage,
            category = n.category,
        )
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + value).toByteArray()).toHex().take(24)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val SALT = "salt"
        private const val MAX_MESSAGES = 10

        fun reasonOf(reason: Int): RemovalReason = when (reason) {
            NotificationListenerService.REASON_CLICK -> RemovalReason.Click
            NotificationListenerService.REASON_APP_CANCEL, NotificationListenerService.REASON_APP_CANCEL_ALL -> RemovalReason.AppCancel
            NotificationListenerService.REASON_CANCEL, NotificationListenerService.REASON_CANCEL_ALL -> RemovalReason.UserDismiss
            else -> RemovalReason.Other
        }
    }
}
