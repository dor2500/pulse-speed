plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Google sign-in and cloud sync need a Firebase project. Drop its google-services.json into app/
// (see docs/firebase-setup.md); without it the app builds and runs with sign-in hidden.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.dorp.pulse"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dorp.pulse"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.9.1"
    }

    // "github" is the sideloaded build that updates itself from GitHub Releases.
    // "play" is for Google Play, which forbids self-updates and the install-packages permission.
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    // The release key comes from the environment, or user's local keystore
    val defaultKeystore = File(System.getProperty("user.home"), ".pulse-speed/release.jks")
    val defaultPasswordFile = File(System.getProperty("user.home"), ".pulse-speed/release-password.txt")
    val keystorePath = System.getenv("PULSE_KEYSTORE_PATH") ?: if (defaultKeystore.exists()) defaultKeystore.absolutePath else null
    val keystorePassword = System.getenv("PULSE_KEYSTORE_PASSWORD") ?: if (defaultPasswordFile.exists()) defaultPasswordFile.readText().trim() else null

    signingConfigs {
        if (keystorePath != null && keystorePassword != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = System.getenv("PULSE_KEY_ALIAS") ?: "pulse"
                keyPassword = keystorePassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (signingConfigs.findByName("release") != null) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }
    // Pulse switches language in-app (Settings → Language), so the Play bundle must ship every language
    // instead of only the device's one.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")

    // Google sign-in (Credential Manager) + Firebase Auth / Firestore for history sync
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // WebSocket client for M-Lab's NDT7 test
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
