package com.sweepiesoftie.earmark.bench

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.sweepiesoftie.earmark.core.Engine
import com.sweepiesoftie.earmark.core.Event
import com.sweepiesoftie.earmark.core.Message
import com.sweepiesoftie.earmark.core.Settings
import com.sweepiesoftie.earmark.core.Signal
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Times Earmark's engine on the phone: every benchmark notification, then every benchmark message as a WhatsApp chat,
 * posted to [Engine.handle] as the listener posts them. The first notification on a fresh engine includes loading the
 * message model. Writes files/engine-timing.json, which `run-as` can read.
 */
class EngineTiming : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val settings = Settings(ownPackage = "com.sweepiesoftie.earmark", zone = ZoneId.of("Asia/Kolkata"), myNames = listOf("Pranav Savla", "Pranav"))
            val now = Instant.parse("2026-10-13T10:30:00Z")
            val notifications = array("notifications.json").map { signal(it.getJSONObject("notification"), "k-${it.getString("id")}", now) }
            val messages = array("messages.json").map { chat(it.getString("id"), it.getString("text"), now) }

            val coldStart = System.nanoTime()
            val engine = Engine(settings)
            engine.handle(Event.NotificationPosted(notifications.first()), now)
            val coldMillis = (System.nanoTime() - coldStart) / 1e6

            fun perItem(signals: List<Signal>): Double {
                repeat(3) { Engine(settings).let { e -> signals.forEach { e.handle(Event.NotificationPosted(it), now) } } }
                val start = System.nanoTime()
                repeat(5) { Engine(settings).let { e -> signals.forEach { e.handle(Event.NotificationPosted(it), now) } } }
                return (System.nanoTime() - start) / 1000.0 / (5 * signals.size)
            }
            val out = JSONObject()
                .put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                .put("coldFirstNotificationMillis", coldMillis)
                .put("microsecondsPerNotification", perItem(notifications))
                .put("microsecondsPerMessage", perItem(messages))
                .put("messageModel", modelLoaded(engine))
            File(context.filesDir, "engine-timing.json").writeText(out.toString(2))
            result.putString(Instrumentation.REPORT_KEY_STREAMRESULT, "$out\n")
            finish(Activity.RESULT_OK, result)
        } catch (e: Exception) {
            result.putString(Instrumentation.REPORT_KEY_STREAMRESULT, e.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    /** v0.1.14's engine has no model to report. */
    private fun modelLoaded(engine: Engine): Any =
        runCatching { Engine::class.java.getMethod("getMessageModel").invoke(engine) }.getOrDefault("none")

    private fun array(name: String): List<JSONObject> =
        JSONArray(context.assets.open(name).bufferedReader().use { it.readText() }).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    private fun chat(id: String, text: String, now: Instant) = Signal(
        key = "m-$id", packageName = "com.whatsapp", appLabel = "WhatsApp", postedAt = now, title = "Rahul", text = text,
        messages = listOf(Message("Rahul", text, now)), conversationKey = "com.whatsapp|$id", hasReplyAction = true, category = "msg",
    )

    /** As the benchmark builds one (core's Benchmark.kt): a message with no sender is yours. */
    private fun signal(n: JSONObject, key: String, now: Instant): Signal {
        val list = n.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until list.length()).map { list.getJSONObject(it) }.map { m ->
            val sender = m.optString("sender").takeIf { !m.isNull("sender") && it.isNotEmpty() }
            Message(sender, m.optString("text"), now.minusSeconds((m.optDouble("minutesAgo", 0.0) * 60).toLong()), m.optBoolean("fromUser") || sender == null, m.optBoolean("isBot"))
        }
        val str = { name: String -> n.optString(name).takeIf { !n.isNull(name) && it.isNotEmpty() } }
        val app = n.getString("app")
        val conversation = str("groupName") ?: messages.lastOrNull { !it.fromUser }?.sender ?: str("title").takeIf { str("category") in setOf("email", "missed_call") } ?: key
        return Signal(
            key = key, packageName = app, appLabel = str("appLabel") ?: app, postedAt = now, title = str("title"), text = str("text"),
            bigText = str("bigText"), messages = messages, conversationKey = "$app|$conversation", isGroupConversation = n.optBoolean("isGroup"),
            conversationTitle = str("groupName"), selfName = str("selfName"), hasReplyAction = n.optBoolean("hasReplyAction"),
            isOngoing = n.optBoolean("isOngoing"), isGroupSummary = n.optBoolean("isGroupSummary"), category = str("category"),
        )
    }
}
