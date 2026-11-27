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

# ---- R8 safety net ----------------------------------------------------------
# The release APK is minified + resource-shrunk, so anything reflective must be kept
# explicitly; a stripped class fails at runtime, not at build time.
-keep class com.mntx.gitpulse.MainActivity { *; }
-keep class com.mntx.gitpulse.GitPulseApp { *; }
-keep class com.mntx.gitpulse.CrashCatcher { *; }
-keep class com.mntx.gitpulse.update.** { *; }

# Compose runtime reflection on composable lambdas.
-dontwarn androidx.compose.**
-keepclassmembers class androidx.compose.runtime.** { *; }

# Kotlin metadata is needed for coroutines + serialization descriptors.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature,Exceptions,SourceFile,LineNumberTable
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# Resource shrinking must not drop the fonts the theme references by name.
-keep class com.mntx.gitpulse.R$font { *; }
