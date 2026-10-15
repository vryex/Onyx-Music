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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFF9EB4FF)

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val baseColorScheme = remember(darkTheme, themeColor) {
        if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFFBBC8FF), onPrimary = Color(0xFF101831),
                background = Color(0xFF090D15), onBackground = Color(0xFFF3F5FF),
                surface = Color(0xFF101724), onSurface = Color(0xFFF3F5FF),
                surfaceContainerLow = Color(0xFF131A29),
                surfaceContainer = Color(0xFF1B2537),
                surfaceContainerHigh = Color(0xFF283348),
                surfaceVariant = Color(0xFF2E3A4E),
                onSurfaceVariant = Color(0xFFBAC5DA),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF3E559B), onPrimary = Color.White,
                background = Color(0xFFF4F6FC), onBackground = Color(0xFF151D30),
                surface = Color(0xFFF9FAFF), onSurface = Color(0xFF151D30),
                surfaceContainerLow = Color(0xFFEEF1F9),
                surfaceContainer = Color(0xFFE6EBF5),
                surfaceContainerHigh = Color(0xFFDBE3F2),
                surfaceVariant = Color(0xFFE3E9F5),
                onSurfaceVariant = Color(0xFF56627A),
            )
        }
    }
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) baseColorScheme.pureBlack(true) else baseColorScheme
    }

    // Use standard MaterialTheme instead of MaterialExpressiveTheme
    MaterialTheme(
        colorScheme = colorScheme,
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
