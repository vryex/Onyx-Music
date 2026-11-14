package com.metrolist.music.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.metrolist.music.R

/**
 * Navigate to the device's native Sound & vibration settings.
 *
 * ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL resolves to MusicFX on many
 * phones; do not launch it because users asked for Android Settings instead.
 * Some Android devices do not provide a built-in equalizer in Settings.
 */
@Suppress("UNUSED_PARAMETER")
fun openSystemEqualizer(context: Context, sessionId: Int): Boolean {
    return try {
        context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (_: Exception) {
        Toast.makeText(context, R.string.system_equalizer_unavailable, Toast.LENGTH_SHORT).show()
        false
    }
}
