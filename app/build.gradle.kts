plugins {
    id("com.android.application")
}

android {
    namespace = "com.retrooyun.baslatici"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.retrooyun.baslatici"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.4.4"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Oyun motorlari (.so) uygulama klasorune acilip dlopen ile yuklendigi icin eski paketleme sart.
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    // NOT: Bu depodaki ci/test-key.p12 sadece DENEME anahtaridir. Play Store icin kendi anahtarini kullan.
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
    // WAD dosyalari zaten sikisik degil ama oyun verisi oldugu icin aapt'in dokunmamasi daha guvenli
    androidResources {
        noCompress += listOf("wad")
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
