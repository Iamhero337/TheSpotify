package com.thespotify.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat

/**
 * Foreground Service — the foundation of background playback.
 *
 * Android's process lifecycle: without a Foreground Service, the OS kills apps
 * within seconds of going to the background. By running as a Foreground Service:
 *   - A persistent notification is shown (OS requirement, user sees what's playing)
 *   - OS treats our process as actively doing work → won't kill it
 *   - Music plays through lock screen, app switching, screen-off
 *
 * The MediaSession powers the lock screen playback widget and headphone buttons.
 * The WakeLock prevents the CPU from sleeping mid-track.
 */
class MediaPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID      = "TheSpotifyPlayback"
        const val NOTIFICATION_ID = 1001
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
    }

    private val binder        = LocalBinder()
    private lateinit var mediaSession: MediaSessionCompat
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTitle  = "Spotify"
    private var currentArtist = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
        acquireWakeLock()

        // Must call startForeground() promptly (within 5 s on Android 12+)
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY  // Restart automatically if OS kills the service

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        mediaSession.release()
        super.onDestroy()
    }

    /**
     * Update notification text and MediaSession metadata.
     * Called via: MainActivity.onMetadataUpdate() ← WebAppInterface ← injected JS.
     */
    fun updateNotification(title: String, artist: String) {
        currentTitle  = title.ifBlank { "Spotify" }
        currentArtist = artist.ifBlank { "" }

        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .build()
        )

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        // Tapping the notification body opens / resumes the app
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_skip_previous, "Previous",  pendingBroadcast("previous",  1))
            .addAction(R.drawable.ic_play_pause,    "Play/Pause", pendingBroadcast("playPause", 2))
            .addAction(R.drawable.ic_skip_next,     "Next",      pendingBroadcast("next",      3))
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2) // All 3 buttons in compact view
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show controls on lock screen
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)  // Cannot be dismissed by swipe
            .setSilent(true)   // No sound/vibration for notification updates
            .build()
    }

    private fun pendingBroadcast(command: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MediaControlReceiver::class.java).apply {
            action = MediaControlReceiver.ACTION
            putExtra(MediaControlReceiver.EXTRA_COMMAND, command)
        }
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "TheSpotify").apply {
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                    .build()
            )
            isActive = true
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TheSpotify::PlaybackWakeLock"
        ).apply { acquire(4 * 60 * 60 * 1000L) } // 4-hour window; refreshed on track change
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = "Shows the currently playing track"
                    setSound(null, null)
                    enableVibration(false)
                }
                .also { channel ->
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                        .createNotificationChannel(channel)
                }
        }
    }
}
