package com.sweepiesoftie.earmark.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.core.ChipCard
import com.sweepiesoftie.earmark.core.LoopId
import com.sweepiesoftie.earmark.core.PillCard
import com.sweepiesoftie.earmark.ui.theme.Brand
import com.sweepiesoftie.earmark.ui.theme.InstrumentSans
import kotlin.math.abs

/** Ease-out for everything that enters; motion stays inside 200–300 ms. */
private val EaseOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Capsule = RoundedCornerShape(28.dp)
private val PaperDim = Brand.Paper.copy(alpha = 0.72f)

private fun enter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) fadeIn(tween(200)) else slideInVertically(tween(280, easing = EaseOut)) { -it / 2 } + fadeIn(tween(220))

private fun exit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) fadeOut(tween(200)) else slideOutVertically(tween(220, easing = EaseOut)) { -it / 3 } + fadeOut(tween(200))

@Composable
fun TopOverlay(
    state: OverlayUi,
    onHold: (LoopId) -> Unit,
    onChipAct: (LoopId) -> Unit,
    onChipDismiss: (LoopId) -> Unit,
    onChipFaded: (LoopId) -> Unit,
    onPillAct: (LoopId) -> Unit,
    onPillDismiss: (LoopId) -> Unit,
) {
    // Remember the last card so it can animate out after the state clears it.
    val lastChip = remember { arrayOfNulls<ChipCard>(1) }
    val lastPill = remember { arrayOfNulls<PillCard>(1) }
    state.chip?.let { lastChip[0] = it }
    state.pill?.let { lastPill[0] = it }

    val chipShown = remember { MutableTransitionState(false) }
    val pillShown = remember { MutableTransitionState(false) }
    SideEffect {
        chipShown.targetState = state.chip != null
        pillShown.targetState = state.pill != null
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        AnimatedVisibility(chipShown, enter = enter(state.reduceMotion), exit = exit(state.reduceMotion)) {
            lastChip[0]?.let { chip ->
                Chip(chip, state.chipTimeoutMillis, state.reduceMotion, onHold, onChipAct, onChipDismiss, onChipFaded)
            }
        }
        AnimatedVisibility(pillShown, enter = enter(state.reduceMotion), exit = exit(state.reduceMotion)) {
            lastPill[0]?.let { pill -> Pill(pill, onPillAct, onPillDismiss) }
        }
    }
}

/** The marigold disc with the ↶ glyph that opens every Earmark surface. */
@Composable
private fun Glyph(size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Brand.Marigold),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_glyph), contentDescription = null, modifier = Modifier.size((size * 0.6).dp))
    }
}

@Composable
private fun Chip(
    chip: ChipCard,
    timeoutMillis: Long,
    reduceMotion: Boolean,
    onHold: (LoopId) -> Unit,
    onAct: (LoopId) -> Unit,
    onDismiss: (LoopId) -> Unit,
    onFaded: (LoopId) -> Unit,
) {
    val fade = remember(chip.loopId) { Animatable(1f) }
    LaunchedEffect(chip.loopId) {
        fade.animateTo(0f, tween(timeoutMillis.toInt(), easing = LinearEasing))
        onFaded(chip.loopId)
    }
    var dragX by remember(chip.loopId) { mutableFloatStateOf(0f) }
    val pane = stringResource(R.string.overlay_chip_pane)
    val keep = stringResource(R.string.overlay_chip_keep)
    val notThis = stringResource(R.string.overlay_chip_not_this)
    val open = stringResource(R.string.overlay_open)

    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = dragX
                alpha = 1f - (abs(dragX) / 600f).coerceAtMost(0.7f)
            }
            .pointerInput(chip.loopId) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (abs(dragX) > 160f) onDismiss(chip.loopId) else dragX = 0f },
                    onDragCancel = { dragX = 0f },
                ) { _, delta -> dragX += delta }
            }
            .clip(Capsule)
            .background(Brand.Ink)
            .semantics(mergeDescendants = false) {
                paneTitle = pane
                customActions = listOf(
                    CustomAccessibilityAction(notThis) { onDismiss(chip.loopId); true },
                )
            },
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(Capsule)
                    .clickable(onClickLabel = chip.action ?: open, role = Role.Button) { onAct(chip.loopId) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(chip.title, color = Brand.Paper, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(chip.subtitle, color = PaperDim, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (chip.action != null) {
                VerbButton(chip.action!!) { onAct(chip.loopId) }
            } else {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brand.Marigold)
                        .clickable(role = Role.Button) { onHold(chip.loopId) }
                        .semantics { contentDescription = keep },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = Brand.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics { })
                }
            }
        }
        // The fade timer: a thin line that drains as the chip's time runs out.
        Box(
            Modifier
                .padding(start = 60.dp, end = 64.dp, bottom = 10.dp)
                .fillMaxWidth()
                .height(2.dp)
                .clearAndSetSemantics { }
                .drawBehind {
                    drawLine(Brand.Paper.copy(alpha = 0.18f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
                    val progress = if (reduceMotion) 1f else fade.value
                    drawLine(Brand.Marigold, Offset(0f, size.height / 2), Offset(size.width * progress, size.height / 2), size.height, StrokeCap.Round)
                },
        )
    }
}

