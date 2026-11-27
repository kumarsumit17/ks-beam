package com.ks.beam.desktop

import com.ks.beam.ui.SettingsStore
import com.ks.beam.update.GitHubUpdater
import com.ks.beam.update.OpenedOn
import com.ks.beam.update.UpdateInstaller
import com.ks.utility.appupdate.UpdatePlatform
import java.io.File

/** The running version: the packaged app and `run` pass it as -Dks.beam.version (from the git tag). */
internal fun desktopAppVersion(): String = System.getProperty("ks.beam.version")?.takeIf { it.isNotBlank() } ?: "0.0.0"

/**
 * Where updates are downloaded, and ks-utility's desktop installer
 * (`com.ks.utility.appupdate.DesktopInstaller`): Windows starts the .msi and quits; macOS opens the
 * .dmg, shows that KS Beam now closes, and quits 6 s later so Finder can replace it. A running cast
 * is stopped on the TV first ([beforeQuit]). Nothing installs silently.
 */
internal class BeamDesktopInstaller(beforeQuit: () -> Unit) : UpdateInstaller {
    private val windows = System.getProperty("os.name").startsWith("Windows")
    private val installer = com.ks.utility.appupdate.DesktopInstaller("KS Beam", beforeQuit)

    override fun downloadDir(): File {
        val base = if (windows) {
            File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "KS Beam")
        } else {
            File(System.getProperty("user.home"), "Library/Caches/com.ks.beam")
        }
        return File(base, "update")
    }

    override fun open(file: File): OpenedOn = when (installer.open(file)) {
        com.ks.utility.appupdate.DesktopInstaller.Opened.MAC -> OpenedOn.Mac
        com.ks.utility.appupdate.DesktopInstaller.Opened.WINDOWS -> OpenedOn.Windows
    }
}

/** The Mac or Windows updater (the .dmg for Apple silicon, the .msi for x64); null elsewhere. */
internal fun desktopUpdater(settings: SettingsStore, beforeQuit: () -> Unit): GitHubUpdater? {
    val platform = UpdatePlatform.current() ?: return null
    return GitHubUpdater(desktopAppVersion(), platform, settings, BeamDesktopInstaller(beforeQuit)).also { it.start() }
}
