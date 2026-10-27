package com.sweepiesoftie.earmark

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** A ring buffer of engine decisions and adapter failures. Lines never carry message text. */
class DebugLog(private val capacity: Int = 400) {
    data class Line(val atMillis: Long, val text: String)

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines

    fun add(text: String) {
        Log.d(TAG, text)
        val line = Line(System.currentTimeMillis(), text)
        _lines.update { (it + line).takeLast(capacity) }
    }

    fun error(where: String, e: Throwable): Unit = add("$where failed: ${e.javaClass.simpleName}")

    fun clear(): Unit = _lines.update { emptyList() }

    private companion object {
        const val TAG = "Earmark"
    }
}
