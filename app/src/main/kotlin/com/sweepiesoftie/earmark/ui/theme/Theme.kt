package com.sweepiesoftie.earmark.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

private val EarmarkColors = lightColorScheme(
    primary = Brand.Ink,
    onPrimary = Brand.Paper,
    secondary = Brand.Marigold,
    onSecondary = Brand.Ink,
    background = Brand.Paper,
    onBackground = Brand.Ink,
    surface = Brand.Paper,
    onSurface = Brand.Ink,
)

@Composable
fun EarmarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EarmarkColors, typography = EarmarkTypography, content = content)
}
