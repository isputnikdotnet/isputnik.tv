plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "net.isputnik.tv.video"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":player"))
    implementation(libs.activity.compose)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
}
