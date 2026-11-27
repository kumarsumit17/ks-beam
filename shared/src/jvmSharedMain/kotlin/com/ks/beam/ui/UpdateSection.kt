package com.ks.beam.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ks.beam.resources.Res
import com.ks.beam.resources.app_name
import com.ks.beam.resources.common_app_update_allow_install
import com.ks.beam.resources.common_app_update_allow_install_button
import com.ks.beam.resources.common_app_update_auto
import com.ks.beam.resources.common_app_update_auto_hint
import com.ks.beam.resources.common_app_update_available
import com.ks.beam.resources.common_app_update_check_button
import com.ks.beam.resources.common_app_update_check_failed
import com.ks.beam.resources.common_app_update_checking
import com.ks.beam.resources.common_app_update_current
import com.ks.beam.resources.common_app_update_damaged
import com.ks.beam.resources.common_app_update_dev
import com.ks.beam.resources.common_app_update_download_failed
import com.ks.beam.resources.common_app_update_downloading
import com.ks.beam.resources.common_app_update_install_button
import com.ks.beam.resources.common_app_update_later
import com.ks.beam.resources.common_app_update_no_installer
import com.ks.beam.resources.common_app_update_open_failed
import com.ks.beam.resources.common_app_update_opened_android
import com.ks.beam.resources.common_app_update_opened_mac_closes
import com.ks.beam.resources.common_app_update_opened_windows
import com.ks.beam.resources.common_app_update_section
import com.ks.beam.resources.common_app_update_unreadable
import com.ks.beam.resources.common_app_update_up_to_date
import com.ks.beam.resources.common_close
import com.ks.beam.update.OpenedOn
import com.ks.beam.update.UpdateFailure
import com.ks.beam.update.UpdateService
import com.ks.beam.update.UpdateStatus
import com.ks.beam.update.formatSize
import org.jetbrains.compose.resources.stringResource

/** The header's "App updates" button: opens the section in a dialog. Sender and receiver. */
@Composable
fun UpdatesButton(updates: UpdateService, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier) { Text(stringResource(Res.string.common_app_update_section)) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(Res.string.common_close)) } },
            text = { UpdateSection(updates) },
        )
    }
}

/**
 * The suite's "App updates" section (ks-utility's `specs/app-update.md`) with the shared texts:
 * the version, the daily automatic check, "Check now", and the install of a newer version.
 */
@Composable
fun UpdateSection(updates: UpdateService) {
    val status by updates.status.collectAsState()
    val auto by updates.autoCheck.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.common_app_update_section), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            if (updates.isDevBuild) stringResource(Res.string.common_app_update_dev)
            else stringResource(Res.string.common_app_update_current, updates.currentVersion),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.common_app_update_auto), Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = updates::setAutoCheck)
        }
        Text(
            stringResource(Res.string.common_app_update_auto_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = updates::checkNow, enabled = status !is UpdateStatus.Checking && status !is UpdateStatus.Downloading) {
            Text(stringResource(Res.string.common_app_update_check_button))
        }
        statusText(status)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Actions(updates, status, notice = false)
    }
}

/**
 * The bar above the interface when the automatic check finds a newer version, until "Later";
 * it then follows the download it started. The section shows the rest.
 */
@Composable
fun UpdateNotice(updates: UpdateService, modifier: Modifier = Modifier) {
    val status by updates.status.collectAsState()
    if (!updateNoticeShown(updates)) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp)) {
            statusText(status)?.let { Text(it) }
            Actions(updates, status, notice = true)
        }
    }
}

/** Whether [UpdateNotice] shows now, for screens that make room for it (the TV's ready screen). */
@Composable
fun updateNoticeShown(updates: UpdateService): Boolean {
    val status by updates.status.collectAsState()
    val later by updates.laterVersion.collectAsState()
    return when (val s = status) {
        is UpdateStatus.Available -> s.version != later
        is UpdateStatus.Downloading, is UpdateStatus.Opened, is UpdateStatus.NeedsPermission -> true
        is UpdateStatus.Failed -> s.failure != UpdateFailure.Check && s.failure != UpdateFailure.Unreadable
        else -> false
    }
}

@Composable
private fun Actions(updates: UpdateService, status: UpdateStatus, notice: Boolean) {
    when (status) {
        is UpdateStatus.Available -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = updates::install) {
                Text(stringResource(Res.string.common_app_update_install_button, formatSize(status.sizeBytes)))
            }
            if (notice) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = updates::later) { Text(stringResource(Res.string.common_app_update_later)) }
            }
        }
        is UpdateStatus.NeedsPermission -> Column(Modifier.padding(top = 8.dp)) {
            Text(stringResource(Res.string.common_app_update_allow_install), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 6.dp)) {
                OutlinedButton(onClick = updates::openInstallPermissionSettings) {
                    Text(stringResource(Res.string.common_app_update_allow_install_button))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = updates::install) { Text(stringResource(Res.string.common_app_update_install_button, formatSize(status.sizeBytes))) }
            }
        }
        else -> {}
    }
}

@Composable
private fun statusText(status: UpdateStatus): String? = when (status) {
    UpdateStatus.Idle, is UpdateStatus.NeedsPermission -> null
    UpdateStatus.Checking -> stringResource(Res.string.common_app_update_checking)
    is UpdateStatus.UpToDate -> stringResource(Res.string.common_app_update_up_to_date, status.version)
    is UpdateStatus.Available -> stringResource(Res.string.common_app_update_available, status.version)
    is UpdateStatus.NoInstaller -> stringResource(Res.string.common_app_update_no_installer, status.version)
    is UpdateStatus.Downloading -> stringResource(Res.string.common_app_update_downloading, status.version)
    is UpdateStatus.Opened -> when (status.on) {
        // The Mac app quits a few seconds after opening the disk image, so Finder can replace it.
        OpenedOn.Mac -> stringResource(Res.string.common_app_update_opened_mac_closes, stringResource(Res.string.app_name))
        OpenedOn.Windows -> stringResource(Res.string.common_app_update_opened_windows)
        OpenedOn.Android -> stringResource(Res.string.common_app_update_opened_android)
    }
    is UpdateStatus.Failed -> when (status.failure) {
        UpdateFailure.Check -> stringResource(Res.string.common_app_update_check_failed, status.detail)
        UpdateFailure.Unreadable -> stringResource(Res.string.common_app_update_unreadable)
        UpdateFailure.Download -> stringResource(Res.string.common_app_update_download_failed, status.detail)
        UpdateFailure.Damaged -> stringResource(Res.string.common_app_update_damaged)
        UpdateFailure.Open -> stringResource(Res.string.common_app_update_open_failed, status.detail)
    }
}
