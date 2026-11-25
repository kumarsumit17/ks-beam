import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Windows entry point and packaging (.msi). Builds an msi only on Windows (CI).
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
}

// jpackage needs MAJOR >= 1; untagged development builds package as 1.0.0.
val versionName = (rootProject.extra["appVersionName"] as String).substringBefore('-')
val packageVersion = versionName.takeIf { (it.substringBefore('.').toIntOrNull() ?: 0) >= 1 } ?: "1.0.0"

compose.desktop {
    application {
        mainClass = "com.ks.beam.windows.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "KS Beam"
            this.packageVersion = packageVersion
            description = "KS Beam: cast to TVs and projectors"
            vendor = "KS-Suite"
            modules("java.prefs", "java.desktop", "java.xml", "jdk.unsupported")
            windows {
                menu = true
                menuGroup = "KS-Suite"
                shortcut = true
                dirChooser = true
                // Fixed for the life of the app, so new versions upgrade the installed one.
                upgradeUuid = "98224424-ed1e-4649-bd85-390c159d53c9"
            }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}
