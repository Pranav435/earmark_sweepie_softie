package com.sweepiesoftie.earmark.listener

import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplyGenerator
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import com.sweepiesoftie.earmark.DebugLog

/** ML Kit Smart Reply with the bundled model: on-device, English, up to 3 replies from the last ≤ 10 messages. */
class ReplySuggester(private val wayBacks: WayBackRegistry, private val log: DebugLog) {
    /** Built on first use and guarded, so an ML Kit failure only hides the reply strip. */
    private val client: SmartReplyGenerator? by lazy {
        try {
            SmartReply.getClient()
        } catch (e: RuntimeException) {
            log.error("smart reply client", e)
            null
        }
    }
    private var started = false

    fun suggest(notificationKey: String, onResult: (List<String>) -> Unit) {
        started = true
        val client = client ?: return onResult(emptyList())
        val messages = wayBacks[notificationKey]?.messages.orEmpty().takeLast(10)
        if (messages.isEmpty()) return onResult(emptyList()).also { log.add("smart reply: no strip, the notification has no messages") }
        if (messages.last().fromUser) return onResult(emptyList()).also { log.add("smart reply: no strip, the last message is yours") }
        val conversation = messages.map {
            if (it.fromUser) TextMessage.createForLocalUser(it.text, it.atMillis)
            else TextMessage.createForRemoteUser(it.text, it.atMillis, it.senderKey)
        }
        try {
            client.suggestReplies(conversation)
                .addOnSuccessListener { result ->
                    val replies = if (result.status == SmartReplySuggestionResult.STATUS_SUCCESS) result.suggestions.map { it.text }.take(3) else emptyList()
                    log.add("smart reply: ${replies.size} suggestions (${statusName(result.status)})")
                    onResult(replies)
                }
                .addOnFailureListener { e ->
                    log.error("smart reply", e)
                    onResult(emptyList())
                }
        } catch (e: RuntimeException) {
            log.error("smart reply", e)
            onResult(emptyList())
        }
    }

    fun close() {
        if (started) client?.close()
    }

    /** ML Kit only answers in English, and stays quiet when it isn't confident or the topic is sensitive. */
    private fun statusName(status: Int): String = when (status) {
        SmartReplySuggestionResult.STATUS_SUCCESS -> "ok"
        SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE -> "not English"
        SmartReplySuggestionResult.STATUS_NO_REPLY -> "no confident reply"
        else -> "status $status"
    }
}
