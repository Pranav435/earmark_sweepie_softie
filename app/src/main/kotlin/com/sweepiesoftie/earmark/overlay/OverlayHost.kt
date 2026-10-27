package com.sweepiesoftie.earmark.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.core.ChipCard
import com.sweepiesoftie.earmark.core.Event
import com.sweepiesoftie.earmark.core.LoopId
import com.sweepiesoftie.earmark.core.Overlay
import com.sweepiesoftie.earmark.core.PillCard
import com.sweepiesoftie.earmark.listener.Permissions
import com.sweepiesoftie.earmark.ui.theme.EarmarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ReplyStrip(val loopId: LoopId, val to: String, val replies: List<String>)

/** Everything the overlay windows draw. Each window is a stateless function of this. */
data class OverlayUi(
    val chip: ChipCard? = null,
    val chipTimeoutMillis: Long = 8_000,
    val pill: PillCard? = null,
    val dot: Boolean = false,
    val strip: ReplyStrip? = null,
    /** TalkBack is reading the chip, so its fade timer waits. */
    val a11yFocused: Boolean = false,
    val reduceMotion: Boolean = false,
)

/**
 * Owns the overlay windows. Each window is created once and reused: it is attached while it has
 * something to show and detached after its exit animation, so Android's "displaying over other
 * apps" notice only appears while Earmark is actually on screen. Main thread only.
 */
