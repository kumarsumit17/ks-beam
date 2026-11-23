package com.ks.beam.ui

import androidx.compose.runtime.Composable
import com.ks.beam.core.MediaSource

/** Saved settings: java.util.prefs on desktop, SharedPreferences on Android. */
interface SettingsStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** What each sender platform provides to the shared interface. */
interface BeamPlatform {
    val settings: SettingsStore

    /** The system's preferred languages, most preferred first, as BCP 47 tags. */
    val systemLanguages: List<String>

    /** Shows the system file picker; null when the user cancels. */
    suspend fun pickMedia(): MediaSource?

    /** Held while searching (Android: a Wi-Fi multicast lock). */
    fun searchLock(): AutoCloseable = AutoCloseable {}

    /** Applies [language] to the interface below it (platform-specific resource lookup). */
    @Composable
    fun ProvideLanguage(language: String, content: @Composable () -> Unit)
}

/** The user's language choice. The suite rule: follow the system, allow a choice, remember it. */
enum class LanguageChoice(val saved: String) {
    System("system"),
    English("en"),
    Hindi("hi");

    companion object {
        /** Anything unknown in the saved setting counts as "Same as the system". */
        fun parse(saved: String?): LanguageChoice = entries.firstOrNull { it.saved == saved } ?: System
    }
}

val supportedLanguages = listOf("en", "hi")

/** The user's choice, else the first supported system language, else English. */
fun resolveLanguage(choice: LanguageChoice, preferred: List<String>): String = when (choice) {
    LanguageChoice.English -> "en"
    LanguageChoice.Hindi -> "hi"
    LanguageChoice.System -> preferred
        .map { it.substringBefore('-').substringBefore('_').lowercase() }
        .firstOrNull { it in supportedLanguages }
        ?: "en"
}

private const val LANGUAGE_KEY = "language"
fun SettingsStore.languageChoice(): LanguageChoice = LanguageChoice.parse(getString(LANGUAGE_KEY))
fun SettingsStore.setLanguageChoice(choice: LanguageChoice) = putString(LANGUAGE_KEY, choice.saved)
