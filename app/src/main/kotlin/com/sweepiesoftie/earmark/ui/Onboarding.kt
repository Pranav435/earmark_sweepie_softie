package com.sweepiesoftie.earmark.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.core.Note
import com.sweepiesoftie.earmark.ui.theme.InstrumentSerif

data class PermissionState(
    val notifications: Boolean = false,
    val usage: Boolean = false,
    val overlay: Boolean = false,
    val calendar: Boolean = false,
    val contacts: Boolean = false,
    val postNotifications: Boolean = false,
    val battery: Boolean = false,
) {
    val required: Boolean get() = notifications && usage && overlay

    /** Where setup picks up: the first required step still missing, or the extras once they're all allowed. */
    val firstMissing: SetupStep get() = SetupStep.required.firstOrNull { !granted(it) } ?: SetupStep.Extras

    fun granted(step: SetupStep): Boolean = when (step) {
        SetupStep.Notifications -> notifications
        SetupStep.Usage -> usage
        SetupStep.Overlay -> overlay
        SetupStep.Battery -> battery
        SetupStep.Intro, SetupStep.Extras -> true
    }
}

/** Setup, one screen at a time (ADR 0032): what Earmark does, each permission it needs, then the optional ones. */
enum class SetupStep {
    Intro, Notifications, Usage, Overlay, Battery, Extras;

    companion object {
        val required: List<SetupStep> = listOf(Notifications, Usage, Overlay, Battery)
    }
}

class OnboardingActions(
    val onNotificationAccess: () -> Unit,
    val onUsageAccess: () -> Unit,
    val onOverlay: () -> Unit,
    val onBattery: () -> Unit,
    val onCalendar: () -> Unit,
    val onContacts: () -> Unit,
    val onPostNotifications: () -> Unit,
    val onAppInfo: () -> Unit,
    val onDone: () -> Unit,
)

@Composable
fun OnboardingScreen(state: PermissionState, start: SetupStep, actions: OnboardingActions) {
    var step by rememberSaveable(start) { mutableStateOf(start) }
    BackHandler(step != start) { step = SetupStep.entries[step.ordinal - 1] }
    val next = { if (step == SetupStep.Extras) actions.onDone() else step = SetupStep.entries[step.ordinal + 1] }
    AnimatedContent(step, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "setup") { shown ->
        when (shown) {
            SetupStep.Intro -> IntroStep(next)
            SetupStep.Extras -> ExtrasStep(state, actions, next)
            else -> PermissionStep(shown, state.granted(shown), actions, next)
        }
    }
}

/** The masthead and words scroll at large font sizes; the buttons stay at the bottom, in reach of a thumb. */
@Composable
private fun StepFrame(title: String, content: @Composable ColumnScope.() -> Unit, buttons: @Composable ColumnScope.() -> Unit) {
    // A new pane title per step, so TalkBack says which step it is as each one appears.
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp).semantics { paneTitle = title }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Masthead()
            Spacer(Modifier.heightIn(min = 32.dp))
            content()
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            buttons()
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, fontFamily = InstrumentSerif, fontSize = 36.sp, lineHeight = 40.sp, color = Tone.Ink, modifier = Modifier.semantics { heading() })
}

