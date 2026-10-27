package com.sweepiesoftie.earmark.store

import android.content.Context
import com.sweepiesoftie.earmark.core.DriftTimer
import com.sweepiesoftie.earmark.core.LoopKind
import com.sweepiesoftie.earmark.core.ScreenTiming
import com.sweepiesoftie.earmark.core.Trigger
import com.sweepiesoftie.earmark.core.Watch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime

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
    /** Custom triggers from Advanced, in the order they were added (ADR 0038). */
    val triggers: List<Trigger> = emptyList(),
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

    /** Adds [trigger], or replaces the one with its id (ADR 0038). */
    fun saveTrigger(trigger: Trigger): Unit = edit { s ->
        s.copy(triggers = if (s.triggers.any { it.id == trigger.id }) s.triggers.map { if (it.id == trigger.id) trigger else it } else s.triggers + trigger)
    }

    fun deleteTrigger(id: String): Unit = edit { s -> s.copy(triggers = s.triggers.filterNot { it.id == id }) }

    fun setTriggerOn(id: String, on: Boolean): Unit = edit { s -> s.copy(triggers = s.triggers.map { if (it.id == id) it.copy(on = on) else it }) }

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
            .putString(TRIGGERS, encode(s.triggers))
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
        triggers = decode(prefs.getString(TRIGGERS, null)),
    )

    /** Triggers are what you typed in Advanced, kept on the phone as JSON. Their words are yours, not anything Earmark read. */
    private fun encode(triggers: List<Trigger>): String = JSONArray(
        triggers.map { t ->
            JSONObject()
                .put("id", t.id)
                .put("package", t.packageName)
                .put("app", t.appLabel)
                .put("watch", t.watch.name)
                .put("words", JSONArray(t.words))
                .put("timing", t.timing.name)
                .put("by", t.by?.toString())
                .put("days", JSONArray(t.days.map { it.name }))
                .put("kind", t.kind.name)
                .put("ignore", t.ignore)
                .put("label", t.label)
                .put("on", t.on)
        },
    ).toString()

    /** A trigger a later version can't read is dropped rather than guessed at. */
    private fun decode(json: String?): List<Trigger> {
        if (json.isNullOrEmpty()) return emptyList()
        val array = try {
            JSONArray(json)
        } catch (_: JSONException) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val watch = Watch.entries.find { it.name == o.optString("watch") } ?: return@mapNotNull null
            val kind = LoopKind.entries.find { it.name == o.optString("kind") } ?: return@mapNotNull null
            val words = o.optJSONArray("words")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val days = o.optJSONArray("days")?.let { a -> (0 until a.length()).mapNotNull { d -> DayOfWeek.entries.find { it.name == a.optString(d) } } }
            Trigger(
                id = o.optString("id").ifEmpty { return@mapNotNull null },
                packageName = o.optString("package").ifEmpty { return@mapNotNull null },
                appLabel = o.optString("app"),
                watch = watch,
                words = words,
                timing = ScreenTiming.entries.find { it.name == o.optString("timing") } ?: ScreenTiming.OnLeaving,
                by = o.optString("by").takeIf { it.isNotEmpty() && o.has("by") }?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                days = days?.toSet() ?: DayOfWeek.entries.toSet(),
                kind = kind,
                ignore = o.optBoolean("ignore"),
                label = o.optString("label").takeIf { o.has("label") && it.isNotBlank() },
                on = o.optBoolean("on", true),
            )
        }
    }

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
        const val TRIGGERS = "triggers"
    }
}
