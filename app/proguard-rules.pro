# ── R8 / ProGuard rules for AeroBox ──

# Keep Room entities and DAO (required by annotation processor)
-keep class com.aerobox.data.model.** { *; }
-keep class com.aerobox.data.database.** { *; }

# Keep kotlinx serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.aerobox.**$$serializer { *; }
-keepclassmembers class com.aerobox.** {
    *** Companion;
}
-keepclasseswithmembers class com.aerobox.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# Keep JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Strip verbose logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Optimize aggressively
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
