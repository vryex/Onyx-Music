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

val DefaultThemeColor = Color(0xFFF3F3F5)

val MidnightCanvas = Color(0xFF050506)
val FrostGlow = Color(0xFFF3F3F5)
val MoonMist = Color(0xFFD3D3D8)
val FogVeil = Color(0xFF999AA4)
val GlassEdge = Color(0x24FFFFFF)
val AuthViolet = Color(0xFF663AF3)

private val MidnightColors = darkColorScheme(
    primary = FrostGlow,
    onPrimary = MidnightCanvas,
    primaryContainer = Color(0xFF2C2D32),
    onPrimaryContainer = FrostGlow,
    secondary = MoonMist,
    onSecondary = MidnightCanvas,
    secondaryContainer = Color(0xFF202126),
    onSecondaryContainer = FrostGlow,
    tertiary = FrostGlow,
    onTertiary = MidnightCanvas,
    tertiaryContainer = Color(0xFF2C2D32),
    onTertiaryContainer = FrostGlow,
    background = MidnightCanvas,
    onBackground = Color(0xFFF3F3F5),
    surface = MidnightCanvas,
    onSurface = FrostGlow,
    surfaceVariant = Color(0xFF191A1E),
    onSurfaceVariant = FogVeil,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = MidnightCanvas,
    surfaceContainerLow = Color(0xFF0C0C0E),
    surfaceContainer = Color(0xFF141416),
    surfaceContainerHigh = Color(0xFF202126),
    surfaceContainerHighest = Color(0xFF2C2D32),
    inverseSurface = FrostGlow,
    inverseOnSurface = MidnightCanvas,
    inversePrimary = Color(0xFF2C2D32),
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
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = (-2).sp),
        displayMedium = t.displayMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = (-1.6).sp),
        displaySmall = t.displaySmall.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = (-1.2).sp),
        headlineLarge = t.headlineLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp),
        headlineMedium = t.headlineMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = (-0.6).sp),
        headlineSmall = t.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
        titleLarge = t.titleLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp),
        titleMedium = t.titleMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
        titleSmall = t.titleSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
        bodyLarge = t.bodyLarge.copy(fontFamily = Outfit),
        bodyMedium = t.bodyMedium.copy(fontFamily = Outfit),
        bodySmall = t.bodySmall.copy(fontFamily = Outfit),
        labelLarge = t.labelLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
        labelMedium = t.labelMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontFamily = Outfit),
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
