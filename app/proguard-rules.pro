# قوانین ProGuard (اکثرا مربوط به کتابخانه‌های بومی که از طریق JNI صدا می‌شوند)
-keep class com.aurora.r.AetherCore { *; }
-keep class com.aurora.r.AetherCore$* { *; }
-keep class com.aurora.r.TunBridge { *; }
-keep class com.aurora.r.TunBridge$* { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
