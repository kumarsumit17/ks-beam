package com.ks.beam.windows

import com.ks.beam.desktop.runDesktopApp
import java.util.Locale

/** Windows entry point. The JVM's default locale follows the Windows display language. */
fun main() {
    runDesktopApp(listOf(Locale.getDefault().toLanguageTag())) { keepAwake() }
}
