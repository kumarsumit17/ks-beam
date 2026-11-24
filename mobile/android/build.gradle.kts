// The phone app: the sender. The casting core and the interface are in :shared; this module adds
// the activity (file picker, multicast lock) and the phone launcher entry. Package name, version
// and signing: the root build.gradle.kts, shared with the Google TV app (tv/google-tv), which is a
// separate APK.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ks.beam.android"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
}
