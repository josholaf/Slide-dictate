plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.slidedictate"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.slidedictate"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_shared") } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    ndkVersion = "26.1.10909125"
    packaging { jniLibs { useLegacyPackaging = false } }
    signingConfigs {
        // Store release: point SLIDE_KEYSTORE at your .jks (or set slide.keystore in
        // ~/.gradle/gradle.properties, never in git) plus passwords/alias. CI sets env vars.
        create("slideRelease") {
            val ks = System.getenv("SLIDE_KEYSTORE")
                ?: (project.findProperty("slide.keystore") as String?)
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("SLIDE_KEYSTORE_PASSWORD")
                    ?: (project.findProperty("slide.keystorePassword") as String?)
                keyAlias = System.getenv("SLIDE_KEY_ALIAS")
                    ?: (project.findProperty("slide.keyAlias") as String?)
                keyPassword = System.getenv("SLIDE_KEY_PASSWORD")
                    ?: (project.findProperty("slide.keyPassword") as String?)
                    ?: storePassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val hasKey = !System.getenv("SLIDE_KEYSTORE").isNullOrEmpty() || project.hasProperty("slide.keystore")
            if (!hasKey) logger.warn("No SLIDE_KEYSTORE/slide.keystore: release signs with debug key (sideload only).")
            signingConfig = signingConfigs.getByName(if (hasKey) "slideRelease" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
