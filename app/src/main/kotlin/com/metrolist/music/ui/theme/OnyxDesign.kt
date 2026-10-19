package com.metrolist.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OnyxDarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF222222),
    onPrimaryContainer = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFF333333),
    secondary = Color(0xFF999999),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF222222),
    onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Color(0xFFCCCCCC),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF222222),
    onTertiaryContainer = Color(0xFFF5F5F5),
    background = Color.Black,
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF0B0B0B),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF999999),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFF5F5F5),
    inverseOnSurface = Color(0xFF111111),
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFF222222),
    scrim = Color.Black,
    surfaceBright = Color(0xFF222222),
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF191919),
    surfaceContainerHighest = Color(0xFF222222),
)

private val OnyxLightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF555555),
    secondaryContainer = Color(0xFFE5E5E5),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF555555),
    tertiaryContainer = Color(0xFFE5E5E5),
    onTertiaryContainer = Color.Black,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFF5F5F5),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF555555),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFFDDDDDD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFEEEEEE),
    surfaceContainerHigh = Color(0xFFE5E5E5),
    surfaceContainerHighest = Color(0xFFDDDDDD),
)

internal fun onyxColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) OnyxDarkColors else OnyxLightColors

internal val OnyxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

internal val OnyxTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
