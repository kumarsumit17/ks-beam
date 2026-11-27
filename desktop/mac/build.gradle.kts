import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// macOS entry point and packaging (.dmg). Builds a dmg only on a Mac.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.jna)
    implementation(libs.compose.resources) // the menu bar's text, from the shared language files
    testImplementation(kotlin("test"))
}

// jpackage needs MAJOR >= 1; untagged development builds package as 1.0.0.
val versionName = (rootProject.extra["appVersionName"] as String).substringBefore('-')
val installerVersion = versionName.takeIf { (it.substringBefore('.').toIntOrNull() ?: 0) >= 1 } ?: "1.0.0"

compose.desktop {
    application {
        mainClass = "com.ks.beam.mac.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "KS Beam"
            packageVersion = installerVersion // not "packageVersion = packageVersion": that names this block's own, unset property
            description = "KS Beam: cast to TVs and projectors"
            vendor = "KS-Suite"
            modules("java.prefs", "java.desktop", "java.xml", "jdk.unsupported")
            macOS {
                bundleID = "com.ks.beam"
                appCategory = "public.app-category.utilities"
                // macOS 15+ asks before an app reaches the local network; this is the reason shown.
                // The document types put KS Beam in Finder's "Open With" for the formats it casts, as
                // an alternative only (LSHandlerRank Alternate): it never becomes the default app.
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>KS Beam finds TVs and projectors on your network and sends them the file you choose.</string>
                        <key>CFBundleDocumentTypes</key>
                        <array>
                        <dict>
                            <key>CFBundleTypeName</key><string>Video</string>
                            <key>CFBundleTypeRole</key><string>Viewer</string>
                            <key>LSHandlerRank</key><string>Alternate</string>
                            <key>CFBundleTypeExtensions</key><array><string>mp4</string><string>m4v</string><string>mkv</string><string>webm</string><string>mov</string><string>avi</string><string>ts</string></array>
                        </dict>
                        <dict>
                            <key>CFBundleTypeName</key><string>Audio</string>
                            <key>CFBundleTypeRole</key><string>Viewer</string>
                            <key>LSHandlerRank</key><string>Alternate</string>
                            <key>CFBundleTypeExtensions</key><array><string>mp3</string><string>m4a</string><string>aac</string><string>flac</string><string>wav</string><string>ogg</string></array>
                        </dict>
                        <dict>
                            <key>CFBundleTypeName</key><string>Photo</string>
                            <key>CFBundleTypeRole</key><string>Viewer</string>
                            <key>LSHandlerRank</key><string>Alternate</string>
                            <key>CFBundleTypeExtensions</key><array><string>jpg</string><string>jpeg</string><string>png</string><string>gif</string><string>webp</string></array>
                        </dict>
                        </array>
                    """.trimIndent()
                }
            }
        }
        // ProGuard removes the unused parts of Compose, Kotlin and the other libraries (shrink only).
        buildTypes.release.proguard {
            isEnabled.set(true)
            configurationFiles.from(rootProject.file("desktop-rules.pro"))
        }
    }
}
