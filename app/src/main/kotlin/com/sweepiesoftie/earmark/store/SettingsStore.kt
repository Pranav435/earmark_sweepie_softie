package com.sweepiesoftie.earmark.store

import android.content.Context
import com.sweepiesoftie.earmark.core.DriftTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What the user chose in Settings. Feed toggles are stored as overrides of the computed default set. */
data class UserSettings(
    val demoMode: Boolean = false,
    /** Paused until this time, in epoch milliseconds, or [UNTIL_RESUMED] (ADR 0036). */
    val pausedUntil: Long? = null,
    /** Right away by default (ADR 0030); Fixed uses [driftSeconds]. */
    val driftTimer: DriftTimer = DriftTimer.RightAway,
    val driftSeconds: Int = 90,
    val feedsOn: Set<String> = emptySet(),
    val feedsOff: Set<String> = emptySet(),
    /** Names you go by, as typed in Settings, for mentions in groups; your contact card adds its own (ADR 0028). */
    val names: String = "",
) {
    val typedNames: List<String> get() = names.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun pausedAt(nowMillis: Long): Boolean = pausedUntil != null && pausedUntil > nowMillis

    companion object {
        val driftChoices: List<Int> = listOf(30, 60, 90, 180, 300)
        const val UNTIL_RESUMED: Long = Long.MAX_VALUE
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<UserSettings> = _state

    fun setDemoMode(on: Boolean): Unit = edit { it.copy(demoMode = on) }

    /** [untilMillis] is when the pause ends, [UserSettings.UNTIL_RESUMED] for never, or null to resume now. */
    fun pause(untilMillis: Long?): Unit = edit { it.copy(pausedUntil = untilMillis) }

    fun setDriftSeconds(seconds: Int): Unit = edit { it.copy(driftTimer = DriftTimer.Fixed, driftSeconds = seconds) }

    fun setDriftTimer(timer: DriftTimer): Unit = edit { it.copy(driftTimer = timer) }

    fun setNames(names: String): Unit = edit { it.copy(names = names) }

    fun setFeed(packageName: String, on: Boolean): Unit = edit {
        if (on) it.copy(feedsOn = it.feedsOn + packageName, feedsOff = it.feedsOff - packageName)
        else it.copy(feedsOn = it.feedsOn - packageName, feedsOff = it.feedsOff + packageName)
    }

    private fun edit(change: (UserSettings) -> UserSettings) {
        _state.update(change)
        val s = _state.value
        prefs.edit()
            .putBoolean(DEMO, s.demoMode)
            .remove(PAUSED)
            .putLong(PAUSED_UNTIL, s.pausedUntil ?: 0L)
            .putString(DRIFT_TIMER, s.driftTimer.name)
            .putInt(DRIFT, s.driftSeconds)
            .putStringSet(FEEDS_ON, s.feedsOn)
            .putStringSet(FEEDS_OFF, s.feedsOff)
            .putString(NAMES, s.names)
            .apply()
    }

    private fun read() = UserSettings(
        demoMode = prefs.getBoolean(DEMO, false),
        // Before v0.1.17 a pause had no end.
        pausedUntil = prefs.getLong(PAUSED_UNTIL, 0L).takeIf { it > 0L } ?: UserSettings.UNTIL_RESUMED.takeIf { prefs.getBoolean(PAUSED, false) },
        driftTimer = DriftTimer.entries.find { it.name == prefs.getString(DRIFT_TIMER, null) } ?: DriftTimer.RightAway,
        driftSeconds = prefs.getInt(DRIFT, 90),
        feedsOn = prefs.getStringSet(FEEDS_ON, emptySet()).orEmpty().toSet(),
        feedsOff = prefs.getStringSet(FEEDS_OFF, emptySet()).orEmpty().toSet(),
        names = prefs.getString(NAMES, "").orEmpty(),
    )

    private companion object {
        const val DEMO = "demo_mode"
        const val PAUSED = "paused"
        const val PAUSED_UNTIL = "paused_until"
        const val DRIFT = "drift_seconds"
        // A new key, so updating turns Right away on even for someone who picked Smart or a fixed time (ADR 0030).
        const val DRIFT_TIMER = "drift_timer"
        const val FEEDS_ON = "feeds_on"
        const val FEEDS_OFF = "feeds_off"
        const val NAMES = "names"
    }
}
