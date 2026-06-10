# ProGuard rules for Monitor APK

# Keep Retrofit interfaces
-keep,allowobfuscation interface com.empresa.monitor.data.api.MonitorApi

# Keep data models
-keep class com.empresa.monitor.data.model.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
