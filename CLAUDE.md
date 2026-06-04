# SpotiWrapper — Claude Code Build Instructions

## What You're Building

A custom Android app wrapping the Spotify Web Player that:

- **Sends a desktop User Agent** → Spotify's server thinks you're on a Windows PC → delivers the
  full-featured app (on-demand playback, skip forwards/backwards, no shuffle-only lock)
- **Sets viewport to phone width** → Spotify's own responsive CSS sees a narrow screen →
  collapses sidebar → renders the compact mobile-style layout automatically
- **Blocks ad domains at the network layer** → no ad scripts load at all
- **Hides upgrade/premium prompts** via injected CSS
- **Detects and skips audio ads** via injected JavaScript
- **Plays in background** → Foreground Service keeps music going when screen is locked
- **Lock screen & notification controls** → Previous / Play-Pause / Next buttons

Result: Looks and feels like mobile Spotify. Has all the features of desktop Spotify. No ads.

---

## Instructions for Claude Code

Create every file below **exactly as specified**. Do not skip files. Do not summarise — write
the full content. After creating all files, print the project tree so the user can verify.

---

## Project File Tree

```
SpotiWrapper/
├── .github/
│   └── workflows/
│       └── build.yml
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/
│   │       │       └── spotiwrapper/
│   │       │           └── app/
│   │       │               ├── MainActivity.kt
│   │       │               ├── CustomWebViewClient.kt
│   │       │               ├── CustomWebChromeClient.kt
│   │       │               ├── AdBlocker.kt
│   │       │               ├── WebAppInterface.kt
│   │       │               ├── MediaPlaybackService.kt
│   │       │               └── MediaControlReceiver.kt
│   │       ├── res/
│   │       │   ├── drawable/
│   │       │   │   ├── ic_launcher_background.xml
│   │       │   │   ├── ic_launcher_foreground.xml
│   │       │   │   ├── ic_notification.xml
│   │       │   │   ├── ic_skip_previous.xml
│   │       │   │   ├── ic_play_pause.xml
│   │       │   │   └── ic_skip_next.xml
│   │       │   ├── layout/
│   │       │   │   └── activity_main.xml
│   │       │   ├── mipmap-anydpi-v26/
│   │       │   │   ├── ic_launcher.xml
│   │       │   │   └── ic_launcher_round.xml
│   │       │   └── values/
│   │       │       ├── colors.xml
│   │       │       ├── strings.xml
│   │       │       └── themes.xml
│   │       └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── gradle.properties
└── settings.gradle
```

---

## All Files — Full Content

### FILE: `settings.gradle`

```groovy
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SpotiWrapper"
include ':app'
```

---

### FILE: `build.gradle` (root — project level)

```groovy
// Top-level build file — only plugin version declarations go here
plugins {
    id 'com.android.application' version '8.3.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.24' apply false
}
```

---

### FILE: `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

---

### FILE: `app/build.gradle`

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.spotiwrapper.app'
    compileSdk 34

    defaultConfig {
        applicationId "com.spotiwrapper.app"
        minSdk 26        // Android 8.0 — covers 97%+ of active devices
        targetSdk 34     // Required for foregroundServiceType enforcement on API 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        debug {
            debuggable true
        }
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'),
                          'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    // MediaSessionCompat, PlaybackStateCompat, MediaMetadataCompat, MediaStyle notification
    implementation 'androidx.media:media:1.7.0'
}
```

---

### FILE: `app/proguard-rules.pro`

```proguard
# JavascriptInterface methods must not be renamed — JavaScript calls them by exact name
-keepclassmembers class com.spotiwrapper.app.WebAppInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep AndroidX Media classes intact
-keep class android.support.v4.media.** { *; }
-keep class android.support.v4.media.session.** { *; }
-keep class androidx.media.** { *; }
-keep class androidx.media.app.** { *; }
```

---

### FILE: `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Network access for Spotify web player -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Prevents CPU from sleeping mid-track -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- Required for any foreground service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <!-- Required on Android 14+ when foregroundServiceType="mediaPlayback" -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <application
        android:allowBackup="true"
        android:hardwareAccelerated="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.SpotiWrapper">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Foreground service keeps music playing when screen is locked -->
        <service
            android:name=".MediaPlaybackService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="false" />

        <!-- Receives broadcasts from notification Previous/Play-Pause/Next buttons -->
        <receiver
            android:name=".MediaControlReceiver"
            android:exported="false" />

    </application>
