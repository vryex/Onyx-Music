package com.metrolist.music.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.metrolist.music.R

val bbhBartle = FontFamily(
    Font(R.font.bbh_bartle_regular, FontWeight.Normal)
)

val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
)

/**
 * User-selected family for synchronized song lyrics only.
 * The app-wide MaterialTheme stays on its original Outfit family.
 */
@androidx.compose.runtime.Composable
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun rememberSonetLyricsFontFamily(): FontFamily {
    val context = androidx.compose.ui.platform.LocalContext.current
    val (option) = com.metrolist.music.utils.rememberPreference(
        com.metrolist.music.constants.SonetLyricsFontKey,
        "Outfit",
    )
    return androidx.compose.runtime.remember(option, context) {
        when (option) {
            "Sans" -> FontFamily.SansSerif
            "Serif" -> FontFamily.Serif
            "Mono" -> FontFamily.Monospace
            else -> {
                if (!option.startsWith("Custom:")) Outfit
                else {
                    val file = storedLyricsFont(context, option)
                    if (file?.isFile == true) {
                        runCatching { FontFamily(Font(file)) }.getOrDefault(Outfit)
                    } else Outfit
                }
            }
        }
    }
}
