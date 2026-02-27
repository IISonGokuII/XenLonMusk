# Keep Ktor HTTP client classes
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.xenlon.instadownloader.model.**$$serializer { *; }
-keepclassmembers class com.xenlon.instadownloader.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.xenlon.instadownloader.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Keep WorkManager
-keep class androidx.work.** { *; }
