package com.protoprojects.agrix.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * AgriX dark-mode "premium agritech" palette — deep OLED black, glass-panel
 * greys, and a single glowing neon-green accent reserved for anything that
 * signals AI activity, growth, or a primary action. Dark-only by design (no
 * light color scheme): this look doesn't degrade gracefully into daylight
 * mode, so the app always renders dark regardless of the system theme.
 */
val AgrixDeepBlack = Color(0xFF000000)
val AgrixSurfaceGrey = Color(0xFF111111)
val AgrixSurfaceGreyElevated = Color(0xFF191919)
val AgrixNeonGreen = Color(0xFF00FF66)
val AgrixNeonGreenDim = Color(0xFF00B84A)
val AgrixGlassBorder = Color(0x1FFFFFFF) // white @ ~12% opacity
val AgrixGlassBorderStrong = Color(0x33FFFFFF) // white @ ~20% opacity
val AgrixWhite = Color(0xFFFFFFFF)
val AgrixMutedSilver = Color(0xFF8A8A8E)
val AgrixDimSilver = Color(0xFF5A5A5E)
val AgrixWarnRed = Color(0xFFFF4D4D)

private val AgrixDarkColors = darkColorScheme(
    primary = AgrixNeonGreen,
    onPrimary = Color.Black,
    primaryContainer = AgrixSurfaceGreyElevated,
    onPrimaryContainer = AgrixNeonGreen,
    secondary = AgrixNeonGreenDim,
    onSecondary = Color.Black,
    tertiary = AgrixNeonGreen,
    onTertiary = Color.Black,
    background = AgrixDeepBlack,
    onBackground = AgrixWhite,
    surface = AgrixSurfaceGrey,
    onSurface = AgrixWhite,
    surfaceVariant = AgrixSurfaceGreyElevated,
    onSurfaceVariant = AgrixMutedSilver,
    outline = AgrixGlassBorderStrong,
    error = AgrixWarnRed,
    errorContainer = Color(0xFF2A1414),
    onErrorContainer = AgrixWarnRed
)

/**
 * A single modern sans-serif voice throughout — bold/white for headlines,
 * medium/muted-silver for subtitles, regular/muted-silver for body copy,
 * and wide-tracked uppercase for micro-copy (status labels, timestamps).
 * Uses the platform default sans (Roboto) rather than a bundled Inter/SF Pro
 * font file, so there's no font asset that can go missing at build time —
 * visually close enough to read as "modern grotesque" at these weights.
 */
private val AgrixFont = FontFamily.Default

val AgrixTypography = Typography(
    displayLarge = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp),
    headlineLarge = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, color = AgrixMutedSilver),
    bodyLarge = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, color = AgrixMutedSilver),
    bodyMedium = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = AgrixMutedSilver),
    bodySmall = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, color = AgrixMutedSilver),
    labelLarge = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp, color = AgrixMutedSilver),
    labelSmall = TextStyle(fontFamily = AgrixFont, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp, color = AgrixDimSilver)
)

@Composable
fun AgriXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AgrixDarkColors, typography = AgrixTypography, content = content)
}
