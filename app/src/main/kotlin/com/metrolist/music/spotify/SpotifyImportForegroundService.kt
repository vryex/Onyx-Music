package com.metrolist.music.spotify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.metrolist.music.MainActivity
import com.metrolist.music.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class SpotifyImportForegroundService : Service() {
    @Inject lateinit var manager: SpotifyImportManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var started = false
    private var localeTag = ""
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            serviceScope.launch {
                manager.stop()
                finish(manager.state.value)
            }
            return START_NOT_STICKY
        }
        localeTag = intent?.getStringExtra(EXTRA_LOCALE).orEmpty()
        createChannel()
        try {
            val initial = notification(SpotifyImportSnapshot(phase = SpotifyImportPhase.RUNNING, localeTag = localeTag))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIFICATION_ID, initial)
        } catch (_: RuntimeException) {
            manager.requestStop(paused = true, failure = SpotifyFailure.RESPONSE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sonet:SpotifyImport").apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)
            }
            val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID) ?: UUID.randomUUID().toString()
            serviceScope.launch {
                try { manager.startOrResume(requestId) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    manager.stop(paused = true, failure = SpotifyFailure.FILE_READ)
                    finish(manager.state.value)
                    return@launch
                }
                observer = serviceScope.launch {
                    manager.state.conflate().collect { snapshot ->
                        if (snapshot.localeTag.isNotBlank()) localeTag = snapshot.localeTag
                        if (!snapshot.active) {
                            finish(snapshot)
                        } else {
                            notify(snapshot)
                            delay(1000)
                        }
                    }
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        manager.requestStop(paused = true)
        notify(manager.state.value.copy(phase = SpotifyImportPhase.PAUSED))
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        if (manager.state.value.active) manager.requestStop(paused = true)
        wakeLock?.let { if (it.isHeld) it.release() }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun finish(snapshot: SpotifyImportSnapshot) {
        notify(snapshot)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun notify(snapshot: SpotifyImportSnapshot) {
        try { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(snapshot)) }
        catch (_: SecurityException) { /* Notification permission does not gate a foreground import. */ }
    }

    private fun localized(): Context {
        val configuration = Configuration(resources.configuration)
        if (localeTag.isNotBlank()) configuration.setLocale(Locale.forLanguageTag(localeTag))
        return createConfigurationContext(configuration)
    }

    private fun createChannel() {
        val strings = localized()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(
            CHANNEL_ID, strings.getString(R.string.spotify_import_notification_channel), NotificationManager.IMPORTANCE_LOW,
        ))
    }

    private fun notification(snapshot: SpotifyImportSnapshot): Notification {
        val strings = localized()
        val open = PendingIntent.getActivity(this, NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_IMPORT, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val progress = snapshot.progress
        val summary = strings.getString(R.string.spotify_import_notification_progress,
            progress.processed, progress.total, progress.imported, snapshot.remaining.size)
        val title = strings.getString(when (snapshot.phase) {
            SpotifyImportPhase.COMPLETE -> R.string.spotify_import_notification_complete
            SpotifyImportPhase.CANCELLED -> R.string.spotify_import_cancelled_title
            SpotifyImportPhase.PAUSED, SpotifyImportPhase.ERROR -> R.string.spotify_import_notification_paused
            else -> R.string.spotify_import_notification_running
        })
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.download)
            .setContentTitle(title).setContentText(summary).setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(snapshot.active).setAutoCancel(!snapshot.active)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS).setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (snapshot.active) {
                    setProgress(progress.total, progress.processed, progress.total == 0)
                    addAction(0, strings.getString(R.string.spotify_import_cancel), PendingIntent.getService(
                        this@SpotifyImportForegroundService, NOTIFICATION_ID,
                        Intent(this@SpotifyImportForegroundService, SpotifyImportForegroundService::class.java).setAction(ACTION_CANCEL),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }.build()
    }

    companion object {
        const val EXTRA_OPEN_IMPORT = "open_spotify_import"
        const val ACTION_CANCEL = "com.metrolist.music.spotify.CANCEL_IMPORT"
        const val ACTION_RESUME = "com.metrolist.music.spotify.RESUME_IMPORT"
        const val NOTIFICATION_ID = 8889
        const val CHANNEL_ID = "spotify_import"
        private const val EXTRA_LOCALE = "spotify_import_locale"
        private const val EXTRA_REQUEST_ID = "spotify_import_request"

        internal fun start(context: Context, localeTag: String) {
            ContextCompat.startForegroundService(context, Intent(context, SpotifyImportForegroundService::class.java)
                .setAction(ACTION_RESUME).putExtra(EXTRA_LOCALE, localeTag)
                .putExtra(EXTRA_REQUEST_ID, UUID.randomUUID().toString()))
        }
    }
}
