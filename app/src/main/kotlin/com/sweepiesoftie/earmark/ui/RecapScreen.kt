package com.sweepiesoftie.earmark.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.core.LoopKind
import com.sweepiesoftie.earmark.core.Outcome
import com.sweepiesoftie.earmark.ui.theme.Brand
import com.sweepiesoftie.earmark.ui.theme.InstrumentSerif

/** One thing on today's recap. [label] is null once the 24-hour label window has passed. */
data class RecapRow(val id: String, val kind: LoopKind, val label: String?, val time: String, val outcome: Outcome?, val broughtBack: Boolean)

data class RecapState(
    val rows: List<RecapRow> = emptyList(),
    val paused: Boolean = false,
    val missingPermission: Boolean = false,
    /** Notification access is on, but Android hasn't connected the listener. */
    val notListening: Boolean = false,
    val openLoops: Int = 0,
    /** Battery optimisation is on, so some phones freeze Earmark in the background. */
    val batteryRestricted: Boolean = false,
    val demoMode: Boolean = false,
    val canPostNotifications: Boolean = true,
) {
    val done: Int get() = rows.count { it.outcome == Outcome.Done }
    val broughtBack: Int get() = rows.count { it.broughtBack }
}

class RecapActions(
    val onSettings: () -> Unit,
    val onLog: () -> Unit,
    val onResume: () -> Unit,
    val onFixPermissions: () -> Unit,
    val onNotificationAccess: () -> Unit,
    val onBattery: () -> Unit,
    val onDemoRahul: () -> Unit,
    val onDemoAisha: () -> Unit,
    val onAllowNotifications: () -> Unit,
)

@Composable
fun RecapScreen(state: RecapState, actions: RecapActions) {
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Masthead {
                TextAction(stringResource(R.string.nav_log), actions.onLog)
                TextAction(stringResource(R.string.nav_settings), actions.onSettings)
            }
        }
        if (state.missingPermission) item { Banner(stringResource(R.string.recap_missing), stringResource(R.string.recap_fix), actions.onFixPermissions) }
        if (state.batteryRestricted) item { Banner(stringResource(R.string.recap_battery), stringResource(R.string.recap_battery_allow), actions.onBattery) }
        if (state.notListening) item { Banner(stringResource(R.string.recap_not_listening), stringResource(R.string.onboarding_open_settings), actions.onNotificationAccess) }
        if (state.paused) item { Banner(stringResource(R.string.recap_paused), stringResource(R.string.recap_resume), actions.onResume) }
        item { Headline(state) }
        items(state.rows, key = { it.id }) { RecapRowView(it) }
        item { Footer(state) }
        if (state.demoMode) item { DemoCard(state, actions) }
        item { Spacer(Modifier.heightIn(min = 24.dp)) }
    }
}

@Composable
private fun Headline(state: RecapState) {
    val total = state.rows.size
    Column(Modifier.padding(top = 24.dp, bottom = 12.dp).semantics(mergeDescendants = true) { heading() }) {
        val title = if (total == 0) stringResource(R.string.recap_empty_title) else pluralStringResource(R.plurals.recap_title, total, total)
        Text(title, fontFamily = InstrumentSerif, fontSize = 40.sp, lineHeight = 44.sp, color = Tone.Ink)
        val second = when {
            total == 0 -> null
            state.done == total && total == 1 -> stringResource(R.string.recap_all_done_one)
            state.done == total -> stringResource(R.string.recap_all_done, total)
            else -> stringResource(R.string.recap_some_done, state.done, total)
        }
        if (second != null) {
            Text(second, fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic, fontSize = 40.sp, lineHeight = 44.sp, color = Tone.MarigoldText)
        } else {
            Spacer(Modifier.heightIn(min = 8.dp))
            val line = if (state.openLoops > 0) pluralStringResource(R.plurals.recap_open_loops, state.openLoops, state.openLoops) else stringResource(R.string.recap_empty_line)
            Text(line, style = MaterialTheme.typography.bodyLarge, color = Tone.Muted)
        }
    }
}

