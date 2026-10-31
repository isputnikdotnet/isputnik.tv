plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "net.isputnik.tv.core"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.compose.ui)
    api(libs.tv.material)
    api(libs.coil.compose)
    api(libs.okhttp)
    api(libs.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
