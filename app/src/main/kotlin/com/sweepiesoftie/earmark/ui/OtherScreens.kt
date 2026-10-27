package com.sweepiesoftie.earmark.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.core.DriftTimer
import com.sweepiesoftie.earmark.core.LoopKind
import com.sweepiesoftie.earmark.core.Trigger
import com.sweepiesoftie.earmark.listener.FeedCandidate
import com.sweepiesoftie.earmark.store.UserSettings
import com.sweepiesoftie.earmark.ui.theme.InstrumentSerif
import java.text.DateFormat
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

data class SettingsState(
    val user: UserSettings = UserSettings(),
    val feeds: List<FeedCandidate> = emptyList(),
    val showAllApps: Boolean = false,
    val muted: List<LoopKind> = emptyList(),
    /** Chats muted by "not this" three times in a row (ADR 0033). Only hashes are stored, so just how many. */
    val mutedChats: Int = 0,
    /** When a pause in force ends, or [UserSettings.UNTIL_RESUMED]; null when Earmark isn't paused (ADR 0036). */
    val pausedUntil: Long? = null,
    /** Android 13 and later can offer to add the "Earmark this" tile from here (ADR 0035). */
    val canAddTile: Boolean = false,
    /** Triggers muted by "not this" three times in a row (ADR 0038). */
    val mutedTriggers: List<Trigger> = emptyList(),
)

class SettingsActions(
    val onBack: () -> Unit,
    /** Pause until a time in epoch milliseconds, [UserSettings.UNTIL_RESUMED], or null to resume. */
    val onPause: (Long?) -> Unit,
    val onAddTile: () -> Unit,
    val onDrift: (Int) -> Unit,
    val onDriftTimer: (DriftTimer) -> Unit,
    val onFeed: (String, Boolean) -> Unit,
    val onNames: (String) -> Unit,
    val onShowAllApps: () -> Unit,
    val onUnmute: (LoopKind) -> Unit,
    val onUnmuteChats: () -> Unit,
    val onUnmuteTrigger: (String) -> Unit,
    val onAdvanced: () -> Unit,
)

