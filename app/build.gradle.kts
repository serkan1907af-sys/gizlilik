plugins {
    id("com.android.application")
}

android {
    namespace = "com.parabolsoft.retrokonsol"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.parabolsoft.retrokonsol"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
        // Deneme APK'sinda Pro her zaman acik. Magaza (AAB) surumunde kapali, Google Play'den satin alinir.
        val proUnlocked = System.getenv("PRO_UNLOCKED") == "1"
        buildConfigField("boolean", "PRO_UNLOCKED", proUnlocked.toString())
    }

    buildFeatures { buildConfig = true }

    // Oyun motorlari (.so) uygulama klasorune acilip dlopen ile yuklendigi icin eski paketleme sart.
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    // NOT: ci/test-key.p12 sadece DENEME anahtaridir. Play Store icin kendi anahtarini kullan.
    signingConfigs {
        create("retro") {
            storeFile = file(System.getenv("RETRO_KS_PATH") ?: "retro.jks")
            storePassword = System.getenv("RETRO_KS_PASS") ?: ""
            keyAlias = "retro"
            keyPassword = System.getenv("RETRO_KS_PASS") ?: ""
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("retro") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("retro")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.android.billingclient:billing:8.0.0")
}
