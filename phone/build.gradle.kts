import java.util.Properties

val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    signingConfigs {
        create("release") {
            storeFile = file(signingProps.getProperty("storeFile", "${System.getProperty("user.home")}/.android/debug.keystore"))
            storePassword = signingProps.getProperty("storePassword", "android")
            keyAlias = signingProps.getProperty("keyAlias", "androiddebugkey")
            keyPassword = signingProps.getProperty("keyPassword", "android")
        }
    }

    namespace = "com.pip.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pip.phone"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("release")
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
        // path WearListenerService uses to receive audio assets from the watch.
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
    implementation(libs.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.wear.gms)

    implementation(libs.work.runtime)
    implementation(libs.datastore.prefs)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.security.crypto)
}
