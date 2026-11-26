plugins {
    id("com.android.application")
}

android {
    namespace = "com.tavlaoyunu.klasik"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tavlaoyunu.klasik"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }

    // İmza anahtarı depoya KONMAZ (depo herkese açık). Derleme sırasında ortam değişkenlerinden okunur:
    // TAVLA_KS_PATH (anahtar dosyası) ve TAVLA_KS_PASS (parola). Anahtar alias'ı: tavla.
    signingConfigs {
        create("tavla") {
            storeFile = file(System.getenv("TAVLA_KS_PATH") ?: "tavla.jks")
            storePassword = System.getenv("TAVLA_KS_PASS") ?: ""
            keyAlias = "tavla"
            keyPassword = System.getenv("TAVLA_KS_PASS") ?: ""
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("tavla") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("tavla")
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
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.android.billingclient:billing:8.0.0")
}