@Composable
fun SettingsScreen(state: SettingsState, actions: SettingsActions) {
    val user = state.user
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        item { ScreenHeader(stringResource(R.string.settings_title), actions.onBack) }
        item { PauseChoices(state.pausedUntil, actions.onPause) }
        item {
            SectionTitle(stringResource(R.string.settings_tile))
            Text(stringResource(R.string.settings_tile_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
            Spacer(Modifier.heightIn(min = 12.dp))
            if (state.canAddTile) {
                SecondaryButton(stringResource(R.string.settings_tile_add), onClick = actions.onAddTile)
            } else {
                Text(stringResource(R.string.settings_tile_how), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SectionTitle(stringResource(R.string.settings_drift))
            Text(stringResource(R.string.settings_drift_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
            Spacer(Modifier.heightIn(min = 12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(stringResource(R.string.settings_drift_right_away), selected = user.driftTimer == DriftTimer.RightAway, enabled = !user.demoMode) { actions.onDriftTimer(DriftTimer.RightAway) }
                Choice(stringResource(R.string.settings_drift_smart), selected = user.driftTimer == DriftTimer.Smart, enabled = !user.demoMode) { actions.onDriftTimer(DriftTimer.Smart) }
                UserSettings.driftChoices.forEach { seconds ->
                    val label = if (seconds < 60 || seconds % 60 != 0) stringResource(R.string.settings_seconds, seconds) else stringResource(R.string.settings_minutes, seconds / 60)
                    Choice(label, selected = user.driftTimer == DriftTimer.Fixed && user.driftSeconds == seconds, enabled = !user.demoMode) { actions.onDrift(seconds) }
                }
            }
            if (user.demoMode) Text(stringResource(R.string.settings_drift_demo), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            SectionTitle(stringResource(R.string.settings_feeds))
            Text(stringResource(R.string.settings_feeds_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
        }
        items(state.feeds, key = { it.packageName }) { app ->
            val on = (app.onByDefault || app.packageName in user.feedsOn) && app.packageName !in user.feedsOff
            SwitchRow(app.label, null, on) { actions.onFeed(app.packageName, it) }
        }
        if (!state.showAllApps) item { TextAction(stringResource(R.string.settings_feeds_more), actions.onShowAllApps) }
        item {
            SectionTitle(stringResource(R.string.settings_names))
            Text(stringResource(R.string.settings_names_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
            Spacer(Modifier.heightIn(min = 12.dp))
            // Local text, so typing isn't slowed by the round trip through the settings store.
            var names by rememberSaveable { mutableStateOf(user.names) }
            OutlinedTextField(
                value = names,
                onValueChange = { names = it; actions.onNames(it) },
                label = { Text(stringResource(R.string.settings_names_label)) },
                placeholder = { Text(stringResource(R.string.settings_names_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Tone.Ink,
                    unfocusedBorderColor = Tone.Line,
                    focusedLabelColor = Tone.Ink,
                    unfocusedLabelColor = Tone.Muted,
                    cursorColor = Tone.Ink,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.muted.isNotEmpty() || state.mutedChats > 0 || state.mutedTriggers.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.settings_muted))
                Text(stringResource(R.string.settings_muted_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
            }
            if (state.mutedChats > 0) {
                item { MutedRow(pluralStringResource(R.plurals.settings_muted_chats, state.mutedChats, state.mutedChats), actions.onUnmuteChats) }
            }
            items(state.muted) { kind -> MutedRow(kindName(kind)) { actions.onUnmute(kind) } }
            items(state.mutedTriggers, key = { it.id }) { trigger -> MutedRow(triggerSentence(trigger)) { actions.onUnmuteTrigger(trigger.id) } }
        }
        item {
            // Last, so custom triggers and Demo mode aren't what an everyday user sees first (ADR 0038).
            Spacer(Modifier.heightIn(min = 16.dp))
            LinkRow(stringResource(R.string.settings_advanced), stringResource(R.string.settings_advanced_body), actions.onAdvanced)
            Spacer(Modifier.heightIn(min = 32.dp))
        }
    }
}

/** A row that opens another screen: one TalkBack stop that reads its title and what's there. */
@Composable
fun LinkRow(title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = Tone.Muted, modifier = Modifier.padding(start = 12.dp).clearAndSetSemantics { })
    }
}

@Composable
private fun MutedRow(label: String, onUnmute: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        SecondaryButton(stringResource(R.string.settings_unmute), onClick = onUnmute)
    }
}

/** A pause that ends by itself, so it can't be forgotten on for days (ADR 0036). */
@Composable
private fun PauseChoices(pausedUntil: Long?, onPause: (Long?) -> Unit) {
    val context = LocalContext.current
    val time = remember { android.text.format.DateFormat.getTimeFormat(context) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.settings_pause), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { heading() })
        val body = when (pausedUntil) {
            null -> stringResource(R.string.settings_pause_body)
            UserSettings.UNTIL_RESUMED -> stringResource(R.string.settings_paused_forever)
            else -> stringResource(R.string.settings_paused_until, time.format(Date(pausedUntil)))
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
        Spacer(Modifier.heightIn(min = 10.dp))
        if (pausedUntil != null) {
            PrimaryButton(stringResource(R.string.settings_resume)) { onPause(null) }
        } else {
            // Wraps rather than scrolls, so all three show at any font size.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.settings_pause_hour)) { onPause(System.currentTimeMillis() + HOUR_MILLIS) }
                SecondaryButton(stringResource(R.string.settings_pause_morning, time.format(Date(nextMorning())))) { onPause(nextMorning()) }
                SecondaryButton(stringResource(R.string.settings_pause_forever)) { onPause(UserSettings.UNTIL_RESUMED) }
            }
        }
    }
}

private const val HOUR_MILLIS = 60 * 60 * 1000L

/** The next 7:00 in the morning, today's if it hasn't come yet. */
private fun nextMorning(): Long {
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    val today = now.toLocalDate().atTime(MORNING).atZone(now.zone)
    return (if (today.isAfter(now)) today else today.plusDays(1)).toInstant().toEpochMilli()
}

private val MORNING: LocalTime = LocalTime.of(7, 0)

@Composable
fun Choice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) Tone.Ink else Tone.Card)
            .border(1.dp, if (selected) Tone.Ink else Tone.Line, RoundedCornerShape(24.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Tone.Paper else if (enabled) Tone.Ink else Tone.Muted)
    }
}

@Composable
fun LogScreen(lines: List<DebugLog.Line>, onBack: () -> Unit, onCopy: () -> Unit, onClear: () -> Unit) {
    val format = DateFormat.getTimeInstance(DateFormat.MEDIUM)
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenHeader(stringResource(R.string.log_title), onBack) }
                if (lines.isNotEmpty()) {
                    TextAction(stringResource(R.string.log_copy), onCopy)
                    TextAction(stringResource(R.string.log_clear), onClear)
                }
            }
            Text(stringResource(R.string.log_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted, modifier = Modifier.padding(bottom = 12.dp))
        }
        if (lines.isEmpty()) item { Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.bodyLarge) }
        items(lines.asReversed()) { line ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }.padding(vertical = 4.dp)) {
                Text(format.format(Date(line.atMillis)), style = MaterialTheme.typography.labelSmall, color = Tone.Muted)
                Text(line.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
