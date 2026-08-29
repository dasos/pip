# Keep ML Kit model downloader / protobuf produced classes
-keep class com.google.mlkit.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Data Layer / Wearable
-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.wearable.** { *; }