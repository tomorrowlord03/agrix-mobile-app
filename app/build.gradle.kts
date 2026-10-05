plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.protoprojects.agrix"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.protoprojects.agrix"
        minSdk = 26 // MediaPipe LLM Inference requires API 24+, we target 26+ for mid-range coverage
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // ABI split keeps the APK small — device only downloads the native libs it needs.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
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

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Extended icon set (Grass, TrendingUp, Terrain, BugReport, HealthAndSafety,
    // Grade, Savings, WaterDrop, Pets, CloudDownload, FolderOpen, etc.) — these
    // are NOT in the default material3 icon set, which is why the build failed.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Local, on-disk key-value storage (no server, no cloud sync)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ---- On-device Gemma inference (Google MediaPipe & LiteRT-LM API) ----
        // Google LiteRT-LM (production-ready on-device engine with NPU acceleration & MTP)
        // Replaces MediaPipe LLM Inference (maintenance-only as of mid-2026)
        implementation("com.google.ai.edge.litertlm:litertlm-android:0.14.0")

        // tasks-vision brings in the com.google.mediapipe.framework.image classes
        // (MPImage / BitmapImageBuilder) used to attach a photo to a prompt for
        // vision-capable Gemma models (e.g. Gemma 3n E2B/E4B, not 1B IT).
        implementation("com.google.mediapipe:tasks-vision:0.10.27")

    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
