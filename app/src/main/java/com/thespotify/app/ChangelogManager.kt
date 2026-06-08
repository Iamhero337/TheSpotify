package com.thespotify.app

import android.app.AlertDialog
import android.content.Context

/**
 * Handles showing a "What's New" popup after an update.
 */
object ChangelogManager {

    private const val PREFS_NAME = "AppPrefs"
    private const val KEY_LAST_VERSION = "last_run_version_code"

    fun checkAndShowChangelog(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastVersion = prefs.getInt(KEY_LAST_VERSION, 0)
        val currentVersion = BuildConfig.VERSION_CODE

        if (currentVersion > lastVersion) {
            // Only show if it's not the first ever install (lastVersion > 0)
            // or if you want to show a "Welcome" message for first install.
            showChangelogDialog(context)
            
            // Save the current version so the popup only shows once
            prefs.edit().putInt(KEY_LAST_VERSION, currentVersion).apply()
        }
    }

    private fun showChangelogDialog(context: Context) {
        val message = """
            What's new in version 1.1.x:
            
            🖼️ Real Album Art: You'll now see high-quality cover art on your lock screen and notification shade.
            
            🌙 Sleep Timer: Tap the floating "moon" button to set a timer. The music will stop automatically!
            
            🔄 Smooth Updates: We've fixed the signature key so future updates install without having to uninstall first.
            
            🛠️ Stability: Fixed a potential crash during background update checks.
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle("What's New! 🚀")
            .setMessage(message)
            .setPositiveButton("Awesome", null)
            .show()
    }
}
