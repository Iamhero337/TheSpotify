package com.thespotify.app

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
