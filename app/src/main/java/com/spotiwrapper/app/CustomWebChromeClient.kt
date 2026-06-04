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
