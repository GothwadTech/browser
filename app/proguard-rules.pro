# TV Browser ProGuard / R8 Performance & Size Rules

# Strip excessive Android debug logging in release builds to eliminate string allocations & logging overhead on TV D-Pad navigation
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Optimize Room & SQLite Database
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}

# WebKit & JavaScript Interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# WebEngine and Models
-keep class com.bansify.internet.webengine.webview.WebViewWebEngine { *; }

-keepclassmembers class com.bansify.internet.model.** {
   public *;
}
-keepclassmembers class com.brave.adblock.AdBlockClient {
   public *;
   private *;
}
-keepclassmembers class com.bansify.internet.webengine.webview.AndroidJSInterface {
   public *;
   private *;
}
