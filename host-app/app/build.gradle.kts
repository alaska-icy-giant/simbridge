plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.simbridge.host"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simbridge.host"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
        // AGP lint crashes on Java 25; disable fatal lint for release builds
        checkReleaseBuilds = false
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.jvmArgs("-Dnet.bytebuddy.experimental=true")
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Compose UI Testing
    val composeBomTest = platform(libs.compose.bom)
    androidTestImplementation(composeBomTest)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.navigation.testing)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")

    // AndroidX
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.core.ktx)

    // Network
    implementation(libs.okhttp)
    implementation(libs.gson)

    // WebRTC
    implementation(libs.webrtc)

    // Google Sign-In
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // Unit testing — JUnit 5 + MockK
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("io.mockk:mockk:1.13.16")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.0.0")
}
