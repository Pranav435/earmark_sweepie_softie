package com.sweepiesoftie.earmark.listener

import android.app.KeyguardManager
import android.content.ComponentName
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sweepiesoftie.earmark.AppGraph
import com.sweepiesoftie.earmark.ListenerState
import com.sweepiesoftie.earmark.core.Blocker
import com.sweepiesoftie.earmark.core.Effect
import com.sweepiesoftie.earmark.core.Engine
import com.sweepiesoftie.earmark.core.Event
import com.sweepiesoftie.earmark.core.Overlay
import com.sweepiesoftie.earmark.overlay.OverlayHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.Executors

/**
 * The one long-lived component (ADR 0001). While notification access is on, the system keeps
 * it bound, so it hosts the engine, owns the overlay windows and registers the runtime-only
 * broadcasts. Every event goes through [dispatch] onto a single engine thread.
 */
class EarmarkListener : NotificationListenerService() {
    private val graph = AppGraph
    private val log get() = graph.debugLog
    private var session: Session? = null

    /** Everything that lives between connect and disconnect. */
    private inner class Session {
        val engineDispatcher: ExecutorCoroutineDispatcher =
            Executors.newSingleThreadExecutor { Thread(it, "earmark-engine") }.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val engine = Engine(graph.apps.engineSettings(graph.settings.state.value))
        val reader = NotificationReader(this@EarmarkListener, graph.wayBacks, log)
        val overlay = OverlayHost(this@EarmarkListener, log, ::dispatch)
        val handBack = HandBack(this@EarmarkListener, graph.wayBacks, log)
        val suggester = ReplySuggester(graph.wayBacks, log)
        val watcher = ForegroundWatcher(this@EarmarkListener, scope, graph.apps, log, ::dispatch)
        val pause = PauseSensor(this@EarmarkListener, ComponentName(this@EarmarkListener, EarmarkListener::class.java), log, ::dispatch)
        val calendar = CalendarReader(this@EarmarkListener, log)
        val receivers = SystemReceivers(this@EarmarkListener, log, ::onUnlock, ::onScreenOff, ::dispatch)
        var navigating = false
        var lastUnlockMillis = 0L

        fun close() {
            watcher.stop()
            pause.stop()
            receivers.stop()
            overlay.destroy()
            suggester.close()
            scope.cancel()
            engineDispatcher.close()
        }
    }

    override fun onCreate() {
        super.onCreate()
        graph.init(this)
    }

    override fun onListenerConnected() {
        session?.close()
        val s = Session()
        session = s
        graph.listener.value = ListenerState(connected = true)
        log.add("listener connected")
        KeepAliveService.start(this)
        s.receivers.start()
        s.pause.start()
        s.scope.launch {
            dispatch(Event.StatsLoaded(graph.store.stats()))
            graph.store.trim(System.currentTimeMillis())
        }
        s.scope.launch {
            graph.settings.state.drop(1).collect { user ->
                val settings = withContext(Dispatchers.IO) { graph.apps.engineSettings(user) }
                dispatch(Event.SettingsChanged(settings))
            }
        }
        s.scope.launch { graph.uiEvents.collect { dispatch(it) } }

        // After a restart the loops are rebuilt from what is still waiting, with fresh intents. The new engine
        // starts empty, so way backs the old one still held (such as an open chat's) would never be forgotten.
        graph.wayBacks.clear()
        try {
            activeNotifications?.forEach { onNotificationPosted(it) }
        } catch (e: SecurityException) {
            log.error("getActiveNotifications", e)
        }
        dispatch(s.receivers.nextAlarm())
        dispatch(Event.BlockerChanged(Blocker.DoNotDisturb, currentInterruptionFilter > INTERRUPTION_FILTER_ALL))
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (power.isInteractive && !keyguard.isKeyguardLocked) s.watcher.start()
    }

    override fun onListenerDisconnected() {
        log.add("listener disconnected; asking to rebind")
        session?.close()
        session = null
        graph.listener.value = ListenerState()
        stopKeepAliveIfRevoked()
        requestRebind(ComponentName(this, EarmarkListener::class.java))
    }

    override fun onDestroy() {
        session?.close()
        session = null
        graph.listener.value = ListenerState()
        stopKeepAliveIfRevoked()
        super.onDestroy()
    }

