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

val DefaultThemeColor = Color(0xFFF5F5F7)

val MidnightCanvas = Color(0xFF000000)
val FrostGlow = Color(0xFFF5F5F7)
val MoonMist = Color(0xFFD1D1D6)
val FogVeil = Color(0xFFA1A1A6)
val GlassEdge = Color(0x18FFFFFF)
val AuthViolet = Color(0xFFF5F5F7)

private val MidnightColors = darkColorScheme(
    primary = Color(0xFFF5F5F7),
    onPrimary = MidnightCanvas,
    primaryContainer = Color(0xFF242424),
    onPrimaryContainer = FrostGlow,
    secondary = Color(0xFFA1A1A6),
    onSecondary = MidnightCanvas,
    secondaryContainer = Color(0xFF242424),
    onSecondaryContainer = FrostGlow,
    tertiary = FrostGlow,
    onTertiary = MidnightCanvas,
    tertiaryContainer = Color(0xFF242424),
    onTertiaryContainer = FrostGlow,
    background = MidnightCanvas,
    onBackground = Color(0xFFF5F5F7),
    surface = MidnightCanvas,
    onSurface = FrostGlow,
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = FogVeil,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = MidnightCanvas,
    surfaceContainerLow = Color(0xFF141414),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF242424),
    inverseSurface = FrostGlow,
    inverseOnSurface = MidnightCanvas,
    inversePrimary = Color(0xFF242424),
    outline = Color(0xFF636366),
    outlineVariant = GlassEdge,
)

private val MidnightShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

private val MidnightTypography = androidx.compose.material3.Typography().let { t ->
    val family = androidx.compose.ui.text.font.FontFamily(
        androidx.compose.ui.text.font.Font(com.metrolist.music.R.font.outfit_regular, FontWeight.Normal),
        androidx.compose.ui.text.font.Font(com.metrolist.music.R.font.outfit_medium, FontWeight.Medium),
    )
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = family, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp),
        displayMedium = t.displayMedium.copy(fontFamily = family, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium),
        displaySmall = t.displaySmall.copy(fontFamily = family, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium),
        headlineLarge = t.headlineLarge.copy(fontFamily = family, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium),
        headlineMedium = t.headlineMedium.copy(fontFamily = family, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium),
        headlineSmall = t.headlineSmall.copy(fontFamily = family, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
        titleLarge = t.titleLarge.copy(fontFamily = family, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
        titleMedium = t.titleMedium.copy(fontFamily = family, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        titleSmall = t.titleSmall.copy(fontFamily = family, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
        bodyLarge = t.bodyLarge.copy(fontFamily = family, fontSize = 16.sp, lineHeight = 20.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = family, fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = t.bodySmall.copy(fontFamily = family, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = t.labelLarge.copy(fontFamily = family, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelMedium = t.labelMedium.copy(fontFamily = family, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontFamily = family, fontSize = 11.sp, fontWeight = FontWeight.Medium),
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
