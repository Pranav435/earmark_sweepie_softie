// Runs ML Kit Smart Reply on a phone over benchmark/data/messages.json, for `:core:benchmark` (benchmark/README.md).
// A debug-only app of its own, so it installs beside Earmark without touching it.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.sweepiesoftie.earmark.bench"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sweepiesoftie.earmark.bench"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }

    sourceSets["main"].assets.srcDir(rootProject.file("benchmark/data"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.mlkit.smart.reply)
}
