package com.sweepiesoftie.earmark.demo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ChatLine(val text: String, val fromUser: Boolean, val atMillis: Long)

/** The built-in demo chat with Rahul and Aisha's request, held in memory like any message text. */
class DemoChats {
    private val _rahul = MutableStateFlow<List<ChatLine>>(emptyList())
    val rahul: StateFlow<List<ChatLine>> = _rahul

    private val _aishaPaid = MutableStateFlow(false)
    val aishaPaid: StateFlow<Boolean> = _aishaPaid

    fun startRahul(now: Long) {
        _rahul.value = listOf(
            ChatLine("did you see the match??", fromUser = false, atMillis = now - DAY + 60_000),
            ChatLine("that last over was unreal", fromUser = true, atMillis = now - DAY + 180_000),
            ChatLine("free at 5?", fromUser = false, atMillis = now),
        )
    }

    fun add(line: ChatLine) = _rahul.update { it + line }

    fun setAishaPaid(paid: Boolean) {
        _aishaPaid.value = paid
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
