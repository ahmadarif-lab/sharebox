import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ahmadarif.sharebox"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ahmadarif.sharebox"
        minSdk = 26
        targetSdk = 36
        versionCode = 45
        versionName = "1.4.6"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(
                (project.findProperty("SHAREBOX_STORE_FILE") as String?) ?: "keystore/sharebox.keystore"
            )
            storePassword = (project.findProperty("SHAREBOX_STORE_PASSWORD") as String?) ?: "REMOVED"
            keyAlias = (project.findProperty("SHAREBOX_KEY_ALIAS") as String?) ?: "sharebox"
            keyPassword = (project.findProperty("SHAREBOX_KEY_PASSWORD") as String?) ?: "REMOVED"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.version",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "kotlin-tooling-metadata.json",
            "DebugProbesKt.bin",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Satu-satunya dependency runtime: QR encoder (R8 akan membuang sisanya)
    implementation("com.google.zxing:core:3.4.0")
}
