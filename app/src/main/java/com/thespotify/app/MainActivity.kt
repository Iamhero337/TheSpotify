package com.thespotify.app

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    // Public so MediaControlReceiver and WebAppInterface can reach the WebView
    lateinit var webView: WebView
    private var mediaService: MediaPlaybackService? = null
    private var serviceBound = false

    // Sleep timer
    private val sleepHandler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    companion object {
        /**
         * Desktop UA — the Spotify server reads this string and decides which version of
         * the app to serve. "Windows Chrome" → full-featured desktop app → no mobile
         * restrictions (on-demand playback, unlimited skips, all tracks selectable).
         *
         * The viewport trick (useWideViewPort = false) below then forces Spotify's own
         * responsive CSS to render the compact phone-width layout despite the desktop UA.
         */
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

        const val SPOTIFY_URL = "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

        /**
         * Weak reference so MediaControlReceiver can find us without leaking memory.
         * WeakReference does not prevent GC from collecting this Activity when the OS
         * decides to destroy it.
         */
        var instance: WeakReference<MainActivity>? = null
    }

    // Service binding callbacks
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MediaPlaybackService.LocalBinder
            mediaService = localBinder.getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            mediaService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = WeakReference(this)

        // Black status bar blends with Spotify's dark UI
        window.statusBarColor = android.graphics.Color.BLACK

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)

        setupWebView()
        startAndBindMediaService()
        setupSleepTimer()
        UpdateManager.checkForUpdates(this)
        ChangelogManager.checkAndShowChangelog(this)

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState) // Restore scroll/history on rotation
        } else {
            webView.loadUrl(SPOTIFY_URL)
        }
    }

    private fun setupWebView() {
        val settings = webView.settings

        // MANDATORY — Spotify is a React SPA, useless without JS
        settings.javaScriptEnabled = true

        // Persist login between sessions (localStorage stores auth tokens)
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        // Let Spotify automatically play the next track without requiring a tap
        settings.mediaPlaybackRequiresUserGesture = false

        // THE CORE TRICK: Server reads this UA → serves unrestricted desktop app
        settings.userAgentString = DESKTOP_UA

        // PHONE LAYOUT TRICK:
        // useWideViewPort = false  →  viewport width = actual phone screen width
        // Spotify's CSS breakpoints kick in → sidebar collapses → compact mobile UI
        // The user gets desktop-app FEATURES with mobile-app APPEARANCE
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false

        // Pinch-to-zoom (useful for reading playlists)
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false  // Hide the ugly +/- overlay

        // Hardware rendering layer required for Widevine DRM decryption
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Spotify's OAuth flow uses third-party cookies — must allow them
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient   = CustomWebViewClient(this)
        webView.webChromeClient = CustomWebChromeClient(this)

        // Registers "TheSpotify" as window.TheSpotify in JavaScript
        webView.addJavascriptInterface(WebAppInterface(this), "TheSpotify")

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    private fun startAndBindMediaService() {
        Intent(this, MediaPlaybackService::class.java).also { intent ->
            startService(intent)
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    /** Called by WebAppInterface (from injected JS) when the track changes */
    fun onMetadataUpdate(title: String, artist: String) {
        mediaService?.updateNotification(title, artist)
    }

    /** Called by WebAppInterface (from injected JS) when the album art URL changes */
    fun onAlbumArtUpdate(artUrl: String) {
        mediaService?.updateAlbumArt(artUrl)
    }

    /** Called by WebAppInterface (from injected JS) when play/pause state changes */
    fun onPlaybackStateChanged(isPlaying: Boolean) {
        mediaService?.updatePlaybackState(isPlaying)
    }

    private fun setupSleepTimer() {
        findViewById<ImageButton>(R.id.sleepTimerButton).setOnClickListener {
            showSleepTimerDialog()
        }
    }

    private fun showSleepTimerDialog() {
        val active = sleepRunnable != null
        val options = if (active)
            arrayOf("Cancel timer", "15 minutes", "30 minutes", "45 minutes", "60 minutes")
        else
            arrayOf("15 minutes", "30 minutes", "45 minutes", "60 minutes")

        AlertDialog.Builder(this)
            .setTitle("Sleep Timer")
            .setItems(options) { _, which ->
                if (active) {
                    when (which) {
                        0 -> setSleepTimer(0)
                        1 -> setSleepTimer(15)
                        2 -> setSleepTimer(30)
                        3 -> setSleepTimer(45)
                        4 -> setSleepTimer(60)
                    }
                } else {
                    setSleepTimer(when (which) { 0 -> 15; 1 -> 30; 2 -> 45; else -> 60 })
                }
            }
            .show()
    }

    private fun setSleepTimer(minutes: Int) {
        sleepRunnable?.let { sleepHandler.removeCallbacks(it) }
        sleepRunnable = null
        if (minutes == 0) {
            Toast.makeText(this, "Sleep timer cancelled", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Sleep timer set: $minutes min", Toast.LENGTH_SHORT).show()
        sleepRunnable = Runnable {
            executeMediaCommand("playPause")
            Toast.makeText(this, "Sleep timer: playback paused", Toast.LENGTH_LONG).show()
            sleepRunnable = null
        }.also { sleepHandler.postDelayed(it, minutes * 60_000L) }
    }

    /**
     * Called by MediaControlReceiver when the user taps a notification button.
     * Evaluates JS that clicks the corresponding control in the Spotify web player.
     * data-testid attributes are stable because Spotify's own test suite uses them.
     */
    fun executeMediaCommand(command: String) {
        val js = when (command) {
            "next"      -> "document.querySelector('[data-testid=\"control-button-skip-forward\"]')?.click()"
            "previous"  -> "document.querySelector('[data-testid=\"control-button-skip-back\"]')?.click()"
            "playPause" -> "document.querySelector('[data-testid=\"control-button-playpause\"]')?.click()"
            else        -> return
        }
        webView.evaluateJavascript(js, null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        instance = WeakReference(this)
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        // DO NOT call webView.onPause() here.
        // webView.onPause() calls pauseTimers() on the Chromium engine, which suspends
        // JavaScript execution and kills audio playback in the background.
        // Skipping it lets JS and audio keep running while the screen is locked.
        // The Foreground Service (MediaPlaybackService) tells the OS to keep the process alive.
    }

    override fun onDestroy() {
        instance = null
        sleepRunnable?.let { sleepHandler.removeCallbacks(it) }
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Navigate within Spotify's SPA history before closing the app
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
