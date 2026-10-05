# ── BuddyDash ProGuard / R8 rules ────────────────────────────────────────────

# Keep line numbers and source files for readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── OkHttp ───────────────────────────────────────────────────────────────────
# OkHttp platform detection uses reflection for optional platform providers
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── DataStore / Protobuf ─────────────────────────────────────────────────────
# AndroidX DataStore Preferences uses protobuf-lite internally.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ── AndroidX Security Crypto / Tink ──────────────────────────────────────────
# EncryptedSharedPreferences uses Tink keysets via reflection
-keepclassmembers class * extends com.google.crypto.tink.Key {
    <fields>;
}
-dontwarn com.google.crypto.tink.**

# ── Kotlin / Annotations ─────────────────────────────────────────────────────
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
