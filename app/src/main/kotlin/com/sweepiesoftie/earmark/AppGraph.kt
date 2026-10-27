package com.sweepiesoftie.earmark

import android.annotation.SuppressLint
import android.content.Context
import com.sweepiesoftie.earmark.core.Event
import com.sweepiesoftie.earmark.demo.DemoChats
import com.sweepiesoftie.earmark.listener.InstalledApps
import com.sweepiesoftie.earmark.listener.WayBackRegistry
import com.sweepiesoftie.earmark.store.SettingsStore
import com.sweepiesoftie.earmark.store.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Every long-lived object, wired by hand (ADR 0008). The activity and the listener share one process and this graph.
 * It only ever holds the application context, which lives as long as the process, so the static reference can't leak.
 */
@SuppressLint("StaticFieldLeak")
object AppGraph {
    lateinit var context: Context
        private set
    lateinit var store: Store
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var apps: InstalledApps
        private set
    val debugLog: DebugLog = DebugLog()
    val wayBacks: WayBackRegistry = WayBackRegistry()
    val demo: DemoChats = DemoChats()

    /** Events raised by screens (unmute a kind) for the listener to feed the engine. */
    val uiEvents: MutableSharedFlow<Event> = MutableSharedFlow(extraBufferCapacity = 16)

    /** For writes that must outlive a screen or a listener rebind. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (this::context.isInitialized) return
        this.context = context.applicationContext
        store = Store(this.context, debugLog)
        apps = InstalledApps(this.context)
        settings = SettingsStore(this.context)
    }
}
