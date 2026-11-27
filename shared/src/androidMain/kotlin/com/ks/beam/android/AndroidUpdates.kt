package com.ks.beam.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ks.beam.ui.SettingsStore
import com.ks.beam.update.GitHubUpdater
import com.ks.beam.update.OpenedOn
import com.ks.beam.update.UpdateInstaller
import com.ks.utility.appupdate.UpdatePlatform
import java.io.File

/** Hands the verified APK to the system installer through a FileProvider address. */
class AndroidInstaller(private val activity: Activity) : UpdateInstaller {
    override fun downloadDir() = File(activity.cacheDir, "update")

    override fun canInstall(): Boolean = activity.packageManager.canRequestPackageInstalls()

    override fun open(file: File): OpenedOn {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
        return OpenedOn.Android
    }

    override fun openPermissionSettings() {
        // Some TVs have no per-app "install unknown apps" screen; the app's own page then.
        runCatching {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
        }.onFailure {
            runCatching { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))) }
        }
    }
}

/**
 * The updater for one of KS Beam's two Android apps: the phone app asks for
 * [UpdatePlatform.ANDROID], the TV app ([tv]) for [UpdatePlatform.GOOGLE_TV], so neither installs the other.
 */
fun androidUpdater(activity: Activity, settings: SettingsStore, tv: Boolean): GitHubUpdater {
    val platform = if (tv) UpdatePlatform.GOOGLE_TV else UpdatePlatform.ANDROID
    val version = runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull() ?: "0.0.0"
    return GitHubUpdater(version, platform, settings, AndroidInstaller(activity)).also { it.start() }
}
