plugins {
    id("com.android.library")
}

android {
    namespace = "net.isputnik.tv.player"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 26 }
}

dependencies {
    api(libs.media3.exoplayer)
    api(libs.media3.ui)
    implementation(libs.media3.hls)
    implementation(libs.media3.okhttp)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
}