@Composable
private fun IntroStep(onNext: () -> Unit) {
    val title = stringResource(R.string.onboarding_title)
    StepFrame(
        title,
        content = {
            StepTitle(title)
            Spacer(Modifier.heightIn(min = 28.dp))
            val sample = Note(
                who = stringResource(R.string.onboarding_sample_who),
                words = stringResource(R.string.onboarding_sample_words),
                verb = stringResource(R.string.onboarding_sample_verb),
                person = true,
                packageName = null,
            )
            val why = stringResource(R.string.onboarding_sample_why)
            // A picture of what Earmark shows, so it reads as one sentence and does nothing when tapped.
            val spoken = stringResource(R.string.onboarding_sample, listOfNotNull(sample.who, sample.words, why).joinToString(". "))
            Box(Modifier.clearAndSetSemantics { contentDescription = spoken }) { NoteCard(sample, why, onVerb = {}) }
            Spacer(Modifier.heightIn(min = 28.dp))
            Text(stringResource(R.string.onboarding_intro_body), style = MaterialTheme.typography.bodyLarge, color = Tone.Muted)
            Spacer(Modifier.heightIn(min = 16.dp))
            Text(stringResource(R.string.onboarding_privacy), style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
        },
        buttons = { PrimaryButton(stringResource(R.string.onboarding_start), Modifier.fillMaxWidth(), onClick = onNext) },
    )
}

@Composable
private fun PermissionStep(step: SetupStep, granted: Boolean, actions: OnboardingActions, onNext: () -> Unit) {
    val (name, title, body) = when (step) {
        SetupStep.Notifications -> Triple(R.string.onboarding_notifications, R.string.onboarding_notifications_title, R.string.onboarding_notifications_body)
        SetupStep.Usage -> Triple(R.string.onboarding_usage, R.string.onboarding_usage_title, R.string.onboarding_usage_body)
        SetupStep.Overlay -> Triple(R.string.onboarding_overlay, R.string.onboarding_overlay_title, R.string.onboarding_overlay_body)
        else -> Triple(R.string.onboarding_battery, R.string.onboarding_battery_title, R.string.onboarding_battery_body)
    }
    val open = when (step) {
        SetupStep.Notifications -> actions.onNotificationAccess
        SetupStep.Usage -> actions.onUsageAccess
        SetupStep.Overlay -> actions.onOverlay
        else -> actions.onBattery
    }
    val heading = stringResource(title)
    val kicker = stringResource(R.string.onboarding_step, SetupStep.required.indexOf(step) + 1, SetupStep.required.size, stringResource(name))
    StepFrame(
        heading,
        content = {
            Text(kicker, style = MaterialTheme.typography.labelSmall, color = Tone.Muted)
            Spacer(Modifier.heightIn(min = 10.dp))
            StepTitle(heading)
            Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = Tone.Muted, modifier = Modifier.padding(top = 12.dp))
            if (granted) Allowed(Modifier.padding(top = 20.dp))
            if (step == SetupStep.Notifications && !granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp).clip(RoundedCornerShape(16.dp)).background(Tone.MarigoldWash).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.onboarding_restricted_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.onboarding_restricted_body), style = MaterialTheme.typography.bodyMedium)
                    SecondaryButton(stringResource(R.string.onboarding_app_info), onClick = actions.onAppInfo)
                }
            }
        },
        buttons = {
            if (granted) {
                PrimaryButton(stringResource(R.string.onboarding_continue), Modifier.fillMaxWidth(), onClick = onNext)
            } else {
                val action = stringResource(if (step == SetupStep.Battery) R.string.onboarding_allow else R.string.onboarding_open_settings)
                PrimaryButton(action, Modifier.fillMaxWidth(), onClick = open)
                TextAction(stringResource(R.string.onboarding_not_now), onNext)
            }
        },
    )
}

@Composable
private fun ExtrasStep(state: PermissionState, actions: OnboardingActions, onDone: () -> Unit) {
    val heading = stringResource(R.string.onboarding_extras_title)
    val allow = stringResource(R.string.onboarding_allow)
    StepFrame(
        heading,
        content = {
            StepTitle(heading)
            Text(stringResource(R.string.onboarding_extras_body), style = MaterialTheme.typography.bodyLarge, color = Tone.Muted, modifier = Modifier.padding(top = 12.dp, bottom = 20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtraRow(stringResource(R.string.onboarding_calendar), stringResource(R.string.onboarding_calendar_body), state.calendar, allow, actions.onCalendar)
                ExtraRow(stringResource(R.string.onboarding_contacts), stringResource(R.string.onboarding_contacts_body), state.contacts, allow, actions.onContacts)
                ExtraRow(stringResource(R.string.onboarding_post), stringResource(R.string.onboarding_post_body), state.postNotifications, allow, actions.onPostNotifications)
            }
        },
        buttons = { PrimaryButton(stringResource(R.string.onboarding_done), Modifier.fillMaxWidth(), onClick = onDone) },
    )
}

@Composable
private fun Allowed(modifier: Modifier = Modifier) {
    Row(modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Tone.Done), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_check), contentDescription = null, colorFilter = ColorFilter.tint(Tone.Paper), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.onboarding_granted), style = MaterialTheme.typography.titleMedium, color = Tone.Done)
    }
}

@Composable
private fun ExtraRow(title: String, body: String, granted: Boolean, action: String, onAction: () -> Unit) {
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(action, color = Tone.Paper, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
