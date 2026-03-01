# Keep Ktor HTTP client classes
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, KotlinMetadata
-dontnote kotlinx.serialization.AnnotationsKt
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.xenlon.instadownloader.model.**$$serializer { *; }
-keepclassmembers class com.xenlon.instadownloader.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.xenlon.instadownloader.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Keep WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# OkHttp/Conscrypt/BouncyCastle platform warnings
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn dalvik.system.CloseGuard

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Suppress SLF4J warnings from Ktor
-dontwarn org.slf4j.**
