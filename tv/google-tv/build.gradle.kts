// The Google TV app: the KS Beam receiver (Media3 playback, the ready screen), a separate APK and
// AAB (<app>-v<version>-google-tv.apk) with the Leanback launcher entry and the banner. The
// receiver protocol, settings and languages are in :shared. Package name, version (plus the TV
// offset) and signing: the root build.gradle.kts.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ks.beam.tv"
}

dependencies {
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.resources)
    implementation(libs.kotlinx.coroutines.android)
}
