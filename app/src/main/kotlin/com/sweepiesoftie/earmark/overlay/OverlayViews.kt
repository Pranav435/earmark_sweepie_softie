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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.sweepiesoftie.earmark.core.Note
import com.sweepiesoftie.earmark.core.PillCard
import com.sweepiesoftie.earmark.ui.NoteCard
import com.sweepiesoftie.earmark.ui.NoteFill
import com.sweepiesoftie.earmark.ui.Tone
import com.sweepiesoftie.earmark.ui.pressFeedback
import com.sweepiesoftie.earmark.ui.theme.Brand
import com.sweepiesoftie.earmark.ui.theme.InstrumentSans
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Ease-out for everything that enters or leaves, fades included; motion stays inside 200–300 ms. */
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** The chip shows at many unlocks a day, so its entrance is quick. */
private fun enter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) fadeIn(tween(200, easing = EaseOut))
    else slideInVertically(tween(220, easing = EaseOut)) { -it / 2 } + fadeIn(tween(200, easing = EaseOut))

private fun exit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) fadeOut(tween(200, easing = EaseOut))
    else slideOutVertically(tween(200, easing = EaseOut)) { -it / 3 } + fadeOut(tween(180, easing = EaseOut))

/** A faded chip shrinks toward the dot at the top left, so Earmark holding it reads as one motion. */
private fun exitIntoDot(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) fadeOut(tween(200, easing = EaseOut))
    else scaleOut(tween(200, easing = EaseOut), targetScale = 0.9f, transformOrigin = TransformOrigin(0f, 0f)) + fadeOut(tween(180, easing = EaseOut))

/** A pill taking the chip's place: the chip just steps aside, so two notes never cross. */
private val makeWayForPill: ExitTransition = fadeOut(tween(120, easing = EaseOut))

@Composable
fun TopOverlay(
    state: OverlayUi,
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
    // A fade hides the chip and shows the dot in the same frame; that pair is the only way the dot appears.
    val chipExit = when {
        state.pill != null -> makeWayForPill
        state.chip == null && state.dot -> exitIntoDot(state.reduceMotion)
        else -> exit(state.reduceMotion)
    }
    // Room below the note for its shadow.
    Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
        AnimatedVisibility(chipShown, enter = enter(state.reduceMotion), exit = chipExit) {
            lastChip[0]?.let { chip ->
                Chip(chip, state.chipTimeoutMillis, state.reduceMotion, state.a11yFocused, onChipAct, onChipDismiss, onChipFaded)
            }
        }
        AnimatedVisibility(pillShown, enter = enter(state.reduceMotion), exit = exit(state.reduceMotion)) {
            lastPill[0]?.let { pill -> Pill(pill, onPillAct, onPillDismiss) }
        }
    }
}

/** What TalkBack says as a note appears: "Earmark: Rahul. “free at 5?” · 1 min ago. Here to reply?" */
@Composable
private fun spoken(note: Note, why: String): String =
    stringResource(R.string.overlay_pane, listOfNotNull(note.who, note.words, why).joinToString(". "))

@Composable
private fun Chip(
    chip: ChipCard,
    timeoutMillis: Long,
    reduceMotion: Boolean,
    a11yFocused: Boolean,
    onAct: (LoopId) -> Unit,
    onDismiss: (LoopId) -> Unit,
    onFaded: (LoopId) -> Unit,
) {
    val body = remember { MutableInteractionSource() }
    val verb = remember { MutableInteractionSource() }
    val swipe = remember(chip.loopId) { SwipeAway() }
    val bodyPressed by body.collectIsPressedAsState()
    val verbPressed by verb.collectIsPressedAsState()
    // The timer waits while a finger or TalkBack is on the chip, so it never leaves mid-gesture.
    val paused = bodyPressed || verbPressed || swipe.dragging || a11yFocused

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
    val pane = spoken(chip.note, chip.why)
    val notThis = stringResource(R.string.overlay_chip_not_this)
    val haptics = LocalHapticFeedback.current
    val line = Tone.Line

    NoteCard(
        note = chip.note,
        why = chip.why,
        modifier = Modifier.fillMaxWidth().swipeAway(swipe, upToo = false) { onDismiss(chip.loopId) },
        inner = Modifier
            .combinedClickable(
                interactionSource = body,
                indication = ripple(),
                onClickLabel = chip.task,
                role = Role.Button,
                onLongClickLabel = notThis,
                onLongClick = { onDismiss(chip.loopId) },
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onAct(chip.loopId)
            }
            .semantics {
                paneTitle = pane
                customActions = listOf(CustomAccessibilityAction(notThis) { onDismiss(chip.loopId); true })
            },
        verbSource = verb,
        onVerb = { onAct(chip.loopId) },
    ) {
        // The fade timer: a thin line under the words that drains as the chip's time runs out.
        Box(
            Modifier
                .padding(start = 68.dp, end = 20.dp, bottom = 10.dp)
                .fillMaxWidth()
                .height(2.dp)
                .clearAndSetSemantics { }
                .drawBehind {
                    drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
                    val progress = if (reduceMotion) 1f else fade.value
                    drawLine(Brand.Marigold, Offset(0f, size.height / 2), Offset(size.width * progress, size.height / 2), size.height, StrokeCap.Round)
                },
        )
    }
}