</manifest>
```

---

### FILE: `app/src/main/java/com/spotiwrapper/app/MainActivity.kt`

```kotlin
package com.spotiwrapper.app

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    // Public so MediaControlReceiver and WebAppInterface can reach the WebView
    lateinit var webView: WebView
    private var mediaService: MediaPlaybackService? = null
    private var serviceBound = false

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

        const val SPOTIFY_URL = "https://open.spotify.com"

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

        // Registers "SpotiWrapper" as window.SpotiWrapper in JavaScript
        webView.addJavascriptInterface(WebAppInterface(this), "SpotiWrapper")

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
```

---

### FILE: `app/src/main/java/com/spotiwrapper/app/CustomWebViewClient.kt`

```kotlin
package com.spotiwrapper.app

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class CustomWebViewClient(private val activity: MainActivity) : WebViewClient() {

    // ─── URL navigation control ───────────────────────────────────────────────

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme ?: return false

        // Block all non-web schemes.
        // Spotify tries to deep-link to its native app (spotify://) or the Play Store
        // (intent://, market://) — we intercept and block these to keep the user inside
        // our wrapper instead of bouncing out to other apps.
        if (scheme != "http" && scheme != "https" && scheme != "about" && scheme != "data") {
            return true  // true = "I handled it" = WebView does nothing = blocked
        }

        // Allow all http/https navigation inside the WebView
        // (login page at accounts.spotify.com, Google OAuth, etc. all work fine)
        return false
    }

    // ─── Network-level ad blocking ────────────────────────────────────────────

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val host = request.url.host ?: return null
        val url  = request.url.toString()

        // If this request targets a known ad domain or has an ad path pattern,
        // return an empty 200 OK — the page sees "success" but receives zero bytes.
        // The ad script never downloads. The ad never executes. Bandwidth saved.
        if (AdBlocker.isAdDomain(host) || AdBlocker.isAdUrl(url)) {
            return AdBlocker.emptyResponse()
        }

        return null  // null = allow the request normally
    }

    // ─── Page load hooks ──────────────────────────────────────────────────────

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        // Inject in order — each layer stacks on the previous
        injectAdBlockCSS(view)       // Layer 1: Hide upgrade prompts and ad containers
        injectTouchCSS(view)         // Layer 2: Polish for finger interaction
        injectAudioAdMonitor(view)   // Layer 3: Detect and skip stream-injected audio ads
        injectMetadataBridge(view)   // Layer 4: Feed track info to the notification
    }

    // ─── JavaScript injection ─────────────────────────────────────────────────

    /**
     * LAYER 1 — CSS: Hide upgrade prompts and ad UI elements.
     * Uses data-testid selectors (stable because Spotify's own test suite uses them).
     */
    private fun injectAdBlockCSS(view: WebView) {
        val css = "[data-testid=\"upgrade-button\"]," +
                  "[data-testid=\"premium-upsell-ad-slot\"]," +
                  "[data-testid=\"audio-ad\"]," +
                  "[aria-label=\"Upgrade to Premium\"]," +
                  "[aria-label=\"Upgrade\"]," +
                  "[class*=\"encore-ad\"]," +
                  "[class*=\"sponsoredAd\"]," +
                  "[id*=\"google_ads\"]," +
                  ".ad-slot," +
                  ".playlist-ad-slot," +
                  "[data-testid=\"premium-upsell\"]" +
                  " { display:none!important; height:0!important; pointer-events:none!important; }"

        val js = "(function(){" +
                 "if(document.getElementById('sw-adblock'))return;" +
                 "var s=document.createElement('style');" +
                 "s.id='sw-adblock';" +
                 "s.textContent='$css';" +
                 "(document.head||document.documentElement).appendChild(s);" +
                 "})();"

        view.evaluateJavascript(js, null)
    }

    /**
     * LAYER 2 — CSS: Touch-friendly adjustments.
     * The desktop→phone layout change (from the viewport trick) already looks good,
     * but we increase tap targets on the player controls and disable text selection
     * on the playback bar.
     */
    private fun injectTouchCSS(view: WebView) {
        val css = "*{-webkit-overflow-scrolling:touch;}" +
                  "[data-testid='control-button-playpause']," +
                  "[data-testid='control-button-skip-forward']," +
                  "[data-testid='control-button-skip-back']" +
                  "{min-width:44px!important;min-height:44px!important;}" +
                  ".now-playing-bar,.player-controls" +
                  "{-webkit-user-select:none;user-select:none;}"

        val js = "(function(){" +
                 "if(document.getElementById('sw-touch'))return;" +
                 "var s=document.createElement('style');" +
                 "s.id='sw-touch';" +
                 "s.textContent='$css';" +
                 "(document.head||document.documentElement).appendChild(s);" +
                 "})();"

        view.evaluateJavascript(js, null)
    }

    /**
     * LAYER 3 — JavaScript: Detect and skip audio ads.
     *
     * Spotify sometimes stitches ads directly into the audio stream server-side.
     * These can't be blocked by domain filtering (same CDN as music).
     * This monitor polls every second; when it detects an audio ad:
     *  1. Mutes the audio element
     *  2. Sets playbackRate to 16× (fast-forward through silence)
     *  3. Clicks the skip-forward button
     *  4. Unmutes 800 ms later (after skip takes effect)
     */
    private fun injectAudioAdMonitor(view: WebView) {
        val js = """
            (function(){
                if(window._swMonitor)return;
                window._swMonitor=true;
                setInterval(function(){
                    try{
                        var isAd=!!document.querySelector('[data-testid="audio-ad"]');
                        if(!isAd)return;
                        var audio=document.querySelector('audio');
                        if(audio){audio.muted=true;audio.playbackRate=16.0;}
                        var skip=document.querySelector('[data-testid="control-button-skip-forward"]');
                        if(skip&&!skip.disabled)skip.click();
                        setTimeout(function(){
                            if(audio){audio.muted=false;audio.playbackRate=1.0;}
                        },800);
                    }catch(e){}
                },1000);
            })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }

    /**
     * LAYER 4 — JavaScript: Track metadata bridge.
     *
     * Android has no native way to know what is playing inside a WebView.
     * This script uses MutationObserver to watch Spotify's now-playing bar.
     * When the track title changes it calls window.SpotiWrapper.updateMetadata(title, artist)
     * — our @JavascriptInterface in WebAppInterface.kt — which then updates the
     * persistent notification and MediaSession (lock screen widget).
     */
    private fun injectMetadataBridge(view: WebView) {
        val js = """
            (function(){
                if(window._swMeta)return;
                window._swMeta=true;
                var lastTitle='';
                var obs=new MutationObserver(function(){
                    try{
                        var t=document.querySelector('[data-testid="context-item-info-title"]');
                        var a=document.querySelector('[data-testid="context-item-info-subtitles"]');
                        if(!t)return;
                        var title=(t.innerText||t.textContent||'').trim();
                        var artist=a?(a.innerText||a.textContent||'').trim():'';
                        if(title&&title!==lastTitle){
                            lastTitle=title;
                            if(window.SpotiWrapper)window.SpotiWrapper.updateMetadata(title,artist);
                        }
                    }catch(e){}
                });
                obs.observe(document.body||document.documentElement,
                    {subtree:true,childList:true,characterData:true});
            })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }
}
```

---

### FILE: `app/src/main/java/com/spotiwrapper/app/CustomWebChromeClient.kt`

```kotlin
package com.spotiwrapper.app

