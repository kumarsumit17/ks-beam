package com.ks.beam.mac

import com.ks.beam.desktop.runDesktopApp
import java.util.Locale
import java.util.concurrent.TimeUnit

/** macOS entry point: the system menu bar and the languages from System Settings. */
fun main() {
    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("apple.awt.application.name", "KS Beam")
    runDesktopApp(preferredLanguages())
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
