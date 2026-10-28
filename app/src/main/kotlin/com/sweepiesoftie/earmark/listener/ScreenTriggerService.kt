package com.sweepiesoftie.earmark.listener

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.HandlerThread
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.sweepiesoftie.earmark.AppGraph
import com.sweepiesoftie.earmark.core.Event
import com.sweepiesoftie.earmark.core.Watch
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Screen reading for screen triggers (ADR 0038), off until you turn it on in Accessibility. Android lets it see every
 * app, but its package filter, set again whenever the triggers change, sends it only apps with a screen trigger on. It
 * reads them at most every [READ_EVERY_MILLIS] while their window changes, on a thread of its own, and hands the
 * engine the window's visible text, which is matched against the trigger's words and dropped. Nothing it reads is
 * stored or logged.
 */
class ScreenTriggerService : AccessibilityService() {
    private val thread = HandlerThread("screen-reading").apply { start() }
    private val handler = Handler(thread.looper)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The app whose window changed since the last read; set on the main thread, taken on the reading one. */
    private val pending = AtomicReference<String?>(null)
    private val read = Runnable { readNow() }

    @Volatile
    private var watched: Set<String> = emptySet()

    override fun onServiceConnected() {
        AppGraph.init(this)
        AppGraph.debugLog.add("screen triggers connected")
        scope.launch {
            AppGraph.settings.state
                .map { settings -> settings.triggers.filter { it.on && it.watch == Watch.ScreenWords }.map { it.packageName }.toSet() }
                .distinctUntilChanged()
                .collect(::watch)
        }
    }

    /**
     * Only [apps]' events reach the service. With none, its own package stands in: an empty filter would mean every
     * app's.
     */
    private fun watch(apps: Set<String>) {
        watched = apps
        try {
            serviceInfo = serviceInfo?.apply { packageNames = apps.ifEmpty { setOf(packageName) }.toTypedArray() } ?: return
        } catch (e: RuntimeException) {
            // The filter only narrows events; [watched] still keeps reading to the apps with a screen trigger.
            AppGraph.debugLog.error("screen triggers' package filter", e)
            return
        }
        AppGraph.debugLog.add("screen triggers watch ${apps.size} app(s)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in watched) return
        // Throttled rather than debounced: a screen that keeps changing would otherwise never be read.
        if (pending.getAndSet(packageName) == null) handler.postDelayed(read, READ_EVERY_MILLIS)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(read)
        pending.set(null)
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacks(read)
        thread.quitSafely()
        AppGraph.debugLog.add("screen triggers disconnected")
        super.onDestroy()
    }

    private fun readNow() {
        val packageName = pending.getAndSet(null) ?: return
        val root = try {
            rootInActiveWindow
        } catch (e: RuntimeException) {
            AppGraph.debugLog.error("rootInActiveWindow", e)
            null
        } ?: return
        // A keyboard or the shade can be the active window while the app's own events still arrive.
        if (root.packageName?.toString() != packageName) return
        val text = StringBuilder()
        try {
            collect(root, text, intArrayOf(MAX_NODES))
        } catch (e: RuntimeException) {
            // A window can go mid-walk; a failed read is skipped, never thrown.
            AppGraph.debugLog.error("read the screen", e)
            return
        }
        AppGraph.uiEvents.tryEmit(Event.ScreenRead(packageName, text.toString()))
    }

    /** The visible text and labels, top to bottom, within a budget of nodes so a huge list can't hold up the next read. */
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
