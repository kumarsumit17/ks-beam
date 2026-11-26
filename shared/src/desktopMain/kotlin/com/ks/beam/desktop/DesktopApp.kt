package com.ks.beam.desktop

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.window.FrameWindowScope
import com.ks.beam.ui.BeamState
import com.ks.beam.ui.handleKey
import com.ks.beam.ui.rememberBeamState
import kotlinx.coroutines.channels.Channel
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
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
import com.ks.beam.ui.CastLock
import com.ks.beam.ui.CastSession
import com.ks.beam.ui.SettingsStore
import java.awt.Desktop
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

private class DesktopPlatform(
    override val systemLanguages: List<String>,
    private val keepAwake: () -> AutoCloseable,
    override val explainFirewall: Boolean,
) : BeamPlatform {
    override val settings: SettingsStore = PrefsSettings()
    override val extraRenderers: List<String> = listOfNotNull(System.getenv("KS_BEAM_RENDERER")?.takeIf { it.isNotBlank() })
    var window: Frame? = null

    override suspend fun pickMedia(): MediaSource? {
        // Runs on the Swing thread (Compose's main dispatcher); the modal dialog keeps events flowing.
        val dialog = FileDialog(window, null as String?, FileDialog.LOAD)
        dialog.isMultipleMode = false
        dialog.isVisible = true
        val file = dialog.files.firstOrNull() ?: return null
        return FileMediaSource(file)
    }

    // While a cast runs this computer is the TV's file server: the OS must not sleep.
    override fun castLock(session: CastSession): CastLock {
        val awake = keepAwake()
        return object : CastLock {
            override fun close() = awake.close()
        }
    }

    @Composable
    override fun ProvideLanguage(language: String, content: @Composable () -> Unit) {
        // Compose resources follow the JVM's default locale on desktop; key() rebuilds the subtree
        // so every text is looked up again in the new language.
        remember(language) { Locale.setDefault(Locale.forLanguageTag(language)) }
        key(language) { content() }
    }
}

/** What each desktop OS adds to the shared window; set by the entry point in its platform folder. */
class DesktopOptions(
    /** The OS's preferred languages, most preferred first. */
    val systemLanguages: List<String>,
    /** Keeps the OS from sleeping until closed. */
    val keepAwake: () -> AutoCloseable = { AutoCloseable {} },
    /** Explain the firewall prompt when no TV is found (Windows). */
    val explainFirewall: Boolean = false,
    /** Room for a title bar drawn over the content (macOS). */
    val contentPadding: PaddingValues = PaddingValues(),
    /** Called once with the window: native styling. */
    val onWindow: (ComposeWindow) -> Unit = {},
    /** The OS's menu bar (macOS). */
    val menuBar: (@Composable FrameWindowScope.(BeamState) -> Unit)? = null,
    /** Shortcuts the OS adds to the shared keys (Windows: Ctrl+O). */
    val extraKeys: (KeyEvent, BeamState) -> Boolean = { _, _ -> false },
)

/**
 * Files to cast that arrive from outside the window: opened with KS Beam (Finder, Explorer), dropped
 * on the Dock icon, or handed over by a second launch (Windows). The window casts each in turn.
 */
object OpenedFiles {
    private val files = Channel<File?>(Channel.UNLIMITED)
    fun open(file: File) { files.trySend(file) }
    /** A second launch with no file: the window just comes to the front. */
    fun bringToFront() { files.trySend(null) }
    internal suspend fun next(): File? = files.receive()
    /** For the Windows module's single-instance test. */
    suspend fun nextForTest(): File? = next()
}

/** Starts the desktop app with what [options] adds for this OS. */
fun runDesktopApp(options: DesktopOptions) {
    val platform = DesktopPlatform(options.systemLanguages, options.keepAwake, options.explainFirewall)
    application {
        val state = rememberBeamState(platform)
        // Quitting (closing the window, or the app menu's Quit on macOS) tells the TV to stop first.
        LaunchedEffect(state) {
            runCatching {
                if (Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                    Desktop.getDesktop().setQuitHandler { _, response -> state.stopBeforeExit(); response.performQuit() }
                }
            }
        }
        Window(
            onCloseRequest = { state.stopBeforeExit(); exitApplication() },
            title = "KS Beam",
            state = rememberWindowState(size = DpSize(560.dp, 680.dp)),
            onPreviewKeyEvent = { state.handleKey(it) || options.extraKeys(it, state) },
        ) {
            platform.window = window
            LaunchedEffect(window) {
                options.onWindow(window)
                acceptDroppedFiles(window)
                smokeTest(options)
            }
            LaunchedEffect(state) {
                while (true) {
                    val file = OpenedFiles.next()
                    window.isMinimized = false
                    window.toFront()
                    if (file != null) state.castMedia(FileMediaSource(file))
                }
            }
            options.menuBar?.let { menu -> platform.ProvideLanguage(state.language) { menu(state) } }
            BeamApp(platform, state, options.contentPadding)
        }
    }
}

/** A file dropped anywhere on the window is cast like one chosen with the picker. */
private fun acceptDroppedFiles(window: ComposeWindow) {
    val target = object : DropTargetAdapter() {
        override fun dragEnter(e: DropTargetDragEvent) {
            if (e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) e.acceptDrag(DnDConstants.ACTION_COPY) else e.rejectDrag()
        }

        override fun drop(e: DropTargetDropEvent) {
            if (!e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return e.rejectDrop()
            e.acceptDrop(DnDConstants.ACTION_COPY)
            @Suppress("UNCHECKED_CAST")
            val files = runCatching { e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File> }.getOrDefault(emptyList())
            files.firstOrNull { it.isFile }?.let(OpenedFiles::open)
            e.dropComplete(files.isNotEmpty())
        }
    }
    // Compose draws into a child component; AWT gives a drop to the nearest component with a target.
    DropTarget(window, DnDConstants.ACTION_COPY, target, true)
    DropTarget(window.contentPane, DnDConstants.ACTION_COPY, target, true)
    window.contentPane.components.forEach { DropTarget(it, DnDConstants.ACTION_COPY, target, true) }
}

/**
 * CI's start-up check of a packaged build (`KS_BEAM_SMOKE_TEST=<file>`): once the window is up, the
 * keep-awake is taken and released (the native calls through JNA), "ok" is written to the file and
 * the app quits. Never set in normal use.
 */
private fun smokeTest(options: DesktopOptions) {
    val path = System.getenv("KS_BEAM_SMOKE_TEST")?.takeIf { it.isNotBlank() } ?: return
    val result = runCatching { options.keepAwake().close() }.fold({ "ok" }, { "keep-awake failed: $it" })
    File(path).writeText(result)
    kotlin.system.exitProcess(if (result == "ok") 0 else 1)
}
