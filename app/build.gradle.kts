import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.my.bontot.sharebox"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.my.bontot.sharebox"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Kunci rilis TIDAK ada di repo: dibaca dari key.properties (di-ignore git) yang menunjuk ke
    // keystore di luar proyek. Tanpa file itu (mis. kontributor), rilis ditandatangani kunci debug
    // supaya build tetap jalan — APK-nya cukup untuk dicoba tapi bukan APK rilis resmi.
    val keyProps = Properties().apply {
        val f = rootProject.file("key.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val hasReleaseKey = keyProps.getProperty("storeFile")?.let { file(it).exists() } == true

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
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
            // Metadata builtins Kotlin: hanya dipakai kotlin-reflect, yang tidak ada di app ini.
            "kotlin/**",
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
