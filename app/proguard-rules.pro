# Butler ProGuard / R8 rules.
#
# OkHttp, Ktor, Room, Hilt, Coil and the kotlinx.serialization runtime ship their own
# consumer rules. What they cannot know about is the app's own @Serializable classes,
# whose generated serializers R8 full mode would otherwise strip.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Generated serializers for every @Serializable class (ours and supabase-kt's session types).
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class ** { *** Companion; }
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Compose runtime stability info.
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}

# Tink (via androidx.security-crypto) references errorprone's compile-time annotations,
# which are not on the runtime classpath. Nothing to keep; nothing is missing at runtime.
-dontwarn com.google.errorprone.annotations.**
