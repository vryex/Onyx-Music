package com.metrolist.music.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import com.metrolist.music.R

fun openSystemEqualizer(context: Context, sessionId: Int): Boolean {
    // Try the session-aware audio effects interface when the OEM provides one.
    if (sessionId > 0) {
        val effectPanel = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(effectPanel)
            return true
        } catch (_: ActivityNotFoundException) {
            // Some devices do not implement a system equalizer.
        } catch (_: SecurityException) {
            // Some manufacturers restrict access to audio effect panels.
        } catch (_: IllegalStateException) {
        }
    }
    // Never leave the icon unresponsive: explain and open sound settings.
    Toast.makeText(context, R.string.system_equalizer_unavailable, Toast.LENGTH_SHORT).show()
    return try {
        context.startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (_: Exception) {
        false
    }
}
