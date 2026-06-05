package com.thespotify.app

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
        const val ACTION        = "com.thespotify.MEDIA_COMMAND"
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
