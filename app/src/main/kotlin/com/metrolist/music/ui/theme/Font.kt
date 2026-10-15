package com.metrolist.music.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.metrolist.music.R
import androidx.compose.ui.unit.sp

val bbhBartle = FontFamily(
    Font(R.font.bbh_bartle_regular, FontWeight.Normal)
)


private val OnyxBaseType = androidx.compose.material3.Typography()
val OnyxTypography = OnyxBaseType.copy(
    displaySmall = OnyxBaseType.displaySmall.copy(fontWeight = FontWeight.Medium),
    headlineLarge = OnyxBaseType.headlineLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp),
    headlineMedium = OnyxBaseType.headlineMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
    headlineSmall = OnyxBaseType.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = OnyxBaseType.titleLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
    titleMedium = OnyxBaseType.titleMedium.copy(fontWeight = FontWeight.Medium),
    bodyLarge = OnyxBaseType.bodyLarge.copy(letterSpacing = (-0.16).sp),
    labelSmall = OnyxBaseType.labelSmall.copy(fontWeight = FontWeight.Medium),
)
