plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val gabiApiUrl = providers.gradleProperty("gabiApiUrl").orNull
    ?: System.getenv("GABI_API_URL")
    ?: "https://companheiro-fala-dqap90kbk-siocred1.vercel.app/api/gabi/chat"
val gabiApiToken = providers.gradleProperty("gabiApiToken").orNull
    ?: System.getenv("GABI_API_TOKEN")
    ?: ""
fun String.asBuildConfigString() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

providers.gradleProperty("companheirofalaBuildDir").orNull?.takeIf { it.isNotBlank() }?.let {
    layout.buildDirectory.set(file(it))
}

android {
    namespace = "br.com.companheirofala"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

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
        versionCode = 30
        versionName = "0.30.0"
        ndk { abiFilters += "arm64-v8a" }
        // Configure outside source control: -PgabiApiUrl=... -PgabiApiToken=...
        buildConfigField("String", "GABI_API_URL", gabiApiUrl.asBuildConfigString())
        buildConfigField("String", "GABI_API_TOKEN", gabiApiToken.asBuildConfigString())
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
    buildFeatures { buildConfig = true }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        externalNativeBuild {
            cmake {
                targets += listOf("companion_llama", "llama", "ggml", "ggml-base", "ggml-cpu")
            }
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