import android.content.Context
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

class CustomWebChromeClient(private val context: Context) : WebChromeClient() {

    /**
     * CRITICAL: Grant Widevine DRM permission.
     *
     * WebView denies ALL permission requests by default.
     * Without explicitly granting RESOURCE_PROTECTED_MEDIA_ID here,
     * Spotify's DRM licence handshake fails → the player UI loads but tracks
     * refuse to buffer, showing generic "content unavailable" errors.
     * This single override is what makes music actually play.
     */
    override fun onPermissionRequest(request: PermissionRequest) {
        val toGrant = request.resources.filter { resource ->
            resource == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
        }
        if (toGrant.isNotEmpty()) {
            request.grant(toGrant.toTypedArray())
        } else {
            request.deny()
        }
    }
}
```

---

### FILE: `app/src/main/java/com/spotiwrapper/app/AdBlocker.kt`

```kotlin
package com.spotiwrapper.app

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Network-level ad blocker singleton.
 *
 * Called inside shouldInterceptRequest before any network connection is made.
 * Matched requests receive an empty 200 response — no bytes transferred,
 * no script executed, no ad served.
 *
 * IMPORTANT: Do NOT add Spotify's own CDN domains here.
 * audio-ak.scdn.co, i.scdn.co, etc. serve real music and album art.
 */
object AdBlocker {

