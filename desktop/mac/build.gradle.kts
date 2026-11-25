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
    testImplementation(kotlin("test"))
}

// jpackage needs MAJOR >= 1; untagged development builds package as 1.0.0.
val versionName = (rootProject.extra["appVersionName"] as String).substringBefore('-')
val packageVersion = versionName.takeIf { (it.substringBefore('.').toIntOrNull() ?: 0) >= 1 } ?: "1.0.0"

compose.desktop {
    application {
        mainClass = "com.ks.beam.mac.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "KS Beam"
            this.packageVersion = packageVersion
            description = "KS Beam: cast to TVs and projectors"
            vendor = "KS-Suite"
            modules("java.prefs", "java.desktop", "java.xml", "jdk.unsupported")
            macOS {
                bundleID = "com.ks.beam"
                appCategory = "public.app-category.utilities"
                // macOS 15+ asks before an app reaches the local network; this is the reason shown.
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>KS Beam finds TVs and projectors on your network and sends them the file you choose.</string>
                    """.trimIndent()
                }
            }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}