    /** With notification access off there is nothing to keep awake, and "Earmark is on" would be wrong. */
    private fun stopKeepAliveIfRevoked() {
        if (!Permissions.notificationAccess(this)) KeepAliveService.stop(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val s = session ?: return
        if (s.reader.isNavigation(sbn) && !s.navigating) {
            s.navigating = true
            dispatch(Event.BlockerChanged(Blocker.Navigation, true))
        }
        val signal = s.reader.read(sbn) ?: return
        dispatch(Event.NotificationPosted(signal.copy(appLabel = graph.apps.label(sbn.packageName))))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        val s = session ?: return
        if (s.reader.isNavigation(sbn) && s.navigating) {
            s.navigating = false
            dispatch(Event.BlockerChanged(Blocker.Navigation, false))
        }
        // The engine forgets the notification's intents and text, unless an open chat still needs them for the reply strip.
        // Only the way back held now is forgotten: the app may post the same key again before the engine answers.
        enqueue(Event.NotificationRemoved(sbn.key, s.reader.reasonOf(sbn, reason)), graph.wayBacks[sbn.key])
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        dispatch(Event.BlockerChanged(Blocker.DoNotDisturb, interruptionFilter > INTERRUPTION_FILTER_ALL))
    }

    private fun onUnlock() {
        val s = session ?: return
        // SCREEN_ON and USER_PRESENT can both arrive for one unlock on phones without a lock screen.
        val now = SystemClock.elapsedRealtime()
        if (now - s.lastUnlockMillis < UNLOCK_DEBOUNCE_MILLIS) return
        s.lastUnlockMillis = now
        s.overlay.unlockedAtMillis = now
        log.add("unlock")
        s.watcher.start()
        graph.appScope.launch { graph.store.trim(System.currentTimeMillis()) }
        s.scope.launch {
            val events = withContext(Dispatchers.IO) { s.calendar.soon() }
            if (events != null) dispatch(Event.CalendarSoon(events))
            dispatch(Event.Unlocked)
        }
    }

    private fun onScreenOff() {
        val s = session ?: return
        log.add("screen off")
        s.watcher.stop()
        dispatch(Event.ScreenOff)
    }

    /** Runs [event] through the engine on its own thread, then carries out the effects in order on the main thread. */
    fun dispatch(event: Event) {
        enqueue(event, removed = null)
    }

    /** [removed] is the way back held when a removal arrived; the engine's Forget for that removal drops only it. */
    private fun enqueue(event: Event, removed: WayBack?) {
        val s = session ?: return
        val removedKey = (event as? Event.NotificationRemoved)?.key
        s.scope.launch(s.engineDispatcher) {
            val effects = s.engine.handle(event, Instant.now())
            val open = s.engine.openLoops
            graph.listener.update { if (session === s) it.copy(openLoops = open) else it }
            if (effects.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    effects.forEach { effect ->
                        if (effect is Effect.Forget && effect.notificationKey == removedKey) {
                            // Nothing held at removal means anything there now was posted since.
                            removed?.let { graph.wayBacks.remove(effect.notificationKey, it) }
                        } else {
                            run(s, effect)
                        }
                    }
                }
            }
        }
    }

    private fun run(s: Session, effect: Effect) {
        when (effect) {
            is Effect.ShowChip -> s.overlay.showChip(effect.chip)
            Effect.ShowDot -> s.overlay.showDot()
            is Effect.ShowPill -> s.overlay.showPill(effect.pill)
            is Effect.HideOverlay -> s.overlay.hide(effect.overlay)
            // Runs before the HideOverlay that follows it, so the window is still attached (Android 15 rule).
            is Effect.OpenWayBack -> s.handBack.open(effect.route)
            is Effect.SuggestReplies -> s.suggester.suggest(effect.notificationKey) { replies ->
                // Replies arrive later; if the loop closed meanwhile, its way back is gone and the strip would be stale.
                if (session === s && graph.wayBacks[effect.notificationKey] != null) s.overlay.showStrip(effect.loopId, effect.to, replies)
            }
            is Effect.SendReply ->
                if (s.handBack.reply(effect.notificationKey, effect.text)) dispatch(Event.ReplySent(effect.loopId)) else s.overlay.hide(Overlay.ReplyStrip)
            is Effect.Forget -> s.reader.forget(effect.notificationKey)
            is Effect.RecordLoop -> graph.appScope.launch { graph.store.record(effect.entry) }
            is Effect.RecordKind -> graph.appScope.launch { graph.store.record(effect.kind, effect.stat) }
            is Effect.RecordContact -> graph.appScope.launch { graph.store.record(effect.conversation, effect.stat) }
            is Effect.RecordApp -> graph.appScope.launch { graph.store.recordApp(effect.packageName, effect.stat) }
            is Effect.RecordAlarm -> graph.appScope.launch { graph.store.recordAlarm(effect.time, effect.on) }
            is Effect.Log -> log.add(effect.line)
        }
    }

    private companion object {
        const val UNLOCK_DEBOUNCE_MILLIS = 2_000L
    }
}
