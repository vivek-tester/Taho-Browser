# Taho Browser R8 / Proguard Configuration

# Keep GeckoView native JNI bindings and public entrypoints
-keep class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.geckoview.**

# Keep Contract models for cross-app IPC transfer serialization
-keep class app.taho.browser.contract.** { *; }

# Keep Room database and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Compose rules
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }

# Preserve LineNumberTable and SourceFile for meaningful release crash stacks without leaking secrets
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod

# Strip all debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
