# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard/proguard-android.txt

# Keep Room entity and DAO class names for reflective instantiation
-keep class com.zack.recomptracker.data.local.entity.** { *; }
-keep interface com.zack.recomptracker.data.local.dao.** { *; }

# Keep Kotlin serialization metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep data classes used in serialized preferences
-keep class com.zack.recomptracker.data.preferences.PlanPreferences { *; }
-keep class com.zack.recomptracker.data.preferences.UiPreferences { *; }

# OkHttp / Retrofit (for OpenFoodFacts API)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ML Kit barcode scanning
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Health Connect
-keep class androidx.health.** { *; }
-dontwarn androidx.health.**

# Baseline Profile installer
-keep class androidx.profileinstaller.** { *; }
-dontwarn androidx.profileinstaller.**
