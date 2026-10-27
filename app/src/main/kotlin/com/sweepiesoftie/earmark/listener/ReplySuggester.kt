package com.sweepiesoftie.earmark.listener

import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import com.sweepiesoftie.earmark.DebugLog

/** ML Kit Smart Reply with the bundled model: on-device, English, up to 3 replies from the last ≤ 10 messages. */
class ReplySuggester(private val wayBacks: WayBackRegistry, private val log: DebugLog) {
    private val client = SmartReply.getClient()

    fun suggest(notificationKey: String, onResult: (List<String>) -> Unit) {
        val messages = wayBacks[notificationKey]?.messages.orEmpty().takeLast(10)
        if (messages.isEmpty() || messages.last().fromUser) return onResult(emptyList())
        val conversation = messages.map {
            if (it.fromUser) TextMessage.createForLocalUser(it.text, it.atMillis)
            else TextMessage.createForRemoteUser(it.text, it.atMillis, it.senderKey)
        }
        try {
            client.suggestReplies(conversation)
                .addOnSuccessListener { result ->
                    val replies = if (result.status == SmartReplySuggestionResult.STATUS_SUCCESS) result.suggestions.map { it.text }.take(3) else emptyList()
                    log.add("smart reply: ${replies.size} suggestions (status ${result.status})")
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

    fun close(): Unit = client.close()
}
