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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.R
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
    val onFeed: (String, Boolean) -> Unit,
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
        item {
            SwitchRow(stringResource(R.string.settings_pause), stringResource(R.string.settings_pause_body), user.paused, actions.onPaused)
            SwitchRow(stringResource(R.string.settings_demo), stringResource(R.string.settings_demo_body), user.demoMode, actions.onDemo)
        }
        item {
            SectionTitle(stringResource(R.string.settings_drift))
            Text(stringResource(R.string.settings_drift_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
            Spacer(Modifier.heightIn(min = 12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserSettings.driftChoices.forEach { seconds ->
                    val label = if (seconds < 60 || seconds % 60 != 0) stringResource(R.string.settings_seconds, seconds) else stringResource(R.string.settings_minutes, seconds / 60)
                    Choice(label, selected = user.driftSeconds == seconds, enabled = !user.demoMode) { actions.onDrift(seconds) }
                }
            }
            if (user.demoMode) Text(stringResource(R.string.settings_drift_demo), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted, modifier = Modifier.padding(top = 8.dp))
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
            SectionTitle(stringResource(R.string.settings_feeds))
            Text(stringResource(R.string.settings_feeds_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
        }
        items(state.feeds, key = { it.packageName }) { app ->
            val on = (app.onByDefault || app.packageName in user.feedsOn) && app.packageName !in user.feedsOff
            SwitchRow(app.label, null, on) { actions.onFeed(app.packageName, it) }
        }
        if (!state.showAllApps) item { TextAction(stringResource(R.string.settings_feeds_more), actions.onShowAllApps) }
        item {
            SectionTitle(stringResource(R.string.app_name))
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
fun LogScreen(lines: List<DebugLog.Line>, onBack: () -> Unit, onClear: () -> Unit) {
    val format = DateFormat.getTimeInstance(DateFormat.MEDIUM)
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenHeader(stringResource(R.string.log_title), onBack) }
                if (lines.isNotEmpty()) TextAction(stringResource(R.string.log_clear), onClear)
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

data class PermissionState(
    val notifications: Boolean = false,
    val usage: Boolean = false,
    val overlay: Boolean = false,
    val calendar: Boolean = false,
    val postNotifications: Boolean = false,
    val battery: Boolean = false,
) {
    val required: Boolean get() = notifications && usage && overlay
}

class OnboardingActions(
    val onNotificationAccess: () -> Unit,
    val onUsageAccess: () -> Unit,
    val onOverlay: () -> Unit,
    val onBattery: () -> Unit,
    val onCalendar: () -> Unit,
    val onPostNotifications: () -> Unit,
    val onAppInfo: () -> Unit,
    val onDone: () -> Unit,
)

@Composable
fun OnboardingScreen(state: PermissionState, actions: OnboardingActions) {
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Masthead() }
        item {
            Text(
                stringResource(R.string.onboarding_title),
                fontFamily = InstrumentSerif,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                modifier = Modifier.padding(top = 20.dp).semantics { heading() },
            )
            Text(stringResource(R.string.onboarding_body), style = MaterialTheme.typography.bodyLarge, color = Tone.Muted, modifier = Modifier.padding(top = 12.dp))
        }
        item { PermissionRow(1, stringResource(R.string.onboarding_notifications), stringResource(R.string.onboarding_notifications_body), state.notifications, stringResource(R.string.onboarding_open_settings), actions.onNotificationAccess) }
        item { PermissionRow(2, stringResource(R.string.onboarding_usage), stringResource(R.string.onboarding_usage_body), state.usage, stringResource(R.string.onboarding_open_settings), actions.onUsageAccess) }
        item { PermissionRow(3, stringResource(R.string.onboarding_overlay), stringResource(R.string.onboarding_overlay_body), state.overlay, stringResource(R.string.onboarding_open_settings), actions.onOverlay) }
        item { PermissionRow(4, stringResource(R.string.onboarding_battery), stringResource(R.string.onboarding_battery_body), state.battery, stringResource(R.string.onboarding_allow), actions.onBattery) }
        item { PermissionRow(5, stringResource(R.string.onboarding_calendar), stringResource(R.string.onboarding_calendar_body), state.calendar, stringResource(R.string.onboarding_allow), actions.onCalendar) }
        item { PermissionRow(6, stringResource(R.string.onboarding_post), stringResource(R.string.onboarding_post_body), state.postNotifications, stringResource(R.string.onboarding_allow), actions.onPostNotifications) }
        if (!state.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tone.MarigoldWash).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.onboarding_restricted_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.onboarding_restricted_body), style = MaterialTheme.typography.bodyMedium)
                    SecondaryButton(stringResource(R.string.onboarding_app_info), onClick = actions.onAppInfo)
                }
            }
        }
        item {
            Text(stringResource(R.string.onboarding_privacy), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.heightIn(min = 16.dp))
            PrimaryButton(
                stringResource(if (state.required) R.string.onboarding_done else R.string.onboarding_later),
                modifier = Modifier.fillMaxWidth(),
                onClick = actions.onDone,
            )
            Spacer(Modifier.heightIn(min = 24.dp))
        }
    }
}

@Composable
private fun PermissionRow(step: Int, title: String, body: String, granted: Boolean, action: String, onAction: () -> Unit) {
    val allowed = stringResource(R.string.onboarding_granted)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Tone.Card)
            .border(1.dp, Tone.Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (granted) Tone.Done else Tone.MarigoldWash).clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            if (granted) {
                Image(painterResource(R.drawable.ic_check), contentDescription = null, colorFilter = ColorFilter.tint(Tone.Paper), modifier = Modifier.size(20.dp))
            } else {
                Text(step.toString(), style = MaterialTheme.typography.labelLarge, color = Tone.Ink, maxLines = 1)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
            if (granted) Text(allowed, style = MaterialTheme.typography.labelSmall, color = Tone.Done, modifier = Modifier.padding(top = 4.dp))
        }
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Tone.Ink)
                    .clickable(role = Role.Button, onClickLabel = "$action: $title", onClick = onAction)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(action, color = Tone.Paper, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
