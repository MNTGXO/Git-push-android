# ---- Kotlin serialization ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mntx.gitpulse.**$$serializer { *; }
-keepclassmembers class com.mntx.gitpulse.** {
    *** Companion;
}
-keepclasseswithmembers class com.mntx.gitpulse.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Compose ----
-keep class androidx.compose.runtime.** { *; }

# ---- App keep ----
-keep class com.mntx.gitpulse.data.model.** { *; }
