package com.sweepiesoftie.earmark.overlay

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.core.ChipCard
import com.sweepiesoftie.earmark.core.LoopId
import com.sweepiesoftie.earmark.core.PillCard
import com.sweepiesoftie.earmark.ui.Tone
import com.sweepiesoftie.earmark.ui.theme.Brand
import com.sweepiesoftie.earmark.ui.theme.InstrumentSans
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Ease-out for everything that enters or leaves, fades included; motion stays inside 200–300 ms. */
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val Capsule = RoundedCornerShape(28.dp)
private val PaperDim = Brand.Paper.copy(alpha = 0.72f)

/** The capsules stay ink in both themes; in dark mode a hairline keeps them apart from a dark app behind. */
@Composable
private fun Modifier.darkEdge(): Modifier =
    if (Tone.Dark) border(1.dp, Brand.Paper.copy(alpha = 0.16f), Capsule) else this

/** The chip shows at many unlocks a day, so its entrance is quick. */
private fun enter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) fadeIn(tween(200, easing = EaseOut))
    else slideInVertically(tween(220, easing = EaseOut)) { -it / 2 } + fadeIn(tween(200, easing = EaseOut))

private fun exit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) fadeOut(tween(200, easing = EaseOut))
    else slideOutVertically(tween(200, easing = EaseOut)) { -it / 3 } + fadeOut(tween(180, easing = EaseOut))

/** ✓ shrinks the chip toward the dot at the top left, so the hold reads as one motion. */
private fun exitIntoDot(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) fadeOut(tween(200, easing = EaseOut))
    else scaleOut(tween(200, easing = EaseOut), targetScale = 0.9f, transformOrigin = TransformOrigin(0f, 0f)) + fadeOut(tween(180, easing = EaseOut))