@Composable
private fun RecapRowView(row: RecapRow) {
    val label = row.label ?: kindName(row.kind)
    val state = when (row.outcome) {
        Outcome.Done -> stringResource(R.string.recap_state_done)
        Outcome.Dismissed -> stringResource(R.string.recap_state_dismissed)
        Outcome.Expired -> stringResource(R.string.recap_state_expired)
        null -> stringResource(R.string.recap_state_open)
    }
    val tag = stringResource(R.string.recap_tag_brought_back)
    val spoken = listOfNotNull(label, row.time, state, tag.takeIf { row.broughtBack }).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Tone.Card)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // At large font sizes the tag moves under the time so the label keeps its width.
        val stacked = LocalDensity.current.fontScale > 1.3f
        StatusMark(row.outcome)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (row.outcome == Outcome.Done || row.outcome == null) Tone.Ink else Tone.Muted)
            Text(row.time, style = MaterialTheme.typography.labelSmall, color = Tone.Muted)
            if (row.broughtBack && stacked) BroughtBackTag(tag)
        }
        if (row.broughtBack && !stacked) {
            Spacer(Modifier.width(8.dp))
            BroughtBackTag(tag)
        }
    }
}

@Composable
private fun BroughtBackTag(tag: String) {
    Text(
        "↶ $tag",
        style = MaterialTheme.typography.labelSmall,
        color = Tone.Ink,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Tone.MarigoldWash).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun StatusMark(outcome: Outcome?) {
    when (outcome) {
        Outcome.Done -> Box(Modifier.size(28.dp).clip(CircleShape).background(Tone.Done), contentAlignment = Alignment.Center) {
            Image(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Tone.Paper),
                modifier = Modifier.size(18.dp),
            )
        }
        null -> Box(Modifier.size(28.dp).border(2.dp, Brand.Marigold, CircleShape))
        else -> Box(Modifier.size(28.dp).border(2.dp, Tone.Line, CircleShape))
    }
}

@Composable
private fun Footer(state: RecapState) {
    val brought = if (state.broughtBack > 0) pluralStringResource(R.plurals.recap_brought_back, state.broughtBack, state.broughtBack) + " " else ""
    Text(
        brought + stringResource(R.string.recap_never),
        style = MaterialTheme.typography.bodyMedium,
        color = Tone.Muted,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(Tone.MarigoldWash).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(action, onClick = onAction)
    }
}

@Composable
private fun DemoCard(state: RecapState, actions: RecapActions) {
    // Ink stands out on paper; on a dark screen an ink card would vanish and a paper one would glare, so it's raised instead.
    val fill = if (Tone.Dark) Tone.Card else Tone.Ink
    val text = if (Tone.Dark) Tone.Ink else Tone.Paper
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(20.dp)).background(fill).border(1.dp, Tone.Line, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.demo_title), style = MaterialTheme.typography.titleMedium, color = text, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.demo_body), style = MaterialTheme.typography.bodyMedium, color = text.copy(alpha = 0.75f))
        if (!state.canPostNotifications) {
            DemoButton(stringResource(R.string.demo_allow_notifications), actions.onAllowNotifications)
        } else {
            DemoButton(stringResource(R.string.demo_rahul), actions.onDemoRahul)
            DemoButton(stringResource(R.string.demo_aisha), actions.onDemoAisha)
        }
        Text(stringResource(R.string.demo_bedtime), style = MaterialTheme.typography.bodyMedium, color = text.copy(alpha = 0.75f))
    }
}

@Composable
private fun DemoButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brand.Marigold)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Brand.Ink, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun kindName(kind: LoopKind): String = stringResource(
    when (kind) {
        LoopKind.Reply -> R.string.kind_reply
        LoopKind.Payment -> R.string.kind_payment
        LoopKind.Flow -> R.string.kind_flow
        LoopKind.Routine -> R.string.kind_routine
        LoopKind.LeaveBy -> R.string.kind_leave_by
    },
)
