plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.padelboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.padelboard"
        minSdk = 24
        targetSdk = 34
        versionCode = 15
        versionName = "2.3.5"
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
        create("release") {
            // Using debug keys for release just to make it installable for testing
            // if the user doesn't have a keystore yet.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    flavorDimensions += "version"
    productFlavors {
        create("legacy") {
            dimension = "version"
            minSdk = 24
            versionName = "2.3.5-legacy"
        }
        create("modern") {
            dimension = "version"
            minSdk = 28
            versionNameSuffix = "-modern"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Disable minify for legacy/Android 7.1 to avoid R8-related runtime issues on older ART versions
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
