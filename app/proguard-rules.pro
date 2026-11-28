# Project-specific R8/ProGuard rules.
# Rules for kotlinx-serialization, Ktor, Room etc. are added alongside those dependencies.

# kotlinx-serialization: keep @Serializable DTOs used by Ktor + Json
-keep class com.code7.techupdates.data.remote.**Dto {
    <fields>;
}
-keep class com.code7.techupdates.data.remote.HnDtos { *; }

# Ktor content negotiation needs the serialization module at runtime
-keepclassmembers class kotlinx.serialization.internal.** { *; }
-keepclassmembers class kotlinx.serialization.** { <init>(...); }

# Room: the Room plugin already generates keep rules; keep generated DAOs
-keep class com.code7.techupdates.data.local.**_Impl { *; }

# Coil 3: keep image transformers (markdown renderer uses Coil3ImageTransformerImpl)
-keep class com.mikepenz.markdown.coil3.** { *; }

# DataStore: keep generated classes
-keep class com.code7.techupdates.data.local.SettingsStore { *; }
