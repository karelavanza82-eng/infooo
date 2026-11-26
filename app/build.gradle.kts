
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.hoisly.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hoisly.launcher"
        minSdk = 28
        targetSdk = 37
        versionCode = 20101
        versionName = "2.1.1"
    }

    // CI signs releases with the project keystore passed through the environment.
    val releaseKeystore = providers.environmentVariable("HOISLY_KEYSTORE_FILE").orNull

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storeType = "PKCS12"
                storePassword = providers.environmentVariable("HOISLY_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("HOISLY_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("HOISLY_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            isCrunchPngs = false
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName(
                if (releaseKeystore != null) "release" else "debug"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
    }

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
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
