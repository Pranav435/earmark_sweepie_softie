package com.sweepiesoftie.earmark.demo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.sweepiesoftie.earmark.AppGraph
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.listener.Permissions

/**
 * Posts real notifications that go through the real listener (ADR 0006): Rahul's MessagingStyle
 * message with a reply action, and Aisha's payment request.
 */
object DemoNotifier {
    const val KEY_REPLY = "reply"
    private const val CHANNEL = "demo"
    private const val ID_RAHUL = 1
    private const val ID_AISHA = 2
    private val rahul = Person.Builder().setName("Rahul").setKey("rahul").build()
    private val you = Person.Builder().setName("You").setKey("you").build()
    private val handler = Handler(Looper.getMainLooper())

    fun postRahul(context: Context) {
        AppGraph.demo.startRahul(System.currentTimeMillis())
        notifyRahul(context, alert = true)
    }

    /** Like WhatsApp and Messages, opening the chat clears its notification. */
    fun chatOpened(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_RAHUL)
    }

    /** A reply from the strip or the chat: the conversation updates with your message, which closes the loop. */
    fun reply(context: Context, text: String) {
        val demo = AppGraph.demo
        demo.add(ChatLine(text, fromUser = true, atMillis = System.currentTimeMillis()))
        // A chat app only updates a notification that is still showing.
        val showing = context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == ID_RAHUL }
        if (showing) notifyRahul(context, alert = false)
        handler.postDelayed({
            NotificationManagerCompat.from(context).cancel(ID_RAHUL)
            demo.add(ChatLine("perfect, see you there", fromUser = false, atMillis = System.currentTimeMillis()))
        }, ANSWER_DELAY_MILLIS)
    }

    fun postAisha(context: Context) {
        AppGraph.demo.setAishaPaid(false)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_glyph)
            .setContentTitle("Aisha requested \$12.00")
            .setContentText("Dinner at Nori")
            .setContentIntent(open(context, DemoActivity.SCREEN_PAY, ID_AISHA))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        post(context, ID_AISHA, n)
    }

    /** Paying clears the request, which is the evidence that closes the Payment loop. */
    fun payAisha(context: Context) {
        AppGraph.demo.setAishaPaid(true)
        NotificationManagerCompat.from(context).cancel(ID_AISHA)
    }

    fun canPost(context: Context): Boolean = Permissions.postNotifications(context)

    private fun notifyRahul(context: Context, alert: Boolean) {
        val style = NotificationCompat.MessagingStyle(you)
        AppGraph.demo.rahul.value.forEach { line ->
            style.addMessage(NotificationCompat.MessagingStyle.Message(line.text, line.atMillis, if (line.fromUser) null else rahul))
        }
        val replyInput = RemoteInput.Builder(KEY_REPLY).setLabel(context.getString(R.string.demo_reply_label)).build()
        // RemoteInput needs a mutable PendingIntent to carry the typed text; the intent is explicit (ADR 0012).
        val replyIntent = PendingIntent.getBroadcast(
            context,
            ID_RAHUL,
            Intent(context, DemoReplyReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val reply = NotificationCompat.Action.Builder(R.drawable.ic_glyph, context.getString(R.string.demo_reply_label), replyIntent)
            .addRemoteInput(replyInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_glyph)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open(context, DemoActivity.SCREEN_CHAT, ID_RAHUL))
            .addAction(reply)
            .setOnlyAlertOnce(!alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        post(context, ID_RAHUL, n)
    }

    private fun open(context: Context, screen: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, DemoActivity::class.java).putExtra(DemoActivity.EXTRA_SCREEN, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun post(context: Context, id: Int, n: android.app.Notification) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.demo_channel), NotificationManager.IMPORTANCE_HIGH))
        if (!canPost(context)) {
            AppGraph.debugLog.add("demo: notification permission missing, nothing posted")
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            AppGraph.debugLog.error("demo notify", e)
        }
    }

    private const val ANSWER_DELAY_MILLIS = 1_500L
}
