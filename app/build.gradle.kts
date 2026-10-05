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
    // Google MediaPipe LLM Inference API (maintenance line)
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    // Google LiteRT-LM (next-gen on-device engine with NPU acceleration & MTP)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.2")

    // tasks-vision brings in the com.google.mediapipe.framework.image classes
    // (MPImage / BitmapImageBuilder) used to attach a photo to a prompt for
    // vision-capable Gemma models (e.g. Gemma 3 Nano / multimodal .task
    // builds). Kept as a separate artifact from tasks-genai on purpose —
    // that's why these classes wouldn't resolve with tasks-genai alone.
    //
    // Pinned to 0.10.26, NOT 0.10.24 — confirmed against Maven Central that
    // tasks-vision was never published at 0.10.24 (that's what the
    // "Could not resolve tasks-vision:0.10.24" build failure was). Google
    // publishes tasks-genai and tasks-vision on separate, not-always-in-sync
    // schedules (see github.com/google-ai-edge/mediapipe issues #6047 and
    // #6098) — tasks-vision jumped straight from 0.10.15 to 0.10.26 while
    // tasks-genai has stayed at 0.10.24. 0.10.26 is the closest published
    // tasks-vision version above the gap, and is Google's current stable
    // MediaPipe line. If a real build ever hits a runtime crash that looks
    // like a native-library mismatch (SIGSEGV on model load, not a Gradle
    // resolution error), that's this cross-artifact version skew — try
    // pinning both to the same exact version if one becomes available, or
    // drop tasks-vision to 0.10.15 to match tasks-genai's release era more
    // closely (at the cost of missing anything vision-side added after it).
    implementation("com.google.mediapipe:tasks-vision:0.10.26")

    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
