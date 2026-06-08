# TheSpotify — Project Guide for Claude Code

> Keep this file lean. It is loaded into context every session, so document **stable
> architecture, conventions, and gotchas** — never inline full source files (the repo already
> holds them). This file must stay well under 40,000 characters.

## What this app is

A native Android wrapper (`com.thespotify.app`, label **TheSpotify**) around Spotify's
**web player** (`open.spotify.com`) inside a single `WebView`. Core tricks:

- **Desktop User-Agent** → Spotify serves the full-featured desktop web app (on-demand
  playback, unlimited skips, no shuffle lock).
- **`useWideViewPort = false`** → Spotify's own responsive CSS renders the compact
  phone-width layout despite the desktop UA. Desktop *features*, mobile *appearance*.
- **Network ad-blocking** via `shouldInterceptRequest` returning empty responses for known
  ad/tracking domains.
- **Injected CSS** hides upgrade/premium prompts; **injected JS** detects and skips
  stream-stitched audio ads.
- **Foreground service** + **MediaSession** keep audio playing with screen off and provide
  lock-screen / notification controls.

## File map (what lives where)

```
app/src/main/java/com/thespotify/app/
  SplashActivity.kt        Animated splash (~1.9s), then launches MainActivity
  MainActivity.kt          WebView setup, service binding, JS media commands, lifecycle
  CustomWebViewClient.kt   URL gating, network ad-block, 4 layers of CSS/JS injection
  CustomWebChromeClient.kt Grants Widevine DRM permission (RESOURCE_PROTECTED_MEDIA_ID)
  AdBlocker.kt             Domain/URL blocklists + empty-response helper
  WebAppInterface.kt       JS→Kotlin bridge (window.SpotiWrapper.updateMetadata/log)
  MediaPlaybackService.kt  Foreground service, MediaSession, notification, WakeLock
  MediaControlReceiver.kt  Notification button broadcasts → WebView JS clicks
  UpdateManager.kt         24h GitHub-release update checker (see below)
app/src/main/res/...       Layouts, drawables, launcher/splash assets, themes
app/thespotify.keystore    Static release keystore (do NOT regenerate — see Gotchas)
.github/workflows/build.yml  CI: builds APK on push to main / manual dispatch
```

## Key mechanisms & conventions

- **JS↔native bridge:** injected JS calls `window.SpotiWrapper.updateMetadata(title, artist)`.
  The bridge runs on the WebView thread — `WebAppInterface` posts to the main thread before
  touching Android services. Track changes flow:
  injected MutationObserver → `WebAppInterface` → `MainActivity.onMetadataUpdate` →
  `MediaPlaybackService.updateNotification`.
- **Notification controls:** button taps → `PendingIntent` broadcast → `MediaControlReceiver`
  → `MainActivity.executeMediaCommand` → `evaluateJavascript` clicks the matching Spotify
  control. `MainActivity` is reached via a `WeakReference` (`MainActivity.instance`).
- **Spotify selectors are `data-testid` based** (e.g. `control-button-skip-forward`). These are
  the single most fragile thing in the app — if skip/play/metadata break, Spotify changed a
  `data-testid`. Update them in `CustomWebViewClient.kt` and `MainActivity.kt` together.
- **`onPause()` deliberately does NOT call `webView.onPause()`** — doing so suspends the
  Chromium engine and kills background audio. Leave it as-is.
- **Background playback** depends on the foreground service staying up; don't move audio logic
  out of it.

## Build & release process

- **CI:** `.github/workflows/build.yml` builds on every push to `main` and on manual dispatch
  (Actions tab → Run workflow). JDK 17, Gradle 8.7, `gradle :app:assembleDebug`. APK is uploaded
  as an artifact.
- **Public releases:** `UpdateManager` polls
  `https://api.github.com/repos/Iamhero337/TheSpotify/releases/latest` at most once per 24h and
  prompts the user when `tag_name` (minus `v`) is newer than `BuildConfig.VERSION_NAME`. So a
  public release = create a GitHub Release whose tag matches the new `versionName`.
- **Version bumps:** edit `versionCode` (integer, +1) and `versionName` in `app/build.gradle`.
  Currently `versionCode 4`, `versionName "1.0.3"`. Bump BOTH and tag the release to match.

## Gotchas (read before changing these)

- **The keystore (`app/thespotify.keystore`) is static and permanent.** Android refuses to
  install an update signed by a different key. Never regenerate it or let CI generate a random
  debug key for releases — that bricks the update path for every existing user.
- **Never add Spotify's own CDNs to `AdBlocker`** (`*.scdn.co`, etc.) — they serve real music
  and album art. Only block ad/tracking domains.
- **Widevine permission is mandatory** — without `CustomWebChromeClient` granting
  `RESOURCE_PROTECTED_MEDIA_ID`, the player loads but no track will buffer.
- **Update check timing:** the dialog can fire while the activity is finishing; the
  `isFinishing/isDestroyed` guard in `UpdateManager` prevents a `BadTokenException` crash.

## Out of scope (do not build)

- **Spotify audio downloading / offline ripping.** Spotify streams are Widevine-encrypted; the
  decrypted bytes are never exposed to the WebView, so it is technically impossible here, and
  circumventing the DRM or mass-downloading tracks from third-party sources is illegal and would
  expose the repo to DMCA takedown. Not a feature to add.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Can't skip / play-pause / no metadata | Spotify changed a `data-testid` | Update selectors in `CustomWebViewClient.kt` + `MainActivity.kt` |
| Player loads but nothing plays | Widevine not granted | Check `CustomWebChromeClient.onPermissionRequest` |
| Logged out on restart | DOM storage off | `settings.domStorageEnabled = true` in `MainActivity` |
| Update won't install over old build | Signing key changed | Restore the static `thespotify.keystore` |
| Looks like desktop on a tiny screen | viewport trick disabled | `settings.useWideViewPort = false` |
