/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

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

val DefaultThemeColor = Color(0xFFD1E4FA)

val MidnightCanvas = Color(0xFF05060F)
val FrostGlow = Color(0xFFD1E4FA)
val MoonMist = Color(0xFFC7D3EA)
val FogVeil = Color(0xFF9DA7BA)
val GlassEdge = Color(0x1FBAD7F7)
val AuthViolet = Color(0xFF663AF3)

private val MidnightColors = darkColorScheme(
    primary = FrostGlow,
    onPrimary = MidnightCanvas,
    primaryContainer = Color(0xFF242C3A),
    onPrimaryContainer = FrostGlow,
    secondary = MoonMist,
    onSecondary = MidnightCanvas,
    secondaryContainer = Color(0xFF1B2230),
    onSecondaryContainer = FrostGlow,
    tertiary = FrostGlow,
    onTertiary = MidnightCanvas,
    tertiaryContainer = Color(0xFF242C3A),
    onTertiaryContainer = FrostGlow,
    background = MidnightCanvas,
    onBackground = Color(0xFFD8ECF8),
    surface = MidnightCanvas,
    onSurface = FrostGlow,
    surfaceVariant = Color(0xFF171D29),
    onSurfaceVariant = FogVeil,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = MidnightCanvas,
    surfaceContainerLow = Color(0xFF0D101A),
    surfaceContainer = Color(0xFF111622),
    surfaceContainerHigh = Color(0xFF191F2C),
    surfaceContainerHighest = Color(0xFF242C3A),
    inverseSurface = FrostGlow,
    inverseOnSurface = MidnightCanvas,
    inversePrimary = Color(0xFF242C3A),
    outline = Color(0xFF647084),
    outlineVariant = GlassEdge,
)

private val MidnightShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

private val MidnightTypography = androidx.compose.material3.Typography().let { type ->
    type.copy(
        headlineLarge = type.headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, letterSpacing = (-0.4).sp),
        headlineMedium = type.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, letterSpacing = (-0.3).sp),
        titleLarge = type.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, letterSpacing = (-0.24).sp),
        bodyLarge = type.bodyLarge.copy(letterSpacing = (-0.16).sp),
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
