package com.thespotify.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
 * The MediaSession powers the lock screen / status-bar media controls (including the
 * OnePlus "dynamic island" style indicator) and routes their button presses back into
 * the web player. The WakeLock prevents the CPU from sleeping mid-track.
 *
 * IMPORTANT: We do NOT show the notification or activate the MediaSession at launch.
 * Doing so makes the phone think media is playing the instant the app opens (even with
 * nothing playing). Instead we wait until the web player reports actual playback via
 * updatePlaybackState(), then go foreground and mark the session active.
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
    private val mainHandler   = Handler(Looper.getMainLooper())
    private lateinit var mediaSession: MediaSessionCompat
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTitle  = "Spotify"
    private var currentArtist = ""

    // Tracks real playback so the system media controls reflect reality
    private var isPlaying    = false
    // True once we've gone foreground (i.e. real playback has started at least once)
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
        acquireWakeLock()
        // NOTE: deliberately NOT calling startForeground() here — see class docs.
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY  // Restart automatically if OS kills the service

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    /**
     * Update notification text and MediaSession metadata.
     * Called via: MainActivity.onMetadataUpdate() ← WebAppInterface ← injected JS.
     *
     * Stores the metadata so it's ready in the session, but only refreshes the visible
     * notification if we've already gone foreground (i.e. something is actually playing).
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

        if (isForeground) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    /**
     * Called when the web player's play/pause state changes (via injected JS).
     *
     * First time playback actually starts: go foreground (show the notification) and
     * activate the MediaSession so the system media controls appear. On later changes,
     * just update the playback state and notification icon.
     */
    fun updatePlaybackState(playing: Boolean) {
        isPlaying = playing
        mediaSession.setPlaybackState(buildPlaybackState())

        if (playing) {
            mediaSession.isActive = true
            if (!isForeground) {
                goForeground()
                return
            }
        }

        if (isForeground) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun goForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
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

        // Show a pause icon while playing, a play icon while paused
        val playPauseIcon  = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_pause
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_skip_previous, "Previous",  pendingBroadcast("previous",  1))
            .addAction(playPauseIcon,               playPauseLabel, pendingBroadcast("playPause", 2))
            .addAction(R.drawable.ic_skip_next,     "Next",      pendingBroadcast("next",      3))
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2) // All 3 buttons in compact view
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show controls on lock screen
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)  // Swipe-dismissable while paused
            .setSilent(true)        // No sound/vibration for notification updates
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

    private fun buildPlaybackState(): PlaybackStateCompat {
        val state = if (isPlaying)
            PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val speed = if (isPlaying) 1f else 0f
        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed)
            .build()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "TheSpotify").apply {
            // CRITICAL: this is what makes the phone's OWN media controls (dynamic island,
            // lock screen, Bluetooth, headset buttons) actually do something. Without it,
            // pressing next/previous/play on the system controls is silently ignored.
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()           = sendToPlayer("playPause")
                override fun onPause()          = sendToPlayer("playPause")
                override fun onSkipToNext()     = sendToPlayer("next")
                override fun onSkipToPrevious() = sendToPlayer("previous")
            })
            setPlaybackState(buildPlaybackState())
            // isActive stays false until real playback begins (see updatePlaybackState)
        }
    }

    /** Route a system media-control press into the web player on the main thread. */
    private fun sendToPlayer(command: String) {
        mainHandler.post {
            MainActivity.instance?.get()?.executeMediaCommand(command)
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