@Composable
private fun Pill(pill: PillCard, onAct: (LoopId) -> Unit, onDismiss: (LoopId) -> Unit) {
    var dragY by remember(pill.loopId) { mutableFloatStateOf(0f) }
    var dragX by remember(pill.loopId) { mutableFloatStateOf(0f) }
    val pane = stringResource(R.string.overlay_pill_pane)
    val label = stringResource(R.string.overlay_pill_label)
    val notNow = stringResource(R.string.overlay_pill_not_now)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = dragY.coerceAtMost(0f)
                translationX = dragX
                alpha = 1f - ((abs(dragX) + abs(dragY.coerceAtMost(0f))) / 500f).coerceAtMost(0.7f)
            }
            .pointerInput(pill.loopId) {
                detectVerticalDragGestures(
                    onDragEnd = { if (dragY < -60f) onDismiss(pill.loopId) else dragY = 0f },
                    onDragCancel = { dragY = 0f },
                ) { _, delta -> dragY += delta }
            }
            .pointerInput(pill.loopId) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (abs(dragX) > 160f) onDismiss(pill.loopId) else dragX = 0f },
                    onDragCancel = { dragX = 0f },
                ) { _, delta -> dragX += delta }
            }
            .clip(Capsule)
            .background(Brand.Ink)
            .border(1.dp, Brand.Marigold.copy(alpha = 0.35f), Capsule)
            .semantics {
                paneTitle = pane
                customActions = listOf(CustomAccessibilityAction(notNow) { onDismiss(pill.loopId); true })
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph()
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { },
        ) {
            Text(label, color = PaperDim, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            Text(pill.task, color = Brand.Paper, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        VerbButton(pill.verb, description = "${pill.verb}: ${pill.task}") { onAct(pill.loopId) }
    }
}

@Composable
private fun VerbButton(verb: String, description: String = verb, onClick: () -> Unit) {
    Box(
        Modifier
            .sizeIn(minWidth = 64.dp, minHeight = 48.dp)
            .clip(Capsule)
            .background(Brand.Marigold)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(verb, color = Brand.Ink, style = MaterialTheme.typography.labelLarge, maxLines = 2, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
fun DotOverlay(visible: Boolean, reduceMotion: Boolean) {
    val shown = remember { MutableTransitionState(false) }
    SideEffect { shown.targetState = visible }
    AnimatedVisibility(
        shown,
        enter = if (reduceMotion) fadeIn(tween(200)) else scaleIn(tween(240, easing = EaseOut), initialScale = 2.4f) + fadeIn(tween(200)),
        exit = fadeOut(tween(200)) + if (reduceMotion) ExitTransition.None else scaleOut(tween(200), targetScale = 0.4f),
    ) {
        // Decorative only: the window is not touchable and TalkBack has nothing to act on here.
        Box(Modifier.padding(4.dp).size(10.dp).clip(CircleShape).background(Brand.Marigold).clearAndSetSemantics { })
    }
}

@Composable
fun ReplyStripOverlay(strip: ReplyStrip?, reduceMotion: Boolean, onReply: (LoopId, String) -> Unit, onClose: () -> Unit) {
    val last = remember { arrayOfNulls<ReplyStrip>(1) }
    strip?.let { last[0] = it }
    val shown = remember { MutableTransitionState(false) }
    SideEffect { shown.targetState = strip != null }
    val pane = stringResource(R.string.overlay_strip_pane)
    val close = stringResource(R.string.overlay_strip_close)
    AnimatedVisibility(
        shown,
        enter = if (reduceMotion) fadeIn(tween(200)) else slideInVertically(tween(260, easing = EaseOut)) { it / 2 } + fadeIn(tween(220)),
        exit = fadeOut(tween(200)),
    ) {
        val current = last[0] ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(Capsule)
                .background(Brand.Ink.copy(alpha = 0.94f))
                .semantics { paneTitle = pane }
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = close },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(size = 32)
            }
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                current.replies.forEachIndexed { index, reply ->
                    val first = index == 0
                    val sendLabel = stringResource(R.string.overlay_strip_send, reply)
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(Capsule)
                            .then(if (first) Modifier.background(Brand.Marigold) else Modifier.border(1.5.dp, Brand.Paper.copy(alpha = 0.5f), Capsule))
                            .clickable(role = Role.Button) { onReply(current.loopId, reply) }
                            .semantics { contentDescription = sendLabel; onClick(label = sendLabel) { onReply(current.loopId, reply); true } }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            reply,
                            color = if (first) Brand.Ink else Brand.Paper,
                            fontFamily = InstrumentSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
            }
        }
    }
}
