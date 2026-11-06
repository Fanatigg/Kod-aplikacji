# ProGuard rules for SumaKamera
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.example.** { *; }
-dontwarn androidx.camera.**
-keep class androidx.camera.** { *; }

