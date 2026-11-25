package com.ks.beam.ui

import androidx.compose.runtime.Composable
import com.ks.beam.core.MediaSource
import com.ks.beam.core.Position

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

    /**
     * Device description addresses to fetch on every search besides SSDP, for testing where
     * multicast cannot reach (emulators): KS_BEAM_RENDERER on desktop, an extra on Android debug builds.
     */
    val extraRenderers: List<String> get() = emptyList()

    /**
     * True on Windows: when no TV is found, the interface explains Windows Defender Firewall's
     * prompt (the owner's choice: no firewall rule is added at install).
     */
    val explainFirewall: Boolean get() = false

    /** Held while searching (Android: a Wi-Fi multicast lock). */
    fun searchLock(): AutoCloseable = AutoCloseable {}

    /**
     * Taken when a cast starts and closed when it ends. While a cast runs this device is the TV's
     * file server, so the platform keeps it reachable: the computer awake, on Android a foreground
     * service and a Wi-Fi lock. It can also show [session] in the OS's media controls.
     */
    fun castLock(session: CastSession): CastLock = CastLock.None

    /** Applies [language] to the interface below it (platform-specific resource lookup). */
    @Composable
    fun ProvideLanguage(language: String, content: @Composable () -> Unit)
}

/** A running cast, as the OS's media controls see it. Read and called on the main thread. */
interface CastSession {
    val rendererName: String
    val title: String
    val playing: Boolean
    val position: Position
    /** The control labels in the interface's language. */
    val labels: CastLabels
    fun togglePlay()
    fun seekBy(delta: Long)
    fun seekTo(seconds: Long)
    fun stop()
}

/** Control labels for OS media controls, in the language the interface shows. */
data class CastLabels(
    val castingTo: String = "",
    val play: String = "",
    val pause: String = "",
    val back: String = "",
    val forward: String = "",
    val stop: String = "",
    val channel: String = "",
)

/** What [BeamPlatform.castLock] returns: told when the session changes, closed when the cast ends. */
interface CastLock : AutoCloseable {
    /** The session's playing state, position or labels changed. */
    fun changed() {}

    object None : CastLock {
        override fun close() {}
    }
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
