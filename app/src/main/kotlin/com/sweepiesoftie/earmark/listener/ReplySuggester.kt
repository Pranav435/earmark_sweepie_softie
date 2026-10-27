package com.sweepiesoftie.earmark.listener

import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplyGenerator
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.core.QuickReplies

/**
 * ML Kit Smart Reply with the bundled model: on-device, English, up to 3 replies from the last ≤ 10 messages.
 * When it can't answer the language, or can't run, the strip offers quick replies from the message's words
 * instead (ADR 0026). When it stays quiet because it isn't confident, which includes sensitive topics, so does the strip.
 */
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
        val messages = wayBacks[notificationKey]?.messages.orEmpty().takeLast(10)
        if (messages.isEmpty()) return onResult(emptyList()).also { log.add("smart reply: no strip, the notification has no messages") }
        if (messages.last().fromUser) return onResult(emptyList()).also { log.add("smart reply: no strip, the last message is yours") }
        val lastText = messages.last().text
        val client = client ?: return onResult(quickReplies(lastText, "Smart Reply unavailable"))
        val conversation = messages.map {
            if (it.fromUser) TextMessage.createForLocalUser(it.text, it.atMillis)
            else TextMessage.createForRemoteUser(it.text, it.atMillis, it.senderKey)
        }
        try {
            client.suggestReplies(conversation)
                .addOnSuccessListener { result ->
                    if (result.status == SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE) {
                        onResult(quickReplies(lastText, statusName(result.status)))
                    } else {
                        val replies = if (result.status == SmartReplySuggestionResult.STATUS_SUCCESS) result.suggestions.map { it.text }.take(3) else emptyList()
                        log.add("smart reply: ${replies.size} suggestions (${statusName(result.status)})")
                        onResult(replies)
                    }
                }
                .addOnFailureListener { e ->
                    log.error("smart reply", e)
                    onResult(quickReplies(lastText, "Smart Reply failed"))
                }
        } catch (e: RuntimeException) {
            log.error("smart reply", e)
            onResult(quickReplies(lastText, "Smart Reply failed"))
        }
    }

    private fun quickReplies(text: String, why: String): List<String> =
        QuickReplies.forMessage(text).also { log.add("smart reply: 0 suggestions ($why); quick replies instead") }

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
