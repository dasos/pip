import java.util.Properties

val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val isReleaseSigningConfigured = releaseSigningKeys.all { signingProps.getProperty(it)?.isNotBlank() == true }
val isReleaseBuildRequested = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }

if (isReleaseBuildRequested && !isReleaseSigningConfigured) {
    error("Release builds require keystore.properties with: ${releaseSigningKeys.joinToString()}")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    val releaseSigning = if (isReleaseSigningConfigured) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(requireNotNull(signingProps.getProperty("storeFile")))
            storePassword = requireNotNull(signingProps.getProperty("storePassword"))
            keyAlias = requireNotNull(signingProps.getProperty("keyAlias"))
            keyPassword = requireNotNull(signingProps.getProperty("keyPassword"))
        }
    } else {
        null
    }

    namespace = "com.pip.wear"
    compileSdk = 35

    defaultConfig {
        // Wear Data Layer identifies matching phone/watch apps by application ID and signing key.
        applicationId = "com.pip"
        minSdk = 30
        targetSdk = 35
        versionCode = 5
        versionName = "0.2.3"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            signingConfig = releaseSigning
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        warning += "NotificationIcon"
        // BIND_LISTENER is deprecated but deliberate: it's the low-latency
        // path WearCapabilityListenerService uses to receive audio assets.
        disable += "WearableBindListener"
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    implementation(libs.wear.tiles)
    implementation(libs.wear.tiles.material)
    implementation(libs.wear.gms)

    implementation(libs.work.runtime)
    implementation(libs.datastore.prefs)
    implementation(libs.concurrent.futures)
}
