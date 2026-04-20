# ═══════════════════════════════════════════════════════════════════════════════
#  MagicFolder — ProGuard / R8 rules
# ═══════════════════════════════════════════════════════════════════════════════

# Preserve stack trace line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Hilt ─────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}
-dontwarn dagger.hilt.**

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}
-dontwarn androidx.room.**

# ── Retrofit + OkHttp ────────────────────────────────────────────────────────
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Gson ─────────────────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }
# Keep all model/data classes used with Gson (Scryfall API responses)
-keep class com.mmg.manahub.core.data.remote.** { *; }

# ── App models (Room entities, domain models) ────────────────────────────────
-keep class com.mmg.manahub.core.data.local.entity.** { *; }
-keep class com.mmg.manahub.core.domain.model.** { *; }
-keep class com.mmg.manahub.feature.game.model.** { *; }

# ── Coil ─────────────────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── DataStore ─────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ── Jetpack Navigation ────────────────────────────────────────────────────────
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ── Compose (R8 handles most, but keep Previews out of release) ───────────────
-assumenosideeffects class androidx.compose.ui.tooling.preview.PreviewKt { *; }

# ── Supabase SDK (Ktor + kotlinx.serialization) ──────────────────────────────
# Ktor HTTP client internals used by the Supabase SDK
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlin serialization — required for Supabase request/response models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.mmg.manahub.**$$serializer { *; }
-keepclassmembers class com.mmg.manahub.** {
    *** Companion;
}
-keepclasseswithmembers class com.mmg.manahub.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep all @Serializable classes so Supabase can deserialize responses
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static final kotlinx.serialization.descriptors.SerialDescriptor serialVersionUID;
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# Supabase Auth data models (session, user, token)
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# Auth feature domain models (used in Supabase JSON serialization)
-keep class com.mmg.manahub.feature.auth.** { *; }
-dontwarn com.mmg.manahub.feature.auth.**

# ── Strip all logs in release ─────────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static boolean isLoggable(...);
}
