import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Code used by two or more platforms. Every target is a JVM (desktop and Android), so the casting
// core and the interface live in jvmSharedMain; commonMain holds only the language files.
// desktopMain: macOS and Windows (window, file dialog, settings). androidMain: Android phones.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(17)

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    android {
        namespace = "com.ks.beam.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        androidResources { enable = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            // ks-utility's update decisions (versions, the right file, checksums): KS Beam's updater.
            dependencies { implementation(libs.ks.utility) }
        }
        val desktopMain by getting {
            dependsOn(jvmSharedMain)
            dependencies { implementation(libs.kotlinx.coroutines.swing) }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.core) // FileProvider: the downloaded update for the system installer
        }
    }
}

// ---- Interface text: ks-utility language files → Compose resources ------------------------------
// The app's en.json / hi.json and ks-utility's shared catalogue are converted to Android-style
// strings.xml, which Compose Multiplatform resources read on every platform. Needs Node 23.6+.
val langTool = rootProject.file("ks-utility/typescript/tools/lang.mjs")
val langDir = layout.projectDirectory.dir("src/commonMain/lang")
val sharedLangDir = rootProject.layout.projectDirectory.dir("ks-utility/lang/common")
val langResources = layout.buildDirectory.dir("generated/lang/composeResources")

val languageResources by tasks.registering(Exec::class) {
    description = "Converts the language files into Compose string resources."
    inputs.dir(langDir)
    inputs.dir(sharedLangDir)
    outputs.dir(langResources)
    val out = langResources.get().asFile
    // `compose`: escaped for Compose resources, which show Android's \' literally ("didn\'t").
    commandLine("node", langTool.path, "compose", langDir.asFile.path, "--out", out.path)
}

val checkLanguageFiles by tasks.registering(Exec::class) {
    description = "Checks the language files with the KS-Suite language-file check."
    inputs.dir(langDir)
    inputs.dir(sharedLangDir)
    commandLine("node", langTool.path, "check", langDir.asFile.path)
}

tasks.named("check") { dependsOn(checkLanguageFiles) }

compose.resources {
    publicResClass = true
    packageOfResClass = "com.ks.beam.resources"
    generateResClass = always
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = languageResources.map { langResources.get() },
    )
}

// The emulator cast test runs only when these point at a running receiver and a media file.
tasks.withType<Test>().configureEach {
    listOf("KS_BEAM_RENDERER", "KS_BEAM_MEDIA", "KS_BEAM_MEDIA_HOST", "KS_BEAM_HOLD_MS").forEach { name ->
        providers.environmentVariable(name).orNull?.let { environment(name, it) }
    }
}
