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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.ui.theme.Brand

/** Hairline and muted text tones derived from the brand ink. */
object Tone {
    val Muted: Color = Brand.Ink.copy(alpha = 0.62f)
    val Line: Color = Brand.Ink.copy(alpha = 0.12f)
    val Card: Color = Color(0xFFFFFDF8)
    val Done: Color = Color(0xFF2E9E5B)
    val MarigoldWash: Color = Color(0xFFFFEBC2)
}

@Composable
fun Glyph(size: Int = 32) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brand.Marigold), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_glyph), contentDescription = null, modifier = Modifier.size((size * 0.6).dp))
    }
}

/** The wordmark row, with optional text actions on the right. */
@Composable
fun Masthead(actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
            Glyph()
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** A screen title with a back control, for the screens under Recap. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.nav_back), onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("←", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics { })
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    }
}

@Composable
fun TextAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) Brand.Ink else Brand.Ink.copy(alpha = 0.3f))
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Brand.Paper, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Brand.Ink.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Tone.Muted,
        modifier = Modifier.padding(top = 28.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** A whole-row switch: one TalkBack stop that reads the title, body and state, and toggles on double-tap. */
@Composable
fun SwitchRow(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = Tone.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Brand.Ink,
                checkedThumbColor = Brand.Marigold,
                uncheckedTrackColor = Tone.Card,
                uncheckedBorderColor = Brand.Ink.copy(alpha = 0.4f),
                uncheckedThumbColor = Brand.Ink.copy(alpha = 0.55f),
            ),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
