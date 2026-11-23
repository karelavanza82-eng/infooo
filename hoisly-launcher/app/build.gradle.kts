plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hoisly.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hoisly.launcher"
        minSdk = 31
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"
    }

    // Release APKs must never be signed with Android's debug key. When all release
    // signing variables are supplied, use the project's release keystore; otherwise
    // Gradle produces an unsigned release artifact for signing outside this build.
    val releaseSigningValues = listOf(
        providers.environmentVariable("HOISLY_KEYSTORE_FILE").orNull,
        providers.environmentVariable("HOISLY_KEYSTORE_PASSWORD").orNull,
        providers.environmentVariable("HOISLY_KEY_ALIAS").orNull,
        providers.environmentVariable("HOISLY_KEY_PASSWORD").orNull,
    )
    val releaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() }
    require(releaseSigningConfigured || releaseSigningValues.all { it.isNullOrBlank() }) {
        "Release signing requires HOISLY_KEYSTORE_FILE, HOISLY_KEYSTORE_PASSWORD, HOISLY_KEY_ALIAS, and HOISLY_KEY_PASSWORD together."
    }
    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseSigningValues[0]!!)
                storePassword = releaseSigningValues[1]!!
                keyAlias = releaseSigningValues[2]!!
                keyPassword = releaseSigningValues[3]!!
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            // Skip PNG recompression during the edit/build/install cycle.
            isCrunchPngs = false
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // The in-app language picker switches languages at runtime, so an app bundle must keep
    // every translation in the base APK instead of per-language splits.
    bundle {
        language {
            enableSplit = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // Only a few dozen glyphs are used; R8 strips the rest in release builds.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in JVM tests; the real one tests layout/weather JSON parsing.
    testImplementation("org.json:json:20260814")
}