class OverlayHost(private val context: Context, private val log: DebugLog, private val dispatch: (Event) -> Unit) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val a11y = context.getSystemService(AccessibilityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private val owner = OverlayOwner()
    private val shoulderTap = ShoulderTap(context, log)
    private val _ui = MutableStateFlow(OverlayUi())
    val ui: StateFlow<OverlayUi> = _ui

    private var settleUntilMillis = 0L
    private val noFrameWork = Choreographer.FrameCallback { }

    /**
     * On a Galaxy A34 (One UI, Android 16), once Earmark's own screen had been opened and left, the
     * frames that drew an overlay skipped every frame callback, Compose's among them, and nothing
     * asked for another. The card stayed at its first, empty frame until Earmark was opened again
     * (ADR 0022). A fresh frame request from a handler message gets a frame that runs them, so ask
     * for one every few frames while a change settles.
     */
    private val settle = object : Runnable {
        override fun run() {
            choreographer.removeFrameCallback(noFrameWork)
            choreographer.postFrameCallback(noFrameWork)
            if (SystemClock.uptimeMillis() < settleUntilMillis) handler.postDelayed(this, SETTLE_STEP_MILLIS)
        }
    }

    private fun keepFramesComing() {
        settleUntilMillis = SystemClock.uptimeMillis() + SETTLE_MILLIS
        handler.removeCallbacks(settle)
        handler.post(settle)
    }

    /** Set at unlock so the log can show how long the chip took to appear. */
    var unlockedAtMillis: Long = 0L

    private val top = Window("Earmark", topParams(), onA11yFocus = { focused -> _ui.update { it.copy(a11yFocused = focused) } }) {
        val state by ui.collectAsState()
        TopOverlay(
            state = state,
            onChipAct = { dispatch(Event.ChipActed(it)) },
            onChipDismiss = { dispatch(Event.ChipDismissed(it)) },
            onChipFaded = ::fadeChip,
            onPillAct = { dispatch(Event.PillTapped(it)) },
            onPillDismiss = { dispatch(Event.PillDismissed(it)) },
        )
    }
    private val dot = Window("Earmark dot", dotParams()) {
        val state by ui.collectAsState()
        DotOverlay(visible = state.dot, reduceMotion = state.reduceMotion)
    }
    private val strip = Window("Earmark replies", stripParams()) {
        val state by ui.collectAsState()
        ReplyStripOverlay(
            strip = state.strip,
            reduceMotion = state.reduceMotion,
            onReply = { id, text -> dispatch(Event.ReplyChosen(id, text)) },
            onClose = { hide(Overlay.ReplyStrip) },
        )
    }

    fun showChip(chip: ChipCard) {
        val timeout = a11y.getRecommendedTimeoutMillis(CHIP_MILLIS, AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS).toLong()
        _ui.update { it.copy(chip = chip, pill = null, chipTimeoutMillis = timeout, reduceMotion = reduceMotion(), a11yFocused = false) }
        if (top.attach() && unlockedAtMillis > 0) {
            log.add("chip attached ${SystemClock.elapsedRealtime() - unlockedAtMillis} ms after unlock")
            unlockedAtMillis = 0
        }
        keepFramesComing()
    }

    fun showPill(pill: PillCard) {
        val arriving = _ui.value.pill?.loopId != pill.loopId
        _ui.update { it.copy(pill = pill, chip = null, reduceMotion = reduceMotion()) }
        if (top.attach() && arriving) shoulderTap.tap()
        keepFramesComing()
    }

    fun showDot() {
        _ui.update { it.copy(dot = true) }
        dot.attach()
        keepFramesComing()
    }

    fun showStrip(loopId: LoopId, to: String, replies: List<String>) {
        if (replies.isEmpty()) return
        _ui.update { it.copy(strip = ReplyStrip(loopId, to, replies), reduceMotion = reduceMotion()) }
        strip.attach()
        keepFramesComing()
    }

    fun hide(overlay: Overlay) {
        _ui.update {
            when (overlay) {
                Overlay.Chip -> it.copy(chip = null)
                Overlay.Pill -> it.copy(pill = null)
                Overlay.Dot -> it.copy(dot = false)
                Overlay.ReplyStrip -> it.copy(strip = null)
            }
        }
        val state = _ui.value
        if (state.chip == null && state.pill == null) top.detachLater()
        if (!state.dot) dot.detachLater()
        if (state.strip == null) strip.detachLater()
        keepFramesComing()
    }

    /** A faded chip only leaves the screen; the engine keeps watching its loop and shows the dot (ADR 0007, 0032). */
    private fun fadeChip(id: LoopId) {
        if (_ui.value.chip?.loopId == id) dispatch(Event.ChipFaded(id))
    }

    fun destroy() {
        handler.removeCallbacks(settle)
        choreographer.removeFrameCallback(noFrameWork)
        listOf(top, dot, strip).forEach { it.detachNow() }
        owner.destroy()
    }

    private fun reduceMotion(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private fun statusBarHeight(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top
        } else {
            (24 * context.resources.displayMetrics.density).toInt()
        }

    private fun navigationBarHeight(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            (48 * context.resources.displayMetrics.density).toInt()
        }

    private fun baseParams(width: Int, flags: Int) = WindowManager.LayoutParams(
        width,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or flags,
        PixelFormat.TRANSLUCENT,
    )

    /** Just under the status bar, where a message's heads-up shows (ADR 0032). */
    private fun topParams() = baseParams(WindowManager.LayoutParams.MATCH_PARENT, 0).apply {
        gravity = Gravity.TOP
        y = statusBarHeight()
        ignoreSystemBars()
        title = "Earmark"
    }

    /** Non-touchable at alpha 0.8, the most Android 12+ allows for touches to pass through (ADR 0003). */
    private fun dotParams() = baseParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
        gravity = Gravity.TOP or Gravity.START
        x = (20 * context.resources.displayMetrics.density).toInt()
        y = statusBarHeight() + (4 * context.resources.displayMetrics.density).toInt()
        ignoreSystemBars()
        alpha = 0.8f
        title = "Earmark dot"
    }

    /**
     * Android lays an overlay out below the status bar already, so [statusBarHeight] in y counted it twice and the
     * chip sat a bar's height lower than meant. Measured from the top of the screen, it lands the same on every phone.
     */
    private fun WindowManager.LayoutParams.ignoreSystemBars() {
        flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
    }

    /** Sits just above a chat's input row. */
    private fun stripParams() = baseParams(WindowManager.LayoutParams.MATCH_PARENT, 0).apply {
        gravity = Gravity.BOTTOM
        y = navigationBarHeight() + (72 * context.resources.displayMetrics.density).toInt()
        title = "Earmark replies"
    }

    private inner class Window(
        private val name: String,
        private val params: WindowManager.LayoutParams,
        onA11yFocus: ((Boolean) -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        private val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { EarmarkTheme { content() } }
            if (onA11yFocus != null) accessibilityDelegate = A11yFocusWatch(onA11yFocus)
        }
        private var attached = false
        private val detach = Runnable { detachNow() }

        /** Returns whether the window is on screen. */
        fun attach(): Boolean {
            handler.removeCallbacks(detach)
            if (attached) return true
            if (!Permissions.overlay(context)) {
                log.add("OverlayHost: no overlay permission, $name not shown")
                return false
            }
            try {
                // A detached view misses configuration changes such as a new font scale.
                view.dispatchConfigurationChanged(context.resources.configuration)
                wm.addView(view, params)
                attached = true
            } catch (e: RuntimeException) {
                log.error("addView($name)", e)
            }
            return attached
        }

        fun detachLater() {
            if (!attached) return
            handler.removeCallbacks(detach)
            handler.postDelayed(detach, EXIT_MILLIS)
        }

        fun detachNow() {
            handler.removeCallbacks(detach)
            if (!attached) return
            try {
                wm.removeViewImmediate(view)
            } catch (e: RuntimeException) {
                log.error("removeView($name)", e)
            }
            attached = false
        }
    }

    /**
     * Compose has no "TalkBack is on this node" callback, but its virtual views send their
     * focus events up through the parent, so the window's root sees them. A count, because
     * moving between nodes can deliver the new node's FOCUSED before the old one's CLEARED.
     */
    private class A11yFocusWatch(private val onChange: (Boolean) -> Unit) : View.AccessibilityDelegate() {
        private var focused = 0

        override fun onRequestSendAccessibilityEvent(host: ViewGroup, child: View, event: AccessibilityEvent): Boolean {
            val before = focused > 0
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> focused++
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> focused = (focused - 1).coerceAtLeast(0)
            }
            if ((focused > 0) != before) onChange(focused > 0)
            return super.onRequestSendAccessibilityEvent(host, child, event)
        }
    }

    private companion object {
        const val CHIP_MILLIS = 8_000
        const val EXIT_MILLIS = 320L

        /** The 220 ms entrance plus the slow first frames of a fresh composition, with room to spare. */
        const val SETTLE_MILLIS = 1_000L
        const val SETTLE_STEP_MILLIS = 50L
    }
}
