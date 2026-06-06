package com.thespotify.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * No-bullshit Update Checker for TheSpotify.
 * 
 * Pings GitHub API to see if a newer version exists.
 * Remembers the last check time to avoid spamming the API (24h limit).
 */
object UpdateManager {

    private const val GITHUB_API_URL = "https://api.github.com/repos/Iamhero337/TheSpotify/releases/latest"
    private const val PREFS_NAME = "UpdatePrefs"
    private const val KEY_LAST_CHECK = "last_check_time"

    fun checkForUpdates(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        val currentTime = System.currentTimeMillis()

        // Only check once every 24 hours
        if (currentTime - lastCheck < 24 * 60 * 60 * 1000L) return

        thread {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val latestVersion = json.getString("tag_name").replace("v", "")
                    val currentVersion = BuildConfig.VERSION_NAME
                    val releaseUrl = json.getString("html_url")

                    // Update last check time
                    prefs.edit().putLong(KEY_LAST_CHECK, currentTime).apply()

                    if (isNewer(latestVersion, currentVersion)) {
                        (context as? android.app.Activity)?.runOnUiThread {
                            showUpdateDialog(context, latestVersion, releaseUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        return try {
            val latestParts = latest.split(".").map { it.toInt() }
            val currentParts = current.split(".").map { it.toInt() }
            val maxLength = maxOf(latestParts.size, currentParts.size)
            
            for (i in 0 until maxLength) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            false // They are equal
        } catch (e: Exception) {
            latest != current
        }
    }

    private fun showUpdateDialog(context: Context, version: String, url: String) {
        AlertDialog.Builder(context)
            .setTitle("Update Available 🚀")
            .setMessage("A new version ($version) of TheSpotify is available. Download it now to get the latest features and fixes!")
            .setPositiveButton("Download") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
