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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
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
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
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
    val pane = stringResource(R.string.overlay_chip_pane)
    val keep = stringResource(R.string.overlay_chip_keep)
    val notThis = stringResource(R.string.overlay_chip_not_this)
    val open = stringResource(R.string.overlay_open)

    Column(
        Modifier
            .fillMaxWidth()
            .swipeAway(chip.loopId) { onDismiss(chip.loopId) }
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
                    .combinedClickable(
                        onClickLabel = chip.action ?: open,
                        role = Role.Button,
                        onLongClickLabel = notThis,
                        onLongClick = { onDismiss(chip.loopId) },
                    ) { onAct(chip.loopId) },
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
                val source = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(48.dp)
                        .pressFeedback(source)
                        .clip(CircleShape)
                        .background(Brand.Marigold)
                        .clickable(interactionSource = source, indication = ripple(), role = Role.Button) { onHold(chip.loopId) }
                        .semantics { contentDescription = keep },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(26.dp))
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
    val pane = stringResource(R.string.overlay_pill_pane)
    val label = stringResource(R.string.overlay_pill_label)
    val notNow = stringResource(R.string.overlay_pill_not_now)
    Row(
        Modifier
            .fillMaxWidth()
            .swipeAway(pill.loopId) { onDismiss(pill.loopId) }
            .combinedClickable(
                onClickLabel = pill.verb,
                role = Role.Button,
                onLongClickLabel = notNow,
                onLongClick = { onDismiss(pill.loopId) },
            ) { onAct(pill.loopId) }
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
            Text(label, color = PaperDim, fontFamily = InstrumentSans, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
            Text(pill.task, color = Brand.Paper, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        VerbButton(pill.verb, description = "${pill.verb}: ${pill.task}") { onAct(pill.loopId) }
    }
}

@Composable
private fun VerbButton(verb: String, description: String = verb, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .sizeIn(minWidth = 64.dp, minHeight = 48.dp)
            .pressFeedback(source)
            .clip(Capsule)
            .background(Brand.Marigold)
            .clickable(interactionSource = source, indication = ripple(), role = Role.Button, onClick = onClick)
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

/**
 * Swipe sideways to dismiss: past a third of the width, or a quick flick at any distance.
 * Long-press does the same for anyone who can't drag (WCAG 2.5.7).
 */
@Composable
private fun Modifier.swipeAway(key: Any, onDismiss: () -> Unit): Modifier {
    val offset = remember(key) { Animatable(0f) }
    var width by remember(key) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val state = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } }
    return this
        .onSizeChanged { width = it.width }
        .graphicsLayer {
            translationX = offset.value
            alpha = 1f - (abs(offset.value) / size.width.coerceAtLeast(1f)).coerceAtMost(0.8f)
        }
        .draggable(
            state = state,
            orientation = Orientation.Horizontal,
            onDragStopped = { velocity ->
                val travel = width.coerceAtLeast(1).toFloat()
                if (abs(offset.value) > travel / 3 || abs(velocity) > FLING_VELOCITY) {
                    offset.animateTo(if (offset.value + velocity >= 0) travel else -travel, tween(180, easing = EaseOut))
                    onDismiss()
                } else {
                    offset.animateTo(0f, tween(200, easing = EaseOut))
                }
            },
        )
}

/** Scales to 0.97 while pressed, so a tap visibly lands. */
@Composable
private fun Modifier.pressFeedback(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120, easing = EaseOut), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

private const val FLING_VELOCITY = 1200f
