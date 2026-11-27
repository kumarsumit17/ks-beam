package com.ks.beam.update

import kotlinx.coroutines.flow.StateFlow

/** Where the downloaded update was handed to the system; each has its own sentence. */
enum class OpenedOn { Mac, Windows, Android }

/** What can go wrong, each with its own sentence (`common.appUpdate.*`). */
enum class UpdateFailure { Check, Unreadable, Download, Damaged, Open }

/** The state of the "App updates" section. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val version: String) : UpdateStatus
    data class Available(val version: String, val sizeBytes: Long) : UpdateStatus
    data class NoInstaller(val version: String) : UpdateStatus
    data class Downloading(val version: String) : UpdateStatus
    data class Opened(val on: OpenedOn) : UpdateStatus

    /** Android: the user must allow KS Beam to install unknown apps first. */
    data class NeedsPermission(val sizeBytes: Long) : UpdateStatus
    data class Failed(val failure: UpdateFailure, val detail: String = "") : UpdateStatus
}

/**
 * KS Beam's updates from its GitHub releases (ks-utility's `specs/app-update.md`), shown by the
 * "App updates" section on every platform. The one internet request KS Beam makes
 * (specs/constraints.md).
 */
interface UpdateService {
    /** The running version, from the git tag; `0.0.0` for a build from source. */
    val currentVersion: String
    val isDevBuild: Boolean
    val status: StateFlow<UpdateStatus>
    val autoCheck: StateFlow<Boolean>

    fun setAutoCheck(enabled: Boolean)
    fun checkNow()

    /** Downloads the update found, verifies its SHA-256 and hands it to the system installer. */
    fun install()

    /** Hides the notice for the version found ("Later"); the section still shows it. */
    fun later()
    val laterVersion: StateFlow<String?>

    /** Android: opens "install unknown apps" for KS Beam. */
    fun openInstallPermissionSettings() {}
}

/** "48.2 MB": a download size, binary units, Latin digits. */
fun formatSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    val tenths = kotlin.math.round(v * 10).toLong()
    return if (i == 0 || tenths % 10 == 0L) "${tenths / 10} ${units[i]}" else "${tenths / 10}.${tenths % 10} ${units[i]}"
}
