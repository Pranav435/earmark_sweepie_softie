package com.sweepiesoftie.earmark.listener

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.core.GroupRemovals
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
            prefs.edit { putString(SALT, it) }
        }
    }

    fun read(sbn: StatusBarNotification): Signal? = try {
        groups.posted(sbn.key, sbn.groupKey, sbn.isGroupSummary())
        build(sbn)
    } catch (e: RuntimeException) {
        // A malformed extras bundle from another app must not take the listener down.
        log.error("NotificationReader(${sbn.packageName})", e)
        null
    }

    fun isNavigation(sbn: StatusBarNotification): Boolean =
        sbn.isOngoing && sbn.notification.category == Notification.CATEGORY_NAVIGATION

    fun forget(key: String): Unit = wayBacks.remove(key)

    /** Which chats each group is showing, and the reason they take when its summary goes. Main thread only. */
    private val groups = GroupRemovals()

    /**
     * Android removes a summary's children with REASON_GROUP_SUMMARY_CANCELED, right after the summary
     * itself, so a child takes the summary's reason: a chat app clearing its group still counts as a clear.
     * A tap on a group of several chats only opens the app, so they count as cleared by it (ADR 0026).
     * Call once per removal: it also takes the chat off its group's count.
     */
    fun reasonOf(sbn: StatusBarNotification, reason: Int): RemovalReason = groups.removed(
        key = sbn.key,
        groupKey = sbn.groupKey,
        isSummary = sbn.isGroupSummary(),
        reason = reasonOf(reason),
        bySummary = reason == NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED,
    )

    private fun StatusBarNotification.isGroupSummary(): Boolean = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

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
            conversationTitle = style?.conversationTitle?.toString(),
            // Whether a message mentions you is the core's call (ADR 0028); it needs the name the app uses for you.
            selfName = user?.name?.toString(),
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
