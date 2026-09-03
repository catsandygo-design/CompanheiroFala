plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.companheirofala"
    compileSdk = 36

    signingConfigs {
        create("prototypeRelease") {
            val storePath = System.getenv("SIGNING_STORE_FILE")
            if (!storePath.isNullOrBlank()) storeFile = file(storePath)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "companheiro123"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "companheiro"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "companheiro123"
        }
    }

    defaultConfig {
        applicationId = "br.com.companheirofala"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.10.0"
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("prototypeRelease")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
