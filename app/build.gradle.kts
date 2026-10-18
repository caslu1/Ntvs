plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.novatv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.novatv.app"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // ===== PERSONALIZAÇÃO =====
        // Servidor Xtream pré-preenchido na tela de login (deixe "" para o cliente digitar)
        buildConfigField("String", "DEFAULT_SERVER", "\"\"")
        // URL de um JSON com banners da tela inicial, ex: ["https://.../jogos1.jpg","https://.../jogos2.jpg"]
        buildConfigField("String", "BANNERS_URL", "\"\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("novatv.keystore")
            storePassword = "novatv123"
            keyAlias = "novatv"
            keyPassword = "novatv123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("androidx.annotation:annotation-experimental:1.4.1")
}
