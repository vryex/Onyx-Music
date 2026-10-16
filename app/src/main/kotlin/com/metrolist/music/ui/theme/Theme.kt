/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.ui.text.font.FontWeight
import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFFFFFFFF)

val MidnightCanvas = Color(0xFF121212)
val FrostGlow = Color(0xFFFFFFFF)
val MoonMist = Color(0xFFD3D3D8)
val FogVeil = Color(0xFFB3B3B3)
val GlassEdge = Color(0x24FFFFFF)
val AuthViolet = Color(0xFF663AF3)

private val MidnightColors = darkColorScheme(
    primary = Color(0xFF1DB954),
    onPrimary = MidnightCanvas,
    primaryContainer = Color(0xFF3E3E3E),
    onPrimaryContainer = FrostGlow,
    secondary = Color(0xFFB3B3B3),
    onSecondary = MidnightCanvas,
    secondaryContainer = Color(0xFF282828),
    onSecondaryContainer = FrostGlow,
    tertiary = FrostGlow,
    onTertiary = MidnightCanvas,
    tertiaryContainer = Color(0xFF3E3E3E),
    onTertiaryContainer = FrostGlow,
    background = MidnightCanvas,
    onBackground = Color(0xFFFFFFFF),
    surface = MidnightCanvas,
    onSurface = FrostGlow,
    surfaceVariant = Color(0xFF282828),
    onSurfaceVariant = FogVeil,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = MidnightCanvas,
    surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Color(0xFF181818),
    surfaceContainerHigh = Color(0xFF282828),
    surfaceContainerHighest = Color(0xFF3E3E3E),
    inverseSurface = FrostGlow,
    inverseOnSurface = MidnightCanvas,
    inversePrimary = Color(0xFF3E3E3E),
    outline = Color(0xFF73747D),
    outlineVariant = GlassEdge,
)

private val MidnightShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
)

private val MidnightTypography = androidx.compose.material3.Typography().let { t ->
    val family = androidx.compose.ui.text.font.FontFamily.SansSerif
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = family, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        displayMedium = t.displayMedium.copy(fontFamily = family, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        displaySmall = t.displaySmall.copy(fontFamily = family, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.copy(fontFamily = family, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = family, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontFamily = family, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = family, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontFamily = family, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontFamily = family, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontFamily = family, fontSize = 16.sp, lineHeight = 20.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = family, fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = t.bodySmall.copy(fontFamily = family, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = t.labelLarge.copy(fontFamily = family, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontFamily = family, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontFamily = family, fontSize = 11.sp, fontWeight = FontWeight.Bold),
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MidnightColors,
        shapes = MidnightShapes,
        typography = MidnightTypography,
        content = content,
    )
}

fun Bitmap.extractThemeColor(): Color = Color(
    Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .rankedColors(1, DefaultThemeColor.toArgb())
        .first()
)

internal fun Palette.rankedColors(
    desiredColorCount: Int,
    fallbackColor: Int,
): List<Int> = Score.score(
    swatches.associate { it.rgb to it.population },
    desiredColorCount,
    fallbackColor,
    true,
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
