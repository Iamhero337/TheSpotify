package com.thespotify.app

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * JavaScript → Kotlin bridge.
 *
 * Methods annotated @JavascriptInterface are callable from JS as:
 *   window.TheSpotify.updateMetadata("Song Title", "Artist Name")
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
            android.util.Log.d("TheSpotify/JS", message)
        }
    }
}
