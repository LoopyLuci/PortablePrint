# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools settings.

# Keep StreamSync protocol classes
-keep class com.streamsync.android.protocol.** { *; }
-keep class com.streamsync.android.model.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Ktor WebSocket
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep protobuf
-keep class com.google.protobuf.** { *; }
