plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dorp.pulse"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dorp.pulse"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.6.0"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
}