    private val adDomains = setOf(
        // Google advertising
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "pagead2.googlesyndication.com",
        "securepubads.g.doubleclick.net",
        "tpc.googlesyndication.com",
        "adservice.google.com",

        // Ad networks
        "ads.pubmatic.com",
        "sync.pubmatic.com",
        "image2.pubmatic.com",
        "ib.adnxs.com",
        "bidder.criteo.com",
        "dis.criteo.com",
        "static.criteo.net",
        "pixel.advertising.com",
        "ad.doubleclick.net",
        "ads.doubleclick.net",
        "cm.g.doubleclick.net",

        // Tracking / fingerprinting
        "scorecardresearch.com",
        "quantserve.com",
        "omtrdc.net",
        "everesttech.net",
        "connexity.net",
        "taboola.com",
        "outbrain.com"
    )

    private val adUrlPatterns = listOf(
        "/ads/", "/ad/", "/advertisement/", "/doubleclick/", "/adserver/",
        "ad_click", "adClick"
    )

    /** True if host matches a known ad domain (exact or subdomain). */
    fun isAdDomain(host: String): Boolean {
        if (host.isBlank()) return false
        val lowerHost = host.lowercase()
        return adDomains.any { domain ->
            lowerHost == domain || lowerHost.endsWith(".$domain")
        }
    }

    /** True if url contains a path pattern associated with ads. */
    fun isAdUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return adUrlPatterns.any { pattern -> lowerUrl.contains(pattern) }
    }

    /** Empty 200 response — silently absorbs the request. */
    fun emptyResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
    )
}
```

---

### FILE: `app/src/main/java/com/spotiwrapper/app/WebAppInterface.kt`

```kotlin
package com.spotiwrapper.app

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * JavaScript → Kotlin bridge.
 *
 * Methods annotated @JavascriptInterface are callable from JS as:
 *   window.SpotiWrapper.updateMetadata("Song Title", "Artist Name")
 *
 * THREADING: These methods run on the WebView rendering thread, not the main thread.
 * We post to the main thread before touching Android services or the notification.
 */
class WebAppInterface(private val activity: MainActivity) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Called from injected JS when the track changes. Triggers notification update. */
    @JavascriptInterface
    fun updateMetadata(title: String, artist: String) {
        mainHandler.post {
            activity.onMetadataUpdate(title, artist)
        }
    }

    /** Forward JS console messages to Android logcat during development. */
    @JavascriptInterface
    fun log(message: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("SpotiWrapper/JS", message)
        }
    }
}
```

---

### FILE: `app/src/main/java/com/spotiwrapper/app/MediaPlaybackService.kt`

```kotlin
package com.spotiwrapper.app

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
        const val CHANNEL_ID      = "SpotiWrapperPlayback"
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
        mediaSession = MediaSessionCompat(this, "SpotiWrapper").apply {
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
            "SpotiWrapper::PlaybackWakeLock"
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
```

---

### FILE: `app/src/main/java/com/spotiwrapper/app/MediaControlReceiver.kt`

```kotlin
package com.spotiwrapper.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Receives media control broadcasts from notification action buttons.
 *
 * Full flow when user taps "Next" on the notification:
 *   PendingIntent fires
 *     → onReceive() runs
 *       → finds MainActivity via WeakReference
 *         → calls executeMediaCommand("next")
 *           → evaluateJavascript() clicks the skip-forward button inside the WebView
 *             → Spotify advances to the next track
 *
 * WeakReference safety: if the Activity was force-closed, get() returns null and nothing
 * happens. With the Foreground Service running, the process stays alive so this is rare.
 */
class MediaControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION        = "com.spotiwrapper.MEDIA_COMMAND"
        const val EXTRA_COMMAND = "command"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val command = intent.getStringExtra(EXTRA_COMMAND) ?: return

        // Post to main thread — executeMediaCommand accesses the WebView
        Handler(Looper.getMainLooper()).post {
            MainActivity.instance?.get()?.executeMediaCommand(command)
        }
    }
}
```

---

### FILE: `app/src/main/res/layout/activity_main.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <WebView
        android:id="@+id/webView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</FrameLayout>
```

---

### FILE: `app/src/main/res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">SpotiWrapper</string>
</resources>
```

---

### FILE: `app/src/main/res/values/colors.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="spotify_green">#1DB954</color>
    <color name="spotify_green_dark">#148A3D</color>
    <color name="spotify_black">#191414</color>
    <color name="white">#FFFFFF</color>
    <color name="black">#000000</color>
