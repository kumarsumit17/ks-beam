package com.ks.beam.windows

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.ks.beam.desktop.DesktopOptions
import com.ks.beam.desktop.OpenedFiles
import com.ks.beam.desktop.runDesktopApp
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Windows entry point. One running copy: a second launch (from Start, or with a file) hands over to
 * it and exits. A dark title bar, Ctrl+O, keep-awake while casting, and the firewall explanation
 * (no firewall rule is added at install: the owner's choice). The JVM's default locale follows the
 * Windows display language.
 */
fun main(args: Array<String>) {
    val files = args.map(::File).filter { it.isFile }
    if (SingleInstance.handOver(files)) exitProcess(0)
    SingleInstance.listen()
    files.firstOrNull()?.let(OpenedFiles::open)
    runDesktopApp(
        DesktopOptions(
            systemLanguages = listOf(Locale.getDefault().toLanguageTag()),
            keepAwake = { keepAwake() },
            explainFirewall = true,
            onWindow = ::useDarkTitleBar,
            extraKeys = { event, state ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.O) {
                    state.chooseFileAndCast(); true
                } else {
                    false
                }
            },
        ),
    )
}
