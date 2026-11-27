package com.ks.beam.mac

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import com.ks.beam.desktop.DesktopOptions
import com.ks.beam.desktop.OpenedFiles
import com.ks.beam.desktop.runDesktopApp
import com.ks.beam.resources.Res
import com.ks.beam.resources.cast_seek_back
import com.ks.beam.resources.cast_seek_forward
import com.ks.beam.resources.cast_stop
import com.ks.beam.resources.menu_file
import com.ks.beam.resources.menu_github
import com.ks.beam.resources.menu_help
import com.ks.beam.resources.menu_minimize
import com.ks.beam.resources.menu_open
import com.ks.beam.resources.menu_play_pause
import com.ks.beam.resources.menu_playback
import com.ks.beam.resources.menu_window
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * macOS entry point: the system menu bar, a title bar drawn over the content, Finder's "Open With"
 * and drops on the Dock icon, keep-awake while casting, Now Playing and the media keys, and the
 * languages from System Settings.
 */
fun main() {
    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("apple.awt.application.name", "KS Beam")
    // Set before the window opens, so a file that launched the app is not missed.
    runCatching {
        if (Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
            Desktop.getDesktop().setOpenFileHandler { e -> e.files.firstOrNull()?.let(OpenedFiles::open) }
        }
    }
    runDesktopApp(
        DesktopOptions(
            systemLanguages = preferredLanguages(),
            keepAwake = { keepAwake("KS Beam is casting to a TV") },
            mediaSession = NowPlaying::lock,
            // The traffic lights sit over the content; keep it below them.
            contentPadding = PaddingValues(top = 28.dp),
            onWindow = { window ->
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            },
            menuBar = { state ->
                MenuBar {
                    Menu(stringResource(Res.string.menu_file)) {
                        Item(stringResource(Res.string.menu_open), shortcut = KeyShortcut(Key.O, meta = true), onClick = state::chooseFileAndCast)
                    }
                    // Space and the arrows are the window's own keys (shared with the TV remote), so
                    // these items show them without taking them as menu shortcuts.
                    Menu(stringResource(Res.string.menu_playback)) {
                        val casting = state.cast != null
                        Item(stringResource(Res.string.menu_play_pause) + "    Space", enabled = casting, onClick = state::togglePlay)
                        Item(stringResource(Res.string.cast_seek_back) + "    ←", enabled = casting, onClick = { state.seekBy(-10) })
                        Item(stringResource(Res.string.cast_seek_forward) + "    →", enabled = casting, onClick = { state.seekBy(10) })
                        Separator()
                        Item(stringResource(Res.string.cast_stop), shortcut = KeyShortcut(Key.Period, meta = true), enabled = casting, onClick = state::stop)
                    }
                    Menu(stringResource(Res.string.menu_window)) {
                        Item(stringResource(Res.string.menu_minimize), shortcut = KeyShortcut(Key.M, meta = true), onClick = { window.isMinimized = true })
                    }
                    Menu(stringResource(Res.string.menu_help)) {
                        Item(stringResource(Res.string.menu_github), onClick = {
                            runCatching { Desktop.getDesktop().browse(URI("https://github.com/kumasumit/ks-beam")) }
                        })
                    }
                }
            },
        ),
    )
}

/** The user's preferred languages (AppleLanguages), most preferred first. */
private fun preferredLanguages(): List<String> {
    val fromSystem = runCatching {
        val process = ProcessBuilder("defaults", "read", "-g", "AppleLanguages").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor(2, TimeUnit.SECONDS)
        Regex("\"?([A-Za-z]{2,3}(?:-[A-Za-z0-9]+)*)\"?").findAll(text.substringAfter('(').substringBefore(')'))
            .map { it.groupValues[1] }.toList()
    }.getOrDefault(emptyList())
    return fromSystem.ifEmpty { listOf(Locale.getDefault().toLanguageTag()) }
}
