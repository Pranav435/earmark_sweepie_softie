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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.core.LoopId
import com.sweepiesoftie.earmark.core.LoopKind
import com.sweepiesoftie.earmark.core.Outcome
import com.sweepiesoftie.earmark.core.Waiting
import com.sweepiesoftie.earmark.ui.theme.Brand
import com.sweepiesoftie.earmark.ui.theme.InstrumentSans
import com.sweepiesoftie.earmark.ui.theme.InstrumentSerif
import java.util.Date

/** One thing on today's recap. [label] is null once the 24-hour label window has passed. */
data class RecapRow(val id: String, val kind: LoopKind, val label: String?, val time: String, val outcome: Outcome?, val broughtBack: Boolean)

/** [rows] are the rest of today: things that closed, or that Earmark no longer watches. */
data class RecapState(
    val waiting: List<Waiting> = emptyList(),
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
    val broughtBack: Int get() = rows.count { it.broughtBack }
}

class RecapActions(
    val onOpen: (LoopId) -> Unit,
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

/** Home: who's waiting first, then the rest of today folded away (ADR 0032). */
@Composable
fun RecapScreen(state: RecapState, actions: RecapActions) {
    var earlier by rememberSaveable { mutableStateOf(false) }
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
        items(state.waiting, key = { it.loopId.value }) { WaitingRow(it, actions.onOpen) }
        if (state.rows.isNotEmpty()) {
            item { EarlierToggle(state.rows.size, earlier) { earlier = !earlier } }
            if (earlier) items(state.rows, key = { it.id }) { RecapRowView(it) }
        }
        item { Footer() }
        if (state.demoMode) item { DemoCard(state, actions) }
        item { Spacer(Modifier.heightIn(min = 24.dp)) }
    }
}

@Composable
private fun Headline(state: RecapState) {
    Column(Modifier.padding(top = 24.dp, bottom = 12.dp).semantics(mergeDescendants = true) { heading() }) {
        Text(headline(state.waiting), fontFamily = InstrumentSerif, fontSize = 36.sp, lineHeight = 40.sp, color = Tone.Ink)
        Spacer(Modifier.heightIn(min = 8.dp))
        when {
            state.broughtBack > 0 -> Text(
                pluralStringResource(R.plurals.recap_brought_back, state.broughtBack, state.broughtBack),
                fontFamily = InstrumentSerif,
                fontStyle = FontStyle.Italic,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                color = Tone.MarigoldText,
            )
            state.waiting.isNotEmpty() -> Text(stringResource(R.string.home_tap_hint), style = MaterialTheme.typography.bodyLarge, color = Tone.Muted)
            else -> {
                val line = if (state.openLoops > 0) pluralStringResource(R.plurals.recap_open_loops, state.openLoops, state.openLoops) else stringResource(R.string.recap_empty_line)
                Text(line, style = MaterialTheme.typography.bodyLarge, color = Tone.Muted)
            }
        }
    }
}

/** People, not a count: "Rahul and 2 others are waiting." A name that is a phone number is set in the sans. */
@Composable
private fun headline(waiting: List<Waiting>): AnnotatedString {
    @Composable
    fun name(w: Waiting, first: Boolean): String = when {
        // A reminder's note is its own words, which don't read as a name.
        w.kind == LoopKind.Reminder -> stringResource(if (first) R.string.home_a_reminder else R.string.home_a_reminder_lower)
        else -> w.note.who
    }
    val names = waiting.take(2).mapIndexed { i, w -> name(w, i == 0) }
    val text = when (waiting.size) {
        0 -> stringResource(R.string.home_nobody)
        1 -> stringResource(R.string.home_one, names[0])
        2 -> stringResource(R.string.home_two, names[0], names[1])
        else -> pluralStringResource(R.plurals.home_many, waiting.size - 1, names[0], waiting.size - 1)
    }
    return buildAnnotatedString {
        append(text)
        names.filter(::isNumber).forEach { number ->
            val at = text.indexOf(number)
            if (at >= 0) addStyle(SpanStyle(fontFamily = InstrumentSans, fontSize = 30.sp), at, at + number.length)
        }
    }
}

/** One person or thing waiting. The whole row opens it, straight to the chat, the call log or the bill. */
@Composable
private fun WaitingRow(waiting: Waiting, onOpen: (LoopId) -> Unit) {
    val context = LocalContext.current
    val time = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(Tone.Card)
            .border(1.dp, Tone.Line, shape)
            .clickable(role = Role.Button, onClickLabel = waiting.task) { onOpen(waiting.loopId) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Face(waiting.note, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(waiting.note.who, style = nameStyle(waiting.note.who, RowName), color = Tone.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            waiting.note.words?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tone.Ink.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Text(time.format(Date(waiting.at.toEpochMilli())), style = MaterialTheme.typography.labelSmall, color = Tone.Muted)
        }
        Spacer(Modifier.width(8.dp))
        // The row is the button; the verb only shows what a tap does.
        Text(
            waiting.note.verb,
            color = Brand.Ink,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Brand.Marigold).padding(horizontal = 14.dp, vertical = 8.dp).clearAndSetSemantics { },
        )
    }
}

private val RowName = TextStyle(fontFamily = InstrumentSerif, fontSize = 20.sp, lineHeight = 24.sp)

/** The rest of today, folded away so the list doesn't read as a score. */
@Composable
private fun EarlierToggle(count: Int, open: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (open) R.string.home_shown else R.string.home_hidden)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.home_earlier, count), style = MaterialTheme.typography.labelSmall, color = Tone.Muted, modifier = Modifier.weight(1f).semantics { heading() })
        Text(stringResource(if (open) R.string.home_hide else R.string.home_show), style = MaterialTheme.typography.labelLarge, color = Tone.Ink, modifier = Modifier.padding(horizontal = 4.dp).clearAndSetSemantics { })
    }
}

@Composable
private fun RecapRowView(row: RecapRow) {
    val label = row.label ?: kindName(row.kind)
    val state = when (row.outcome) {
        Outcome.Done -> stringResource(R.string.recap_state_done)
        Outcome.Dismissed -> stringResource(R.string.recap_state_dismissed)
        Outcome.Expired -> stringResource(R.string.recap_state_expired)
        // Not waiting any more, yet never closed: Earmark restarted, or lost the listener.
        null -> stringResource(R.string.recap_state_lost)
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
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (row.outcome == Outcome.Done) Tone.Ink else Tone.Muted)
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
        else -> Box(Modifier.size(28.dp).border(2.dp, Tone.Line, CircleShape))
    }
}

@Composable
private fun Footer() {
    Text(
        stringResource(R.string.recap_never),
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
        LoopKind.CallBack -> R.string.kind_call_back
        LoopKind.Reminder -> R.string.kind_reminder
        LoopKind.Email -> R.string.kind_email
    },
)
