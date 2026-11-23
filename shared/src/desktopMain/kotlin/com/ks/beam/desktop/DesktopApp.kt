package com.ks.beam.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ks.beam.core.MediaSource
import com.ks.beam.core.Mime
import com.ks.beam.ui.BeamApp
import com.ks.beam.ui.BeamPlatform
import com.ks.beam.ui.SettingsStore
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import java.util.prefs.Preferences

/** A file on disk, read in place. */
class FileMediaSource(private val file: File) : MediaSource {
    override val name: String = file.name
    override val mimeType: String = Mime.forName(file.name) ?: "application/octet-stream"
    override val length: Long get() = file.length()
    override fun openAt(offset: Long): InputStream =
        FileInputStream(file).apply { if (offset > 0) channel.position(offset) }
}

private class PrefsSettings : SettingsStore {
    private val prefs = Preferences.userRoot().node("com/ks/beam")
    override fun getString(key: String): String? = prefs.get(key, null)
    override fun putString(key: String, value: String) {
        prefs.put(key, value)
        prefs.flush()
    }
}

private class DesktopPlatform(override val systemLanguages: List<String>) : BeamPlatform {
    override val settings: SettingsStore = PrefsSettings()
    var window: Frame? = null

    override suspend fun pickMedia(): MediaSource? {
        // Runs on the Swing thread (Compose's main dispatcher); the modal dialog keeps events flowing.
        val dialog = FileDialog(window, null as String?, FileDialog.LOAD)
        dialog.isMultipleMode = false
        dialog.isVisible = true
        val file = dialog.files.firstOrNull() ?: return null
        return FileMediaSource(file)
    }

    @Composable
    override fun ProvideLanguage(language: String, content: @Composable () -> Unit) {
        // Compose resources follow the JVM's default locale on desktop; key() rebuilds the subtree
        // so every text is looked up again in the new language.
        remember(language) { Locale.setDefault(Locale.forLanguageTag(language)) }
        key(language) { content() }
    }
}

/** Starts the desktop app. [systemLanguages]: the OS's preferred languages, most preferred first. */
fun runDesktopApp(systemLanguages: List<String>) {
    val platform = DesktopPlatform(systemLanguages)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "KS Beam",
            state = rememberWindowState(size = DpSize(560.dp, 680.dp)),
        ) {
            platform.window = window
            BeamApp(platform)
        }
    }
}
