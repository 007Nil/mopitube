plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // ✅ Only ONE serialization plugin (correct one)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("kotlin-kapt")

    // ✅ Compose plugin stays
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace = "com.nil.mopitube"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nil.mopitube"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
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
        freeCompilerArgs = freeCompilerArgs.filterNot { it == "-Werror" }
    }
}

dependencies {

    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))

    // Compose Core
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.common)
    implementation(libs.androidx.lifecycle.process)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Material3
    implementation("androidx.compose.material3:material3")

    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended")

    // Foundation & Runtime
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Animation
    implementation("androidx.compose.animation:animation")

    // Coil Image loader
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // OkHttp WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version") // For coroutine support
    kapt("androidx.room:room-compiler:$room_version")      // <-- THIS LINE IS NOW CORRECT

    // Lifecycle (Process lifecycle owner and runtime)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-process:2.6.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
