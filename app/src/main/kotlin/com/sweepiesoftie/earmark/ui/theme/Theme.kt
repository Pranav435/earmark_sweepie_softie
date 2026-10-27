package com.sweepiesoftie.earmark.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sweepiesoftie.earmark.R

/** Brand tokens from videos/shared/kit.css. */
object Brand {
    val Paper: Color = Color(0xFFFFF8EC)
    val Ink: Color = Color(0xFF17150F)
    val Ink2: Color = Color(0xFF2A261D)
    val Marigold: Color = Color(0xFFFFB224)
    val MarigoldDeep: Color = Color(0xFFE08E00)

    /** Marigold dark enough for text on paper (about 4.6:1); the brand marigolds are for fills. */
    val MarigoldText: Color = Color(0xFFA35F00)
}

val InstrumentSerif: FontFamily = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

val InstrumentSans: FontFamily = FontFamily(
    Font(R.font.instrument_sans_400, FontWeight.Normal),
    Font(R.font.instrument_sans_500, FontWeight.Medium),
    Font(R.font.instrument_sans_600, FontWeight.SemiBold),
    Font(R.font.instrument_sans_700, FontWeight.Bold),
)

val JetBrainsMono: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_500, FontWeight.Medium),
    Font(R.font.jetbrains_mono_700, FontWeight.Bold),
)

private val EarmarkTypography = Typography(
    displaySmall = TextStyle(fontFamily = InstrumentSerif, fontSize = 40.sp, lineHeight = 44.sp),
    headlineSmall = TextStyle(fontFamily = InstrumentSerif, fontSize = 28.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = InstrumentSans, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = InstrumentSans, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

/**
 * The screens' colours in one theme. [ink] is the foreground and [paper] the background, so dark mode
 * swaps them: ink-filled buttons turn paper-coloured, and their paper text turns ink.
 */
@Immutable
data class Tones(
    val dark: Boolean,
    val ink: Color,
    val paper: Color,
    val card: Color,
    val line: Color,
    val muted: Color,
    val done: Color,
    /** Marigold for text: deepened on paper, the brand fill on ink. */
    val marigoldText: Color,
    val marigoldWash: Color,
)

private val LightTones = Tones(
    dark = false,
    ink = Brand.Ink,
    paper = Brand.Paper,
    card = Color(0xFFFFFDF8),
    line = Brand.Ink.copy(alpha = 0.12f),
    muted = Brand.Ink.copy(alpha = 0.62f),
    done = Color(0xFF2E9E5B),
    marigoldText = Brand.MarigoldText,
    marigoldWash = Color(0xFFFFEBC2),
)

private val DarkTones = Tones(
    dark = true,
    ink = Brand.Paper,
    paper = Brand.Ink,
    card = Color(0xFF26221A),
    line = Brand.Paper.copy(alpha = 0.14f),
    muted = Brand.Paper.copy(alpha = 0.66f),
    done = Color(0xFF4CC27E),
    marigoldText = Brand.Marigold,
    marigoldWash = Color(0xFF3A2B0C),
)

val LocalTones = staticCompositionLocalOf { LightTones }

private fun colorScheme(tones: Tones) = if (tones.dark) {
    darkColorScheme(
        primary = tones.ink,
        onPrimary = tones.paper,
        secondary = Brand.Marigold,
        onSecondary = Brand.Ink,
        background = tones.paper,
        onBackground = tones.ink,
        surface = tones.paper,
        onSurface = tones.ink,
    )
} else {
    lightColorScheme(
        primary = tones.ink,
        onPrimary = tones.paper,
        secondary = Brand.Marigold,
        onSecondary = Brand.Ink,
        background = tones.paper,
        onBackground = tones.ink,
        surface = tones.paper,
        onSurface = tones.ink,
    )
}

/** Follows the system's light or dark setting. */
@Composable
fun EarmarkTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val tones = if (dark) DarkTones else LightTones
    CompositionLocalProvider(LocalTones provides tones) {
        MaterialTheme(colorScheme = colorScheme(tones), typography = EarmarkTypography, content = content)
    }
}
