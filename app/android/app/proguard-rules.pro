# NativeStream Android — ProGuard rules

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.nativestream.android.**$$serializer { *; }
-keepclassmembers class com.nativestream.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.nativestream.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# Cast SDK
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.common.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }