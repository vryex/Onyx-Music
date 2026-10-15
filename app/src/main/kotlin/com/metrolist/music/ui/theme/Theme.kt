/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFFFF6474)

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(pureBlack) {
        darkColorScheme(
            primary = Color(0xFFD8ECF8),
            onPrimary = Color(0xFF05060F),
            primaryContainer = Color(0xFF202733),
            onPrimaryContainer = Color(0xFFD8ECF8),
            secondary = Color(0xFFC7D3EA),
            onSecondary = Color(0xFF05060F),
            secondaryContainer = Color(0xFF1B202C),
            onSecondaryContainer = Color(0xFFD1E4FA),
            tertiary = Color(0xFFB6D9FC),
            onTertiary = Color(0xFF05060F),
            tertiaryContainer = Color(0xFF1B202C),
            onTertiaryContainer = Color(0xFFD1E4FA),
            background = if (pureBlack) Color.Black else Color(0xFF05060F),
            onBackground = Color(0xFFD8ECF8),
            surface = if (pureBlack) Color.Black else Color(0xFF05060F),
            onSurface = Color(0xFFD1E4FA),
            surfaceVariant = Color(0xFF171C27),
            onSurfaceVariant = Color(0xFF9DA7BA),
            surfaceContainerLowest = Color(0xFF05060F),
            surfaceContainerLow = Color(0xFF0B0E18),
            surfaceContainer = Color(0xFF101420),
            surfaceContainerHigh = Color(0xFF191F2C),
            surfaceContainerHighest = Color(0xFF242C3A),
            outline = Color(0xFF505C70),
            outlineVariant = Color(0x1FBAD7F7),
            inverseSurface = Color(0xFFD8ECF8),
            inverseOnSurface = Color(0xFF05060F),
            inversePrimary = Color(0xFF303D56),
            surfaceTint = Color.Transparent,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = OnyxTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(50),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        ),
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
