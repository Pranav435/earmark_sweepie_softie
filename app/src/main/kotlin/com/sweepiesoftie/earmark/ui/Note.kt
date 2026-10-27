package com.sweepiesoftie.earmark.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.sweepiesoftie.earmark.AppGraph
import com.sweepiesoftie.earmark.core.Note
import com.sweepiesoftie.earmark.ui.theme.Brand
import com.sweepiesoftie.earmark.ui.theme.InstrumentSans
import com.sweepiesoftie.earmark.ui.theme.InstrumentSerif
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val NoteShape: RoundedCornerShape = RoundedCornerShape(22.dp)

/** Paper in light mode; at night a warm dark card, so it never glares over a dark feed (ADR 0032). */
val NoteFill @Composable get() = if (Tone.Dark) Tone.Card else Tone.Paper

private val Who = TextStyle(fontFamily = InstrumentSerif, fontSize = 22.sp, lineHeight = 26.sp)

/** Someone with no name but a phone number: Instrument Serif's 1 reads as an l, so they're set in the sans. */
fun isNumber(who: String): Boolean = who.none { it.isLetter() }

/** [serif] for a name; for a phone number, the sans a size smaller, so the two look the same size. */
fun nameStyle(who: String, serif: TextStyle): TextStyle =
    if (!isNumber(who)) serif else serif.copy(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = serif.fontSize * 0.85f)
private val Words = TextStyle(fontFamily = InstrumentSans, fontSize = 15.sp, lineHeight = 20.sp)

/** The small line in the friend's voice: "You came here to reply". */
private val Why = TextStyle(fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 20.sp)

/**
 * The friend's note that the chip and the pill are (ADR 0032): who it's from, their own words, a small line on why
 * it's here, and one verb. [modifier] moves the whole note (a swipe), so the shadow goes with it; [inner] is
 * applied inside the note's shape, so a ripple stays within it.
 */
@Composable
fun NoteCard(
    note: Note,
    why: String,
    modifier: Modifier = Modifier,
    inner: Modifier = Modifier,
    verbSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onVerb: () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier
            .shadow(10.dp, NoteShape, ambientColor = Brand.Ink, spotColor = Brand.Ink)
            .clip(NoteShape)
            .background(NoteFill)
            .border(1.dp, Tone.Line, NoteShape)
            .then(inner),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Face(note, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(note.who, style = nameStyle(note.who, Who), color = Tone.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                note.words?.let { Text(it, style = Words, color = Tone.Ink.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(why, style = Why, color = Tone.MarigoldText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            // The note itself is the one TalkBack stop and does the same, so the button is for touch only.
            VerbButton(note.verb, source = verbSource, modifier = Modifier.clearAndSetSemantics { }, onClick = onVerb)
        }
        footer()
    }
}

/** A marigold capsule with the loop's verb: Reply, Call back, Pay, Set it. A press scales it and clicks. */
@Composable
fun VerbButton(
    verb: String,
    modifier: Modifier = Modifier,
    description: String = verb,
    source: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .sizeIn(minWidth = 64.dp, minHeight = 48.dp)
            .pressFeedback(source)
            .clip(CircleShape)
            .background(Brand.Marigold)
            .clickable(interactionSource = source, indication = ripple(), role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .semantics { contentDescription = description }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(verb, color = Brand.Ink, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
    }
}

/** Scales to 0.97 while pressed, so a tap visibly lands. */
@Composable
fun Modifier.pressFeedback(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120, easing = PressEase), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

private val PressEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Who a note is from (ADR 0032): the photo the sender's app put in the notification, else their initial in the serif
 * on marigold. A bill, an alarm, a ride or a reminder shows its app's icon. The photo is read from the way back held
 * in memory, so it goes when the loop does, like the message text.
 */
@Composable
fun Face(note: Note, size: Dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val picture by produceState<ImageBitmap?>(null, note.faceKey, note.packageName, note.person) {
        value = withContext(Dispatchers.IO) { picture(context, note, px) }
    }
    val shown = picture
    val decorative = Modifier.size(size).clearAndSetSemantics { }
    when {
        shown != null && note.person -> Image(shown, null, decorative.clip(CircleShape), contentScale = ContentScale.Crop)
        shown != null -> Image(shown, null, decorative)
        else -> Initial(note.who, size, decorative)
    }
}

@Composable
private fun Initial(who: String, size: Dp, modifier: Modifier) {
    // The circle is a fixed size, so the letter doesn't grow with the font scale and spill out of it.
    val letter = who.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·"
    val fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
    Box(modifier.clip(CircleShape).background(Brand.Marigold), contentAlignment = Alignment.Center) {
        Text(letter, fontFamily = InstrumentSerif, fontSize = fontSize, lineHeight = fontSize, color = Brand.Ink, maxLines = 1)
    }
}

private fun picture(context: Context, note: Note, px: Int): ImageBitmap? = try {
    val drawable = if (note.person) {
        note.faceKey?.let { AppGraph.wayBacks[it]?.face }?.loadDrawable(context)
    } else {
        note.packageName?.let { context.packageManager.getApplicationIcon(it) }
    }
    drawable?.toBitmap(px, px)?.asImageBitmap()
} catch (_: PackageManager.NameNotFoundException) {
    null
} catch (e: RuntimeException) {
    // A photo behind a content URI the app didn't share with Earmark, or a broken icon: the initial stands in.
    AppGraph.debugLog.error("face", e)
    null
}
