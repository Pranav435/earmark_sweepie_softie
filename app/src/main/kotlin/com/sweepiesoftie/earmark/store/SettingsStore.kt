package com.sweepiesoftie.earmark.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What the user chose in Settings. Feed toggles are stored as overrides of the computed default set. */
data class UserSettings(
    val demoMode: Boolean = false,
    val paused: Boolean = false,
    /** Smart works the timer out per guess (ADR 0023); off, [driftSeconds] is used. */
    val driftSmart: Boolean = true,
    val driftSeconds: Int = 90,
    val feedsOn: Set<String> = emptySet(),
    val feedsOff: Set<String> = emptySet(),
) {
    /** Demo mode uses a 10-second drift timer so a story fits in a minute (ADR 0006). */
    val effectiveDriftSeconds: Int get() = if (demoMode) DEMO_DRIFT_SECONDS else driftSeconds

    companion object {
        const val DEMO_DRIFT_SECONDS: Int = 10
        val driftChoices: List<Int> = listOf(30, 60, 90, 180, 300)
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<UserSettings> = _state

    fun setDemoMode(on: Boolean): Unit = edit { it.copy(demoMode = on) }

    fun setPaused(on: Boolean): Unit = edit { it.copy(paused = on) }

    fun setDriftSeconds(seconds: Int): Unit = edit { it.copy(driftSmart = false, driftSeconds = seconds) }

    fun setDriftSmart(): Unit = edit { it.copy(driftSmart = true) }

    fun setFeed(packageName: String, on: Boolean): Unit = edit {
        if (on) it.copy(feedsOn = it.feedsOn + packageName, feedsOff = it.feedsOff - packageName)
        else it.copy(feedsOn = it.feedsOn - packageName, feedsOff = it.feedsOff + packageName)
    }

    private fun edit(change: (UserSettings) -> UserSettings) {
        _state.update(change)
        val s = _state.value
        prefs.edit()
            .putBoolean(DEMO, s.demoMode)
            .putBoolean(PAUSED, s.paused)
            .putBoolean(DRIFT_SMART, s.driftSmart)
            .putInt(DRIFT, s.driftSeconds)
            .putStringSet(FEEDS_ON, s.feedsOn)
            .putStringSet(FEEDS_OFF, s.feedsOff)
            .apply()
    }

    private fun read() = UserSettings(
        demoMode = prefs.getBoolean(DEMO, false),
        paused = prefs.getBoolean(PAUSED, false),
        driftSmart = prefs.getBoolean(DRIFT_SMART, true),
        driftSeconds = prefs.getInt(DRIFT, 90),
        feedsOn = prefs.getStringSet(FEEDS_ON, emptySet()).orEmpty().toSet(),
        feedsOff = prefs.getStringSet(FEEDS_OFF, emptySet()).orEmpty().toSet(),
    )

    private companion object {
        const val DEMO = "demo_mode"
        const val PAUSED = "paused"
        const val DRIFT = "drift_seconds"
        const val DRIFT_SMART = "drift_smart"
        const val FEEDS_ON = "feeds_on"
        const val FEEDS_OFF = "feeds_off"
    }
}
