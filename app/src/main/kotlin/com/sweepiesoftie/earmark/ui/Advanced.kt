package com.sweepiesoftie.earmark.ui

import android.app.TimePickerDialog
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.core.LoopKind
import com.sweepiesoftie.earmark.core.ScreenTiming
import com.sweepiesoftie.earmark.core.Trigger
import com.sweepiesoftie.earmark.core.TriggerStat
import com.sweepiesoftie.earmark.core.Watch
import com.sweepiesoftie.earmark.listener.FeedCandidate
import com.sweepiesoftie.earmark.store.UserSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.UUID

data class AdvancedState(
    val user: UserSettings = UserSettings(),
    /** Screen reading is on in Android's Accessibility settings (ADR 0038). */
    val screenReading: Boolean = false,
    val stats: Map<String, TriggerStat> = emptyMap(),
)

class AdvancedActions(
    val onBack: () -> Unit,
    val onAccessibility: () -> Unit,
    val onAppInfo: () -> Unit,
    val onAdd: () -> Unit,
    val onEdit: (Trigger) -> Unit,
    val onTriggerOn: (String, Boolean) -> Unit,
    val onDemo: (Boolean) -> Unit,
    val onPermissions: () -> Unit,
    val onLog: () -> Unit,
)

/** Settings › Advanced (ADR 0038): reading the screen, custom triggers, and the tools for testers. */
@Composable
fun AdvancedScreen(state: AdvancedState, actions: AdvancedActions) {
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        item { ScreenHeader(stringResource(R.string.advanced_title), actions.onBack) }
        item {
            SectionTitle(stringResource(R.string.advanced_screen))
            Text(stringResource(R.string.advanced_screen_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
            Spacer(Modifier.heightIn(min = 12.dp))
            Text(
                stringResource(if (state.screenReading) R.string.advanced_screen_on else R.string.advanced_screen_off),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.heightIn(min = 12.dp))
            SecondaryButton(stringResource(R.string.advanced_screen_open), onClick = actions.onAccessibility)
            if (!state.screenReading) {
                Text(stringResource(R.string.advanced_screen_how), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted, modifier = Modifier.padding(vertical = 12.dp))
                SecondaryButton(stringResource(R.string.onboarding_app_info), onClick = actions.onAppInfo)
            }
        }
        item {
            SectionTitle(stringResource(R.string.advanced_triggers))
            Text(stringResource(R.string.advanced_triggers_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
            Spacer(Modifier.heightIn(min = 8.dp))
            if (state.user.triggers.isEmpty()) {
                Text(stringResource(R.string.advanced_triggers_none), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        items(state.user.triggers, key = { it.id }) { trigger ->
            TriggerRow(trigger, triggerStatus(trigger, state.stats[trigger.id], state.screenReading), { actions.onEdit(trigger) }) { actions.onTriggerOn(trigger.id, it) }
        }
        item {
            Spacer(Modifier.heightIn(min = 12.dp))
            PrimaryButton(stringResource(R.string.advanced_add), onClick = actions.onAdd)
        }
        item {
            SectionTitle(stringResource(R.string.settings_testers))
            SwitchRow(stringResource(R.string.settings_demo), stringResource(R.string.settings_demo_body), state.user.demoMode, actions.onDemo)
            Spacer(Modifier.heightIn(min = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.settings_permissions), onClick = actions.onPermissions)
                SecondaryButton(stringResource(R.string.settings_log), onClick = actions.onLog)
            }
            Spacer(Modifier.heightIn(min = 32.dp))
        }
    }
}

/** Two TalkBack stops: the trigger as a sentence with how it's doing, which edits it, then its switch. */
@Composable
private fun TriggerRow(trigger: Trigger, status: String, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.advanced_edit), onClick = onEdit)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(triggerSentence(trigger), style = MaterialTheme.typography.bodyLarge)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
        }
        Spacer(Modifier.width(12.dp))
        val label = stringResource(R.string.trigger_switch, trigger.appLabel)
        Switch(checked = trigger.on, onCheckedChange = onToggle, colors = switchColors(), modifier = Modifier.semantics { contentDescription = label })
    }
}

/** "IRCTC: screen says “Proceed to pay”, on leaving › Finish", for Advanced and Settings › Muted. */
@Composable
fun triggerSentence(trigger: Trigger): String {
    val app = trigger.appLabel
    val words = trigger.words.joinToString(", ")
    val watch = when (trigger.watch) {
        Watch.NotificationWords -> stringResource(R.string.trigger_watch_words, app, words)
        Watch.AnyNotification -> stringResource(if (trigger.ignore) R.string.trigger_watch_everything else R.string.trigger_watch_any, app)
        Watch.ScreenWords -> stringResource(
            if (trigger.timing == ScreenTiming.OnLeaving) R.string.trigger_watch_screen_leaving else R.string.trigger_watch_screen_shown,
            app,
            words,
        )
        Watch.NotOpenedBy -> stringResource(R.string.trigger_watch_time, app, clock(trigger.by ?: LocalTime.of(21, 0)), days(trigger.days))
    }
    return stringResource(R.string.trigger_sentence, watch, stringResource(doName(trigger.kind, trigger.ignore)))
}

/** Off, muted, waiting for screen reading, or when it last matched: enough to tell a trigger works. */
@Composable
private fun triggerStatus(trigger: Trigger, stat: TriggerStat?, screenReading: Boolean): String {
    val matched = stat?.lastMatched
    return when {
        !trigger.on -> stringResource(R.string.trigger_status_off)
        stat?.muted == true -> stringResource(R.string.trigger_status_muted)
        trigger.watch == Watch.ScreenWords && !screenReading -> stringResource(R.string.trigger_status_waiting)
        matched != null && System.currentTimeMillis() - matched.toEpochMilli() < DateUtils.MINUTE_IN_MILLIS -> stringResource(R.string.trigger_status_just_now)
        matched != null -> stringResource(
            R.string.trigger_status_matched,
            DateUtils.getRelativeTimeSpanString(matched.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
        )
        else -> stringResource(R.string.trigger_status_never)
    }
}

private fun doName(kind: LoopKind, ignore: Boolean): Int = when {
    ignore -> R.string.trigger_do_ignore
    kind == LoopKind.Payment -> R.string.trigger_do_pay
    kind == LoopKind.Reply -> R.string.trigger_do_reply
    kind == LoopKind.CallBack -> R.string.trigger_do_call_back
    kind == LoopKind.Flow -> R.string.trigger_do_finish
    kind == LoopKind.Own -> R.string.trigger_do_own
    else -> R.string.trigger_do_reminder
}

@Composable
private fun clock(time: LocalTime): String {
    val context = LocalContext.current
    val format = remember { android.text.format.DateFormat.getTimeFormat(context) }
    return format.format(Date.from(time.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant()))
}

private val weekdays = DayOfWeek.entries.take(5).toSet()
private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

@Composable
private fun days(days: Set<DayOfWeek>): String {
    val locale = LocalLocale.current.platformLocale
    return when (days) {
        DayOfWeek.entries.toSet() -> stringResource(R.string.trigger_days_every)
        weekdays -> stringResource(R.string.trigger_days_weekdays)
        weekend -> stringResource(R.string.trigger_days_weekends)
        else -> DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
}

class TriggerFormActions(
    val onBack: () -> Unit,
    val onSave: (Trigger) -> Unit,
    val onDelete: (String) -> Unit,
    val onTurnOnScreenReading: () -> Unit,
)

/**
 * Adding or editing a trigger (ADR 0038): one form, read top to bottom. App, what to watch for, the words or the time,
 * then what to do, and a label. Fields that don't apply are left out.
 */
@Composable
fun TriggerFormScreen(initial: Trigger?, apps: List<FeedCandidate>, screenReading: Boolean, actions: TriggerFormActions) {
    var packageName by rememberSaveable { mutableStateOf(initial?.packageName.orEmpty()) }
    var appLabel by rememberSaveable { mutableStateOf(initial?.appLabel.orEmpty()) }
    var watch by rememberSaveable { mutableStateOf(initial?.watch ?: Watch.NotificationWords) }
    var words by rememberSaveable { mutableStateOf(initial?.words.orEmpty().joinToString(", ")) }
    var timing by rememberSaveable { mutableStateOf(initial?.timing ?: ScreenTiming.OnLeaving) }
    var byMinute by rememberSaveable { mutableStateOf(initial?.by?.let { it.hour * 60 + it.minute }) }
    var days by rememberSaveable { mutableStateOf((initial?.days ?: DayOfWeek.entries.toSet()).joinToString(",") { it.name }) }
    var ignore by rememberSaveable { mutableStateOf(initial?.ignore ?: false) }
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: LoopKind.Reminder) }
    var label by rememberSaveable { mutableStateOf(initial?.label.orEmpty()) }
    var picking by rememberSaveable { mutableStateOf(false) }

    if (picking) {
        BackHandler { picking = false }
        AppPicker(apps, onBack = { picking = false }) { app ->
            packageName = app.packageName
            appLabel = app.label
            picking = false
        }
        return
    }

    val chosenDays = days.split(',').mapNotNull { name -> DayOfWeek.entries.find { it.name == name } }.toSet()
    val canIgnore = watch != Watch.NotOpenedBy
    val ignoring = ignore && canIgnore
    val typed = Trigger.words(words)
    val missing = when {
        packageName.isEmpty() -> R.string.form_missing_app
        (watch == Watch.NotificationWords || watch == Watch.ScreenWords) && typed.isEmpty() -> R.string.form_missing_words
        watch == Watch.NotOpenedBy && (byMinute == null || chosenDays.isEmpty()) -> R.string.form_missing_time
        else -> null
    }
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        item { ScreenHeader(stringResource(if (initial == null) R.string.form_new else R.string.form_edit), actions.onBack) }
        item {
            SectionTitle(stringResource(R.string.form_app))
            if (packageName.isEmpty()) {
                PrimaryButton(stringResource(R.string.form_app_choose)) { picking = true }
            } else {
                SecondaryButton(stringResource(R.string.form_app_change, appLabel)) { picking = true }
            }
        }
        item {
            SectionTitle(stringResource(R.string.form_watch))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(stringResource(R.string.form_watch_words), watch == Watch.NotificationWords) { watch = Watch.NotificationWords }
                Choice(stringResource(if (ignoring) R.string.form_watch_everything else R.string.form_watch_any), watch == Watch.AnyNotification) { watch = Watch.AnyNotification }
                Choice(stringResource(R.string.form_watch_screen), watch == Watch.ScreenWords) { watch = Watch.ScreenWords }
                Choice(stringResource(R.string.form_watch_time), watch == Watch.NotOpenedBy) { watch = Watch.NotOpenedBy }
            }
        }
        if (watch == Watch.NotificationWords || watch == Watch.ScreenWords) {
            item {
                SectionTitle(stringResource(R.string.form_words))
                Text(stringResource(R.string.form_words_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
                Spacer(Modifier.heightIn(min = 12.dp))
                FormField(words, { words = it }, stringResource(R.string.form_words_label), stringResource(R.string.form_words_hint), KeyboardCapitalization.None)
            }
        }
        if (watch == Watch.ScreenWords) {
            item {
                SectionTitle(stringResource(R.string.form_when))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice(stringResource(R.string.form_when_leaving), timing == ScreenTiming.OnLeaving) { timing = ScreenTiming.OnLeaving }
                    Choice(stringResource(R.string.form_when_shown), timing == ScreenTiming.AsSoonAsShown) { timing = ScreenTiming.AsSoonAsShown }
                }
                if (!screenReading) {
                    Text(stringResource(R.string.form_needs_screen), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    SecondaryButton(stringResource(R.string.form_turn_on), onClick = actions.onTurnOnScreenReading)
                }
            }
        }
        if (watch == Watch.NotOpenedBy) {
            item {
                SectionTitle(stringResource(R.string.form_time))
                Text(stringResource(R.string.form_time_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
                Spacer(Modifier.heightIn(min = 12.dp))
                val chosen = byMinute
                val pick = {
                    val start = chosen ?: (21 * 60)
                    TimePickerDialog(context, { _, hour, minute -> byMinute = hour * 60 + minute }, start / 60, start % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
                }
                if (chosen == null) {
                    PrimaryButton(stringResource(R.string.form_time_pick), onClick = pick)
                } else {
                    SecondaryButton(stringResource(R.string.form_time_change, clock(LocalTime.of(chosen / 60, chosen % 60))), onClick = pick)
                }
                SectionTitle(stringResource(R.string.form_days))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        DayChip(day, day in chosenDays) { on -> days = (if (on) chosenDays + day else chosenDays - day).joinToString(",") { it.name } }
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.form_then))
            Text(stringResource(R.string.form_then_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
            Spacer(Modifier.heightIn(min = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Trigger.kinds.forEach { k ->
                    Choice(stringResource(doName(k, ignore = false)), !ignoring && kind == k) {
                        kind = k
                        ignore = false
                    }
                }
                if (canIgnore) Choice(stringResource(R.string.trigger_do_ignore), ignoring) { ignore = true }
            }
        }
        if (!ignoring) {
            item {
                SectionTitle(stringResource(R.string.form_label))
                Text(stringResource(R.string.form_label_body), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
                Spacer(Modifier.heightIn(min = 12.dp))
                FormField(label, { label = it }, stringResource(R.string.form_label_field), stringResource(R.string.form_label_hint), KeyboardCapitalization.Sentences)
            }
        }
        item {
            Spacer(Modifier.heightIn(min = 24.dp))
            if (missing != null) Text(stringResource(missing), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted, modifier = Modifier.padding(bottom = 8.dp))
            PrimaryButton(stringResource(R.string.form_save), enabled = missing == null) {
                val minute = byMinute
                actions.onSave(
                    Trigger(
                        id = initial?.id ?: UUID.randomUUID().toString().take(8),
                        packageName = packageName,
                        appLabel = appLabel,
                        watch = watch,
                        words = if (watch == Watch.NotificationWords || watch == Watch.ScreenWords) typed else emptyList(),
                        timing = timing,
                        by = minute?.takeIf { watch == Watch.NotOpenedBy }?.let { LocalTime.of(it / 60, it % 60) },
                        days = chosenDays,
                        kind = kind,
                        ignore = ignoring,
                        label = label.trim().takeIf { it.isNotEmpty() && !ignoring },
                        on = initial?.on ?: true,
                    ),
                )
            }
            if (initial != null) {
                Spacer(Modifier.heightIn(min = 12.dp))
                SecondaryButton(stringResource(R.string.form_delete)) { actions.onDelete(initial.id) }
            }
            Spacer(Modifier.heightIn(min = 32.dp))
        }
    }
}

/** A day of the week to check, which TalkBack reads by its full name as a checkbox. */
@Composable
private fun DayChip(day: DayOfWeek, checked: Boolean, onChange: (Boolean) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    val full = day.getDisplayName(TextStyle.FULL, locale)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (checked) Tone.Ink else Tone.Card)
            .border(1.dp, if (checked) Tone.Ink else Tone.Line, RoundedCornerShape(24.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .semantics { contentDescription = full }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // TalkBack reads the full name from the chip, not "Mon" as well.
        Text(
            day.getDisplayName(TextStyle.SHORT, locale),
            style = MaterialTheme.typography.labelLarge,
            color = if (checked) Tone.Paper else Tone.Ink,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun FormField(value: String, onChange: (String) -> Unit, label: String, hint: String, capitalization: KeyboardCapitalization) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(hint) },
        keyboardOptions = KeyboardOptions(capitalization = capitalization, autoCorrectEnabled = false),
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

/** Every launcher app, with a search box, for the trigger's App field. */
@Composable
private fun AppPicker(apps: List<FeedCandidate>, onBack: () -> Unit, onPick: (FeedCandidate) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }.sortedBy { it.label.lowercase() }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        item { ScreenHeader(stringResource(R.string.form_app_choose), onBack) }
        item {
            FormField(query, { query = it }, stringResource(R.string.form_app_search), "", KeyboardCapitalization.None)
            Spacer(Modifier.heightIn(min = 8.dp))
        }
        items(shown, key = { it.packageName }) { app ->
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { onPick(app) }
                    .padding(vertical = 16.dp),
            )
        }
    }
}