</resources>
```

---

### FILE: `app/src/main/res/values/themes.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.SpotiWrapper" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="android:windowBackground">@color/black</item>
        <item name="android:statusBarColor">@color/black</item>
        <item name="android:navigationBarColor">@color/black</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="colorPrimary">@color/spotify_green</item>
        <item name="colorPrimaryVariant">@color/spotify_green_dark</item>
        <item name="colorOnPrimary">@color/white</item>
    </style>
</resources>
```

---

### FILE: `app/src/main/res/drawable/ic_launcher_background.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#191414" android:pathData="M0,0h108v108H0z"/>
</vector>
```

---

### FILE: `app/src/main/res/drawable/ic_launcher_foreground.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Spotify green circle -->
    <path android:fillColor="#1DB954"
          android:pathData="M54,20 a34,34 0,0,1 0,68 a34,34 0,0,1 0,-68Z"/>
    <!-- White play triangle -->
    <path android:fillColor="#FFFFFF"
          android:pathData="M46,38L46,70L72,54Z"/>
</vector>
```

---

### FILE: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

---

### FILE: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

---

### FILE: `app/src/main/res/drawable/ic_notification.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Status bar icon — must be monochrome white, system applies tinting -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#FFFFFF" android:pathData="M8,5v14l11,-7z"/>
</vector>
```

---

### FILE: `app/src/main/res/drawable/ic_skip_previous.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#FFFFFF"
          android:pathData="M6,6h2v12H6zm3.5,6 8.5,6V6z"/>
</vector>
```

---

### FILE: `app/src/main/res/drawable/ic_play_pause.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#FFFFFF" android:pathData="M8,5v14l11,-7z"/>
</vector>
```

---

### FILE: `app/src/main/res/drawable/ic_skip_next.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#FFFFFF"
          android:pathData="M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z"/>
</vector>
```

---

### FILE: `.github/workflows/build.yml`

```yaml
name: Build APK

# Triggers: every push to main branch, OR manually via Actions tab → "Run workflow"
on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    name: Build Debug APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Accept Android SDK licences
        run: yes | $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager --licenses || true

      - name: Set up Gradle 8.7
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.7'

      - name: Build debug APK
        run: gradle :app:assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: SpotiWrapper-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30
```

---

## After Creating All Files

Run `find SpotiWrapper -type f | sort` and confirm every file matches the tree at the top of
this document.

---

## How to Build and Install the APK

### Step 1 — Create a GitHub repository and push the code

```bash
cd SpotiWrapper
git init
git add .
git commit -m "Initial commit"
git branch -M main
# Create a new empty repo at github.com first, then:
git remote add origin https://github.com/YOUR_USERNAME/SpotiWrapper.git
git push -u origin main
```

### Step 2 — Wait for the build (~3–5 minutes)

Open: `https://github.com/YOUR_USERNAME/SpotiWrapper/actions`

A workflow named **Build APK** will appear. Click it and wait for the green checkmark.

### Step 3 — Download the APK

At the bottom of the completed run, under **Artifacts**, click **SpotiWrapper-debug**.
This downloads a `.zip` containing `app-debug.apk`.

### Step 4 — Install on your phone

1. Transfer `app-debug.apk` to your phone (cable, Drive, email — anything)
2. On your phone: **Settings → Apps → Special app access → Install unknown apps**
   Allow whichever app you used to open the APK (Files, Chrome, etc.)
3. Tap the APK to install
4. Open **SpotiWrapper**, log in to Spotify

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Tracks play but can't skip forward/back | Spotify updated their `data-testid` attributes | Update selector strings in `CustomWebViewClient.kt` and `MainActivity.kt` |
| Player loads but nothing plays | Widevine DRM permission not granted | Confirm `CustomWebChromeClient.onPermissionRequest` grants `RESOURCE_PROTECTED_MEDIA_ID` |
| Logged out every time app restarts | DOM storage disabled | Confirm `settings.domStorageEnabled = true` in `MainActivity.setupWebView()` |
| Notification buttons do nothing | Activity WeakReference is null (app was force-closed) | Open the app, then try the buttons |
| App looks like desktop on a tiny screen | `useWideViewPort` was accidentally set to `true` | Set `settings.useWideViewPort = false` in `MainActivity` |
| Build fails: "AGP requires JDK 17" | Wrong Java version in workflow | Confirm `java-version: '17'` in `build.yml` |
