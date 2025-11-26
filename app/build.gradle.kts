plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.isekiparcom"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.isekiparcom"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.runtime.android)
    implementation(libs.androidx.navigation.compose.android)
    implementation(libs.androidx.appcompat)

    // ❌ HAPUS BARIS INI:
    // implementation(libs.litert.support.api)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ⚠️ HAPUS GANDA (sudah ada di bom)
    // implementation(libs.androidx.material3)

    implementation("com.google.accompanist:accompanist-swiperefresh:0.32.0")

    // CameraX
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit - Barcode & Text Recognition
    implementation(libs.mlkit.barcode.scanning) {
        exclude(group = "com.google.ai.edge.litert", module = "litert-api")
        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")
    }
    implementation(libs.mlkit.text.recognition) {
        exclude(group = "com.google.ai.edge.litert", module = "litert-api")
        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")
    }
    implementation(libs.mlkit.text.recognition.chinese) {
        exclude(group = "com.google.ai.edge.litert", module = "litert-api")
        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")
    }

    // TensorFlow Lite (tetap gunakan versi resmi)
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tensorflow.lite.metadata)

    implementation(libs.androidx.material.icons.extended)

    implementation(libs.okhttp)

    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
}