package com.ks.beam.update

import com.ks.beam.ui.SettingsStore
import com.ks.utility.appupdate.UpdatePlatform
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** KS Beam's updater: each app asks for its own file, and failures get their own status. */
class GitHubUpdaterTest {
    private class Settings : SettingsStore {
        val values = mutableMapOf<String, String>()
        override fun getString(key: String) = values[key]
        override fun putString(key: String, value: String) { values[key] = value }
    }

    private class Installer(private val allowed: Boolean = true) : UpdateInstaller {
        override fun downloadDir() = File(System.getProperty("java.io.tmpdir"), "ks-beam-update-test")
        override fun canInstall() = allowed
        override fun open(file: File) = OpenedOn.Android
    }

    private val digest = "sha256:" + "a".repeat(64)
    private fun asset(name: String, size: Long) =
        """{"name":"$name","size":$size,"digest":"$digest","browser_download_url":"https://github.com/kumasumit/ks-beam/releases/download/v1.8.0/$name"}"""
    private val releases = """[{"tag_name":"v1.8.0","body":"","draft":false,"assets":[
        ${asset("ks-beam-v1.8.0.aab", 1)},${asset("ks-beam-v1.8.0-google-tv.aab", 2)},${asset("ks-beam-v1.8.0.apk", 100)},${asset("ks-beam-v1.8.0-google-tv.apk", 200)},
        ${asset("ks-beam-v1.8.0-macos-arm64.dmg", 300)},${asset("ks-beam-v1.8.0-windows-x64.msi", 400)}]}]"""

    private fun updater(version: String, platform: UpdatePlatform, body: () -> String, installer: Installer = Installer(), settings: Settings = Settings()) =
        GitHubUpdater(version, platform, settings, installer, fetch = { body() })

    @Test
    fun eachAppGetsItsOwnFile() {
        val sizes = mapOf(UpdatePlatform.ANDROID to 100L, UpdatePlatform.GOOGLE_TV to 200L, UpdatePlatform.MACOS_ARM64 to 300L, UpdatePlatform.WINDOWS_X64 to 400L)
        for ((platform, size) in sizes) {
            val u = updater("1.7.0", platform, { releases })
            u.check(silent = false)
            assertEquals(UpdateStatus.Available("1.8.0", size), u.status.value, platform.name)
        }
    }

    @Test
    fun upToDateAndFailures() {
        val settings = Settings()
        val current = updater("1.8.0", UpdatePlatform.ANDROID, { releases }, settings = settings)
        current.check(silent = false)
        assertEquals(UpdateStatus.UpToDate("1.8.0"), current.status.value)
        assertTrue(settings.values.containsKey("update.lastCheck"))

        val offline = updater("1.7.0", UpdatePlatform.ANDROID, { throw IOException("offline") })
        offline.check(silent = false)
        assertEquals(UpdateStatus.Failed(UpdateFailure.Check, "offline"), offline.status.value)
        // The automatic check stays quiet when it fails.
        val quiet = updater("1.7.0", UpdatePlatform.ANDROID, { throw IOException("offline") })
        quiet.check(silent = true)
        assertEquals(UpdateStatus.Idle, quiet.status.value)

        val garbage = updater("1.7.0", UpdatePlatform.ANDROID, { "not json" })
        garbage.check(silent = false)
        assertEquals(UpdateStatus.Failed(UpdateFailure.Unreadable), garbage.status.value)
    }

    @Test
    fun androidAsksForTheInstallPermissionFirst() {
        val u = updater("1.7.0", UpdatePlatform.ANDROID, { releases }, Installer(allowed = false))
        u.check(silent = true)
        u.install()
        assertEquals(UpdateStatus.NeedsPermission(100), u.status.value)
    }

    @Test
    fun laterAndAutoAreRemembered() {
        val settings = Settings()
        val u = updater("1.7.0", UpdatePlatform.ANDROID, { releases }, settings = settings)
        u.check(silent = true)
        assertIs<UpdateStatus.Available>(u.status.value)
        u.later()
        u.setAutoCheck(false)
        val again = updater("1.7.0", UpdatePlatform.ANDROID, { releases }, settings = settings)
        assertEquals("1.8.0", again.laterVersion.value)
        assertEquals(false, again.autoCheck.value)
        assertTrue(updater("0.0.0", UpdatePlatform.ANDROID, { releases }).isDevBuild)
    }

    @Test
    fun sizes() {
        assertEquals("512 B", formatSize(512))
        assertEquals("1.5 KB", formatSize(1536))
        assertEquals("48 MB", formatSize(48L * 1024 * 1024))
    }
}
