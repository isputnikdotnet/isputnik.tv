plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "net.isputnik.tv.photos"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.okhttp)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
}