/** A pill taking the chip's place: the chip just steps aside, so two capsules never cross. */
private val makeWayForPill: ExitTransition = fadeOut(tween(120, easing = EaseOut))

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
    // ✓ hides the chip and shows the dot in the same frame; that pair is the only way the dot appears.
    val chipExit = when {
        state.pill != null -> makeWayForPill
        state.chip == null && state.dot -> exitIntoDot(state.reduceMotion)
        else -> exit(state.reduceMotion)
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        AnimatedVisibility(chipShown, enter = enter(state.reduceMotion), exit = chipExit) {
            lastChip[0]?.let { chip ->
                Chip(chip, state.chipTimeoutMillis, state.reduceMotion, state.a11yFocused, onHold, onChipAct, onChipDismiss, onChipFaded)
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
    a11yFocused: Boolean,
    onHold: (LoopId) -> Unit,
    onAct: (LoopId) -> Unit,
    onDismiss: (LoopId) -> Unit,
    onFaded: (LoopId) -> Unit,
) {
    val body = remember { MutableInteractionSource() }
    val check = remember { MutableInteractionSource() }
    val swipe = remember(chip.loopId) { SwipeAway() }
    val bodyPressed by body.collectIsPressedAsState()
    val checkPressed by check.collectIsPressedAsState()
    // The timer waits while a finger or TalkBack is on the chip, so it never leaves mid-gesture.
    val paused = bodyPressed || checkPressed || swipe.dragging || a11yFocused

    val fade = remember(chip.loopId) { Animatable(1f) }
    var remaining by remember(chip.loopId) { mutableLongStateOf(timeoutMillis) }
    LaunchedEffect(chip.loopId, paused) {
        if (paused) return@LaunchedEffect
        // The line is only a picture of the timer. The timer itself is real time, because with
        // animations turned off Android finishes every animation instantly.
        launch { fade.animateTo(0f, tween(remaining.toInt(), easing = LinearEasing)) }
        while (remaining > 0) {
            val start = SystemClock.uptimeMillis()
            delay(minOf(remaining, TIMER_STEP_MILLIS))
            remaining -= SystemClock.uptimeMillis() - start
        }
        onFaded(chip.loopId)
    }
    val pane = stringResource(R.string.overlay_chip_pane)
    val keep = stringResource(R.string.overlay_chip_keep)
    val notThis = stringResource(R.string.overlay_chip_not_this)
    val open = stringResource(R.string.overlay_open)

    Column(
        Modifier
            .fillMaxWidth()
            .swipeAway(swipe, upToo = false) { onDismiss(chip.loopId) }
            .clip(Capsule)
            .background(Brand.Ink)
            .darkEdge()
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
                        interactionSource = body,
                        indication = ripple(),
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
                VerbButton(chip.action!!, source = check) { onAct(chip.loopId) }
            } else {
                Box(
                    Modifier
                        .size(48.dp)
                        .pressFeedback(check)
                        .clip(CircleShape)
                        .background(Brand.Marigold)
                        .clickable(interactionSource = check, indication = ripple(), role = Role.Button) { onHold(chip.loopId) }
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
    val notNow = stringResource(R.string.overlay_pill_not_now)
    val swipe = remember(pill.loopId) { SwipeAway() }
    Row(
        Modifier
            .fillMaxWidth()
            // Sideways like a notification, or up the way it leaves.
            .swipeAway(swipe, upToo = true) { onDismiss(pill.loopId) }
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
            Text(pill.label, color = PaperDim, fontFamily = InstrumentSans, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
            Text(pill.task, color = Brand.Paper, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        VerbButton(pill.verb, description = "${pill.verb}: ${pill.task}") { onAct(pill.loopId) }
    }
}

@Composable
private fun VerbButton(
    verb: String,
    description: String = verb,
    source: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
) {
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
        // Starts as the chip finishes shrinking toward it, so the two read as one hand-off.
        enter = if (reduceMotion) {
            fadeIn(tween(200, easing = EaseOut))
        } else {
            scaleIn(tween(220, delayMillis = DOT_DELAY_MILLIS, easing = EaseOut), initialScale = 0.6f) +
                fadeIn(tween(200, delayMillis = DOT_DELAY_MILLIS, easing = EaseOut))
        },
        exit = if (reduceMotion) {
            fadeOut(tween(200, easing = EaseOut))
        } else {
            scaleOut(tween(200, easing = EaseOut), targetScale = 0.6f) + fadeOut(tween(200, easing = EaseOut))
        },
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
    val close = stringResource(R.string.overlay_strip_close)
    AnimatedVisibility(
        shown,
        enter = if (reduceMotion) {
            fadeIn(tween(200, easing = EaseOut))
        } else {
            slideInVertically(tween(240, easing = EaseOut)) { it / 2 } + fadeIn(tween(200, easing = EaseOut))
        },
        exit = fadeOut(tween(200, easing = EaseOut)),
    ) {
        val current = last[0] ?: return@AnimatedVisibility
        val pane = stringResource(R.string.overlay_strip_pane, current.to)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(Capsule)
                .background(Brand.Ink.copy(alpha = 0.94f))
                .darkEdge()
                .semantics { paneTitle = pane }
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val closeSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(48.dp)
                    .pressFeedback(closeSource)
                    .clip(CircleShape)
                    .clickable(interactionSource = closeSource, indication = ripple(), role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = close },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(size = 32)
            }
            // The strip stays while you move between chats in the app, so it says who a reply goes to.
            Text(
                stringResource(R.string.overlay_strip_to, current.to),
                color = PaperDim,
                fontFamily = InstrumentSans,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // The pane title and every send button already name them for TalkBack.
                modifier = Modifier.widthIn(max = 96.dp).padding(end = 8.dp).clearAndSetSemantics { },
            )
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                current.replies.forEachIndexed { index, reply ->
                    val first = index == 0
                    val sendLabel = stringResource(R.string.overlay_strip_send, current.to, reply)
                    val source = remember(reply) { MutableInteractionSource() }
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .pressFeedback(source)
                            .clip(Capsule)
                            .then(if (first) Modifier.background(Brand.Marigold) else Modifier.border(1.5.dp, Brand.Paper.copy(alpha = 0.5f), Capsule))
                            .clickable(interactionSource = source, indication = ripple(), role = Role.Button) { onReply(current.loopId, reply) }
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

/** Drag state for swipe-to-dismiss. The chip reads [dragging] to hold its timer mid-swipe. */
private class SwipeAway {
    val x = Animatable(0f)
    val y = Animatable(0f)
    var dragging by mutableStateOf(false)
}

/**
 * Swipe sideways (or, with [upToo], upward) to dismiss: past a third of the size, or a flick
 * faster than ~0.11 px/ms (110 dp/s) at any distance. A flick leaves the way it was thrown.
 * Long-press does the same for anyone who can't drag (WCAG 2.5.7).
 */
@Composable
private fun Modifier.swipeAway(swipe: SwipeAway, upToo: Boolean, onDismiss: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val flick = with(LocalDensity.current) { FLICK_DP_PER_SECOND.dp.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val horizontal = rememberDraggableState { delta -> scope.launch { swipe.x.snapTo(swipe.x.value + delta) } }
    // Downward drags meet friction instead of a wall; only up dismisses.
    val vertical = rememberDraggableState { delta ->
        scope.launch { swipe.y.snapTo(swipe.y.value + if (swipe.y.value + delta > 0) delta * 0.25f else delta) }
    }

    suspend fun release(axis: Animatable<Float, *>, extent: Int, velocity: Float, upOnly: Boolean) {
        swipe.dragging = false
        val flung = abs(velocity) > flick
        val direction = if (flung) sign(velocity) else sign(axis.value)
        val far = abs(axis.value) > extent / 3f
        if ((flung || far) && direction != 0f && (!upOnly || direction < 0f)) {
            onDismiss()
            axis.animateTo(direction * extent.coerceAtLeast(1), tween(180, easing = EaseOut))
        } else {
            axis.animateTo(0f, tween(200, easing = EaseOut))
        }
    }

    val base = this
        .onSizeChanged { size = it }
        .graphicsLayer {
            translationX = swipe.x.value
            translationY = swipe.y.value
            val gone = abs(swipe.x.value) / this.size.width.coerceAtLeast(1f) + abs(swipe.y.value.coerceAtMost(0f)) / this.size.height.coerceAtLeast(1f)
            alpha = 1f - gone.coerceAtMost(0.8f)
        }
        .draggable(
            state = horizontal,
            orientation = Orientation.Horizontal,
            onDragStarted = { swipe.dragging = true },
            onDragStopped = { velocity -> release(swipe.x, size.width, velocity, upOnly = false) },
        )
    return if (!upToo) {
        base
    } else {
        base.draggable(
            state = vertical,
            orientation = Orientation.Vertical,
            onDragStarted = { swipe.dragging = true },
            onDragStopped = { velocity -> release(swipe.y, size.height, velocity, upOnly = true) },
        )
    }
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

private const val FLICK_DP_PER_SECOND = 110f
private const val TIMER_STEP_MILLIS = 100L
private const val DOT_DELAY_MILLIS = 120