@Composable
private fun Pill(pill: PillCard, onAct: (LoopId) -> Unit, onDismiss: (LoopId) -> Unit) {
    val pane = spoken(pill.note, pill.why)
    val notNow = stringResource(R.string.overlay_pill_not_now)
    val swipe = remember(pill.loopId) { SwipeAway() }
    val haptics = LocalHapticFeedback.current
    NoteCard(
        note = pill.note,
        why = pill.why,
        // Sideways like a notification, or up the way it leaves.
        modifier = Modifier.fillMaxWidth().swipeAway(swipe, upToo = true) { onDismiss(pill.loopId) },
        inner = Modifier
            .combinedClickable(
                onClickLabel = pill.task,
                role = Role.Button,
                onLongClickLabel = notNow,
                onLongClick = { onDismiss(pill.loopId) },
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onAct(pill.loopId)
            }
            .semantics {
                paneTitle = pane
                customActions = listOf(CustomAccessibilityAction(notNow) { onDismiss(pill.loopId); true })
            },
        onVerb = { onAct(pill.loopId) },
    )
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
        val haptics = LocalHapticFeedback.current
        // The same note as the chip and pill, so the replies read as Earmark's, not the chat app's.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(Capsule)
                .background(NoteFill)
                .border(1.dp, Tone.Line, Capsule)
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
                CloseMark(Tone.Muted)
            }
            // The strip stays while you move between chats in the app, so it says who a reply goes to.
            Text(
                stringResource(R.string.overlay_strip_to, current.to),
                color = Tone.Muted,
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
                    val send = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onReply(current.loopId, reply)
                    }
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .pressFeedback(source)
                            .clip(Capsule)
                            .then(if (first) Modifier.background(Brand.Marigold) else Modifier.border(1.dp, Tone.Ink.copy(alpha = 0.3f), Capsule))
                            .clickable(interactionSource = source, indication = ripple(), role = Role.Button, onClick = send)
                            .semantics { contentDescription = sendLabel; onClick(label = sendLabel) { send(); true } }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            reply,
                            color = if (first) Brand.Ink else Tone.Ink,
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

private val Capsule = RoundedCornerShape(28.dp)

/** A small ×, drawn so it takes the theme's colour. */
@Composable
private fun CloseMark(color: Color) {
    Box(
        Modifier.size(14.dp).drawBehind {
            val stroke = 2.dp.toPx()
            drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        },
    )
}

/** Drag state for swipe-to-dismiss. The chip reads [dragging] to hold its timer mid-swipe. */
private class SwipeAway {
    val x = Animatable(0f)
    val y = Animatable(0f)
    var dragging by mutableStateOf(false)

    /** Past the point where letting go dismisses; a light tick marks each crossing. */
    var past = false
}

/**
 * Swipe sideways (or, with [upToo], upward) to dismiss: past a third of the size, or a flick
 * faster than ~0.11 px/ms (110 dp/s) at any distance. A flick leaves the way it was thrown.
 * Long-press does the same for anyone who can't drag (WCAG 2.5.7).
 */
@Composable
private fun Modifier.swipeAway(swipe: SwipeAway, upToo: Boolean, onDismiss: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val flick = with(LocalDensity.current) { FLICK_DP_PER_SECOND.dp.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun mark(far: Boolean) {
        if (far != swipe.past) {
            swipe.past = far
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
    val horizontal = rememberDraggableState { delta ->
        scope.launch {
            swipe.x.snapTo(swipe.x.value + delta)
            mark(abs(swipe.x.value) > size.width / 3f)
        }
    }
    // Downward drags meet friction instead of a wall; only up dismisses.
    val vertical = rememberDraggableState { delta ->
        scope.launch {
            swipe.y.snapTo(swipe.y.value + if (swipe.y.value + delta > 0) delta * 0.25f else delta)
            mark(-swipe.y.value > size.height / 3f)
        }
    }

    suspend fun release(axis: Animatable<Float, *>, extent: Int, velocity: Float, upOnly: Boolean) {
        swipe.dragging = false
        swipe.past = false
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

private const val FLICK_DP_PER_SECOND = 110f
private const val TIMER_STEP_MILLIS = 100L
private const val DOT_DELAY_MILLIS = 120
