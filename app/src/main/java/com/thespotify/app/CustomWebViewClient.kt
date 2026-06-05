package com.thespotify.app

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
        injectPlaybackStateMonitor(view) // Layer 5: Report play/pause state to the service
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
     * LAYER 3 — JavaScript: Detect and silence audio ads.
     *
     * Spotify sometimes stitches ads directly into the audio stream server-side.
     * These can't be blocked by domain filtering (same CDN as music) and skipping is
     * usually disabled during an ad. So the reliable action is to MUTE the actual media
     * element for the entire duration of the ad and unmute the moment it ends.
     *
     * Each tick:
     *  - If an ad is showing: mute every media element, and click skip if it's enabled.
     *  - When the ad disappears: unmute so music returns.
     * Muting the underlying <video>/<audio> element silences output even for DRM media,
     * which the old playbackRate trick could not affect.
     */
    private fun injectAudioAdMonitor(view: WebView) {
        val js = """
            (function(){
                if(window._swMonitor)return;
                window._swMonitor=true;
                var wasAd=false;
                setInterval(function(){
                    try{
                        var isAd=!!document.querySelector('[data-testid="audio-ad"]');
                        var els=document.querySelectorAll('video,audio');
                        if(isAd){
                            wasAd=true;
                            for(var i=0;i<els.length;i++){els[i].muted=true;}
                            var skip=document.querySelector('[data-testid="control-button-skip-forward"]');
                            if(skip&&!skip.disabled)skip.click();
                        }else if(wasAd){
                            wasAd=false;
                            for(var k=0;k<els.length;k++){els[k].muted=false;}
                        }
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
     * When the track title changes it calls window.TheSpotify.updateMetadata(title, artist)
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
                            if(window.TheSpotify)window.TheSpotify.updateMetadata(title,artist);
                        }
                    }catch(e){}
                });
                obs.observe(document.body||document.documentElement,
                    {subtree:true,childList:true,characterData:true});
            })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }

    /**
     * LAYER 5 — JavaScript: Playback state monitor.
     *
     * The Android side needs to know whether music is ACTUALLY playing so it can:
     *  1. Only show the system media controls once real playback starts
     *     (otherwise the phone shows a "playing" indicator the moment the app opens)
     *  2. Reflect play vs pause in the notification + lock screen / dynamic island
     *
     * Ground truth is the media element's own `paused` flag — this is language
     * independent and reflects what's actually coming out of the speaker. We fall back
     * to the play/pause button's aria-label only if no media element is present yet.
     * We poll once a second and report only on change.
     */
    private fun injectPlaybackStateMonitor(view: WebView) {
        val js = """
            (function(){
                if(window._swState)return;
                window._swState=true;
                var last=null;
                function isPlaying(){
                    var els=document.querySelectorAll('video,audio');
                    for(var i=0;i<els.length;i++){
                        var el=els[i];
                        if(!el.paused&&!el.ended)return true;
                    }
                    if(els.length>0)return false;
                    // No media element yet: fall back to the button's accessibility label.
                    var btn=document.querySelector('[data-testid="control-button-playpause"]');
                    if(btn){
                        var label=(btn.getAttribute('aria-label')||'').toLowerCase();
                        if(label.indexOf('paus')!==-1)return true; // showing "Pause" => playing
                    }
                    return false;
                }
                setInterval(function(){
                    try{
                        var playing=isPlaying();
                        if(playing!==last){
                            last=playing;
                            if(window.TheSpotify&&window.TheSpotify.updatePlaybackState){
                                window.TheSpotify.updatePlaybackState(playing);
                            }
                        }
                    }catch(e){}
                },1000);
            })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }
}
