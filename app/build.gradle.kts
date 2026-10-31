plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "net.isputnik.tv"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "net.isputnik.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        // Until T4 brings a real key. A debug build of Compose is too slow to judge on a stick.
        release { signingConfig = signingConfigs.getByName("debug") }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":feature-video"))
    implementation(libs.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.zxing)
}
