package com.sweepiesoftie.earmark.listener

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.sweepiesoftie.earmark.AppGraph
import com.sweepiesoftie.earmark.core.Event
import com.sweepiesoftie.earmark.core.Watch

/**
 * Screen reading for screen triggers (ADR 0038), off until you turn it on in Accessibility. Android lets it see every app, but it reads only apps with
 * a screen trigger on, at most every [READ_EVERY_MILLIS] while their window changes, and hands the engine the
 * window's visible text, which is matched against the trigger's words and dropped. Nothing it reads is stored or logged.
 */
class ScreenTriggerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: String? = null
    private val read = Runnable { readNow() }

    override fun onServiceConnected() {
        AppGraph.init(this)
        AppGraph.debugLog.add("screen triggers connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in watched()) return
        // Throttled rather than debounced: a screen that keeps changing would otherwise never be read.
        if (pending == null) handler.postDelayed(read, READ_EVERY_MILLIS)
        pending = packageName
    }

    override fun onInterrupt() {
        handler.removeCallbacks(read)
        pending = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(read)
        AppGraph.debugLog.add("screen triggers disconnected")
        super.onDestroy()
    }

    private fun watched(): Set<String> =
        AppGraph.settings.state.value.triggers.filter { it.on && it.watch == Watch.ScreenWords }.map { it.packageName }.toSet()

    private fun readNow() {
        val packageName = pending ?: return
        pending = null
        val root = try {
            rootInActiveWindow
        } catch (e: RuntimeException) {
            AppGraph.debugLog.error("rootInActiveWindow", e)
            null
        } ?: return
        // A keyboard or the shade can be the active window while the app's own events still arrive.
        if (root.packageName?.toString() != packageName) return
        val text = StringBuilder()
        collect(root, text, intArrayOf(MAX_NODES))
        AppGraph.uiEvents.tryEmit(Event.ScreenRead(packageName, text.toString()))
    }

    /** The visible text and labels, top to bottom, within a budget of nodes so a huge list can't stall the main thread. */
    private fun collect(node: AccessibilityNodeInfo, out: StringBuilder, budget: IntArray) {
        if (budget[0]-- <= 0 || out.length > MAX_CHARS || !node.isVisibleToUser) return
        node.text?.let { out.append(it).append('\n') }
        node.contentDescription?.takeIf { it != node.text }?.let { out.append(it).append('\n') }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, out, budget) }
    }

    private companion object {
        const val READ_EVERY_MILLIS = 400L
        const val MAX_NODES = 1_500
        const val MAX_CHARS = 20_000
    }
}
