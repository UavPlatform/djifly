plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt")
}

// Exclude old support library to avoid conflicts with AndroidX
configurations.all {
    resolutionStrategy {
        // Force using AndroidX instead of old support library
        force("androidx.media:media:1.0.0")
    }
    exclude(group = "com.android.support", module = "support-media-compat")
    exclude(group = "com.android.support", module = "support-compat")
}

android {
    namespace = "com.fuwaki.djifly"
    compileSdk = 35

    val droneBackendBaseUrl = ((project.findProperty("DRONE_BACKEND_BASE_URL") as String?) ?: "")
        .trim()
        .replace("\"", "\\\"")

    defaultConfig {
        applicationId = "com.fuwaki.djifly"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }

        // Set API key from gradle.properties
        manifestPlaceholders["API_KEY"] = project.findProperty("AIRCRAFT_API_KEY") ?: ""
        buildConfigField("String", "DRONE_BACKEND_BASE_URL", "\"$droneBackendBaseUrl\"")
    }

    buildTypes {
        debug {
            // 核心魔法：强行关闭调试标志，骗过大疆的防破解壳
            isDebuggable = false

            // 下面这些保持默认，确保编译速度依然飞快
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
            pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
        }
    }

    packagingOptions {
        // DJI MSDK native libraries - do not strip
        doNotStrip("*/*/libconstants.so")
        doNotStrip("*/*/libdji_innertools.so")
        doNotStrip("*/*/libdjibase.so")
        doNotStrip("*/*/libDJICSDKCommon.so")
        doNotStrip("*/*/libDJIFlySafeCore-CSDK.so")
        doNotStrip("*/*/libdjifs_jni-CSDK.so")
        doNotStrip("*/*/libDJIRegister.so")
        doNotStrip("*/*/libdjisdk_jni.so")
        doNotStrip("*/*/libDJIUpgradeCore.so")
        doNotStrip("*/*/libDJIUpgradeJNI.so")
        doNotStrip("*/*/libDJIWaypointV2Core-CSDK.so")
        doNotStrip("*/*/libdjiwpv2-CSDK.so")
        doNotStrip("*/*/libFlightRecordEngine.so")
        doNotStrip("*/*/libvideo-framing.so")
        doNotStrip("*/*/libwaes.so")
        doNotStrip("*/*/libagora-rtsa-sdk.so")
        doNotStrip("*/*/libc++.so")
        doNotStrip("*/*/libc++_shared.so")
        doNotStrip("*/*/libmrtc_28181.so")
        doNotStrip("*/*/libmrtc_agora.so")
        doNotStrip("*/*/libmrtc_core.so")
        doNotStrip("*/*/libmrtc_core_jni.so")
        doNotStrip("*/*/libmrtc_data.so")
        doNotStrip("*/*/libmrtc_log.so")
        doNotStrip("*/*/libmrtc_onvif.so")
        doNotStrip("*/*/libmrtc_rtmp.so")
        doNotStrip("*/*/libmrtc_rtsp.so")
    }
}

dependencies {
    // DJI MSDK Dependencies
    implementation(project(":android-sdk-v5-uxsdk"))
    implementation("com.dji:dji-sdk-v5-aircraft:5.17.0")
    implementation("com.dji:dji-sdk-v5-networkImp:5.17.0")
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    compileOnly("com.dji:dji-sdk-v5-aircraft-provided:5.17.0")

    // Existing Compose dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // ConstraintLayout (required for UXSDK widgets)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("com.tencent.liteav:LiteAVSDK_TRTC:13.2.0.20058")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
