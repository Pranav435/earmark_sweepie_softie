package com.sweepiesoftie.earmark.bench

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Asks Smart Reply for replies to every benchmark message that isn't only an acknowledgement, the way ReplySuggester
 * does, as a one-message conversation from someone else. Writes files/smart-reply.json, which `run-as` can read.
 */
class SmartReplyBench : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val messages = JSONArray(context.assets.open("messages.json").bufferedReader().use { it.readText() })
            val client = SmartReply.getClient()
            val out = JSONArray()
            for (i in 0 until messages.length()) {
                val message = messages.getJSONObject(i)
                if (message.optBoolean("ack")) continue
                val conversation = listOf(TextMessage.createForRemoteUser(message.getString("text"), System.currentTimeMillis(), "them"))
                val row = JSONObject().put("id", message.getString("id"))
                try {
                    val replies = Tasks.await(client.suggestReplies(conversation))
                    row.put("status", statusName(replies.status)).put("replies", JSONArray(replies.suggestions.map { it.text }.take(3)))
                } catch (e: Exception) {
                    row.put("status", "failed").put("replies", JSONArray())
                }
                out.put(row)
            }
            client.close()
            File(context.filesDir, "smart-reply.json").writeText(out.toString(2))
            result.putString(Instrumentation.REPORT_KEY_STREAMRESULT, "Smart Reply: ${out.length()} messages\n")
            finish(Activity.RESULT_OK, result)
        } catch (e: Exception) {
            result.putString(Instrumentation.REPORT_KEY_STREAMRESULT, e.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    /** The same names ReplySuggester logs. */
    private fun statusName(status: Int): String = when (status) {
        SmartReplySuggestionResult.STATUS_SUCCESS -> "ok"
        SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE -> "not English"
        SmartReplySuggestionResult.STATUS_NO_REPLY -> "no confident reply"
        else -> "status $status"
    }
}
