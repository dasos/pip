# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Data Layer / Wearable
-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.wearable.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }

# Tink (security-crypto) — errorprone annotations are compile-time only
-dontwarn com.google.errorprone.annotations.**