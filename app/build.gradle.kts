import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * Signing.
 *
 * Updates only install when the signing certificate matches the copy already on the device, so
 * the APK users download must always be signed with *one stable key*. Priority:
 *   1. `keystore.properties` (CI generates it via `tools/release-key/make_keystore.py`, which
 *      derives the key from a seed in the repo — byte-identical on every runner).
 *   2. A real release keystore committed under `tools/release-key/` (same generator).
 *   3. The debug key, with a loud warning: still installable, but an update will clash with any
 *      previous debug/release build — this is the "package conflict" everyone hits.
 */
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

/**
 * Versioning has two jobs, and they need different numbers.
 *
 * `appVersionCode` is what Android compares before installing: it must strictly increase or the
 * package installer refuses the update. GITHUB_RUN_NUMBER does that, but it restarts at 1 on a
 * different repo and is 0 for a local build, so the build *date* is folded in as the high part -
 * a build made today always outranks one made yesterday regardless of run numbers.
 *
 * `appVersionName` is what the in-app updater compares. It has to carry the same `yyyyMMdd-HHmm`
 * stamp the released file is named with (GitPulse-20261127-1639-e42f5ae.apk), because the previous
 * constant "1.1.0" contained no date at all: stampOf() returned null and the updater fell back to
 * "the published release always wins", so it offered an update on every single check, forever.
 * Overridable for reproducible builds (CI passes its own UTC stamp).
 */
val ciRunNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toIntOrNull() ?: 0
val buildStamp: String = System.getenv("GITPULSE_BUILD_STAMP")
    ?: SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date())
// Days since the epoch (20_784 on 2026-11-27) x 10_000 keeps versionCode inside Int while leaving
// room for 10_000 builds per day, and stays far below Google Play's 2.1e9 ceiling. A naive
// yyyyMMdd * 1000 would overflow Int outright.
val epochDays: Long = runCatching {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            buildStamp.substring(0, 4).toInt(),
            buildStamp.substring(4, 6).toInt() - 1,
            buildStamp.substring(6, 8).toInt(),
            0,
            0,
            0,
        )
    }
    calendar.timeInMillis / 86_400_000L
}.getOrDefault(0L)
val appVersionCode = (epochDays * 10_000L + (ciRunNumber % 10_000L)).toInt()
val appVersionName = "1.1.0+$buildStamp"

android {
    namespace = "com.mntx.gitpulse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mntx.gitpulse"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        resourceConfigurations += listOf("en", "hi", "ml")

        // Optional OAuth Device-Flow client id. Users can also set it at runtime in Settings.
        buildConfigField(
            "String",
            "GITHUB_CLIENT_ID",
            "\"${project.findProperty("gitpulse.clientId") ?: ""}\""
        )
        buildConfigField("String", "APP_VERSION_LABEL", "\"$appVersionName\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile", "release.keystore"))
                storePassword = keystoreProps.getProperty("storePassword", "")
                keyAlias = keystoreProps.getProperty("keyAlias", "")
                keyPassword = keystoreProps.getProperty("keyPassword", "")
                storeType = keystoreProps.getProperty("storeType", "JKS")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystoreProps.isNotEmpty())
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.biometric:biometric:1.1.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // Robolectric needs the test manifest on the *unit test* classpath for createComposeRule().
    testImplementation("androidx.compose.ui:ui-test-manifest")
    // createAndroidComposeRule<ComponentActivity>() needs the activity artifact on the test classpath.
    testImplementation("androidx.activity:activity-compose:1.9.3")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test:core-ktx:1.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
