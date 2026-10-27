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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
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
import com.sweepiesoftie.earmark.listener.FeedCandidate
import com.sweepiesoftie.earmark.store.UserSettings
import com.sweepiesoftie.earmark.ui.theme.InstrumentSerif
import java.text.DateFormat
import java.util.Date

data class SettingsState(
    val user: UserSettings = UserSettings(),
    val feeds: List<FeedCandidate> = emptyList(),
    val showAllApps: Boolean = false,
    val muted: List<LoopKind> = emptyList(),
)

class SettingsActions(
    val onBack: () -> Unit,
    val onPaused: (Boolean) -> Unit,
    val onDemo: (Boolean) -> Unit,
    val onDrift: (Int) -> Unit,
    val onDriftTimer: (DriftTimer) -> Unit,
    val onFeed: (String, Boolean) -> Unit,
    val onNames: (String) -> Unit,
    val onShowAllApps: () -> Unit,
    val onUnmute: (LoopKind) -> Unit,
    val onPermissions: () -> Unit,
    val onLog: () -> Unit,
)

@Composable
fun SettingsScreen(state: SettingsState, actions: SettingsActions) {
    val user = state.user
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        item { ScreenHeader(stringResource(R.string.settings_title), actions.onBack) }
        item { SwitchRow(stringResource(R.string.settings_pause), stringResource(R.string.settings_pause_body), user.paused, actions.onPaused) }
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
        if (state.muted.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.settings_muted))
                Text(stringResource(R.string.settings_muted_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
            }
            items(state.muted) { kind ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(kindName(kind), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    SecondaryButton(stringResource(R.string.settings_unmute)) { actions.onUnmute(kind) }
                }
            }
        }
        item {
            // Last, so Demo mode isn't the second thing an everyday user sees.
            SectionTitle(stringResource(R.string.settings_testers))
            SwitchRow(stringResource(R.string.settings_demo), stringResource(R.string.settings_demo_body), user.demoMode, actions.onDemo)
            Spacer(Modifier.heightIn(min = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.settings_permissions), onClick = actions.onPermissions)
                SecondaryButton(stringResource(R.string.settings_log), onClick = actions.onLog)
            }
            Spacer(Modifier.heightIn(min = 32.dp))
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
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
