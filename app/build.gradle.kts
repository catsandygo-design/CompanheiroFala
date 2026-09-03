import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val restoreFairyImage = tasks.register("restoreFairyImage") {
    doLast {
        val drawableDir = file("src/main/res/drawable")
        val payload = File(drawableDir, "fairy_companion_base64.txt")
        val output = File(drawableDir, "fairy_companion.jpg")
        if (payload.exists()) {
            output.writeBytes(Base64.getMimeDecoder().decode(payload.readText().trim()))
        }
    }
}

android {
    namespace = "br.com.companheirofala"
    compileSdk = 36

    signingConfigs {
        create("prototypeRelease") {
            val storePath = System.getenv("SIGNING_STORE_FILE")
            if (!storePath.isNullOrBlank()) storeFile = file(storePath)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "companheiro"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
        }
    }

    defaultConfig {
        applicationId = "br.com.companheirofala"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.11.0"
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

tasks.named("preBuild") {
    dependsOn(restoreFairyImage)
}
