import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// One place for the version: a release's tag has to match it (.github/workflows/release.yml).
val tvVersion = providers.gradleProperty("tvVersion").get()
val tvVersionCode = tvVersion.split(".").map { it.toInt() }.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

// The real key lives outside the repo: CI has it in secrets, a person can name it in local.properties.
val localProperties = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun signing(name: String): String? = System.getenv("TV_SIGNING_${name.uppercase()}") ?: localProperties.getProperty("signing.$name")

android {
    namespace = "net.isputnik.tv"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "net.isputnik.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = tvVersionCode
        versionName = tvVersion
        buildConfigField("String", "RELEASES_URL", "\"${providers.gradleProperty("releasesUrl").get()}\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    signingConfigs {
        signing("store")?.let { store ->
            create("release") {
                storeFile = file(store)
                storePassword = signing("password")
                keyAlias = "isputnik-tv"
                keyPassword = signing("password")
            }
        }
    }
    buildTypes {
        // The debug key is for trying a build on a TV: a debug build of Compose is too slow to
        // judge on a stick. An APK signed with it can never update one signed with the real key,
        // so a release refuses to be built without that (-PrequireKey).
        release {
            val key = signingConfigs.findByName("release")
            check(key != null || !providers.gradleProperty("requireKey").isPresent) { "No signing key" }
            signingConfig = key ?: signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":feature-video"))
    implementation(libs.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.zxing)
}
