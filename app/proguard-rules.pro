# JavascriptInterface methods must not be renamed — JavaScript calls them by exact name
-keepclassmembers class com.spotiwrapper.app.WebAppInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep AndroidX Media classes intact
-keep class android.support.v4.media.** { *; }
-keep class android.support.v4.media.session.** { *; }
-keep class androidx.media.** { *; }
-keep class androidx.media.app.** { *; }
