package com.ks.beam.tv

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import com.ks.beam.android.PrefsStore
import com.ks.beam.android.androidUpdater
import com.ks.beam.android.ProvideAndroidLanguage
import com.ks.beam.android.systemLanguageTags
import com.ks.beam.resources.Res
import com.ks.beam.resources.receiver_ready
import com.ks.beam.ui.LanguageChoice
import com.ks.beam.ui.languageChoice
import com.ks.beam.ui.resolveLanguage
import com.ks.beam.ui.setLanguageChoice
import org.jetbrains.compose.resources.stringResource

/**
 * The Google TV app's activity: shows the receiver ([ReceiverService], specs/receiver.md). Opening
 * it starts the service, which keeps receiving after the activity closes. The sender is the
 * separate phone app (mobile/android).
 */
class ReceiverActivity : ComponentActivity() {
    // The same settings file as the phone app and as v1.0.1's single APK, so a TV that had the
    // single APK keeps its language choice.
    private val settings by lazy { PrefsStore(getSharedPreferences("settings", MODE_PRIVATE)) }
    private var languageChoice by mutableStateOf(LanguageChoice.System)
    private var canShowOverApps by mutableStateOf(true)
    // The TV app's own APK from KS Beam's releases, never the phone app's.
    private val updater by lazy { androidUpdater(this, settings, tv = true) }

    // Android 17 asks before an app reaches the local network; the receiver starts once the user
    // has answered (it is tried either way).
    private val askLocalNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        ReceiverService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        languageChoice = settings.languageChoice()
        val name = tvName(this)
        if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_SDK && checkSelfPermission(LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            askLocalNetwork.launch(LOCAL_NETWORK)
        } else {
            ReceiverService.start(this)
        }
        setContent {
            ProvideAndroidLanguage(resolveLanguage(languageChoice, systemLanguageTags())) {
                val ready = stringResource(Res.string.receiver_ready)
                SideEffect { ReceiverService.readyLabel = ready }
                val target = ReceiverService.target
                ReceiverScreen(
                    name, ReceiverService.online, ReceiverService.showing.takeIf { target != null }, target, languageChoice,
                    onLanguage = {
                        languageChoice = it
                        settings.setLanguageChoice(it)
                    },
                    canShowOverApps = canShowOverApps,
                    onAllowOverApps = ::openOverAppsSetting,
                    updates = updater,
                )
            }
        }
    }

    override fun onDestroy() {
        updater.close()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        ReceiverService.visible = true
        ReceiverService.resumeHeld()
    }

    // The remote's keys before any view: the player's controls take focus inside Compose, so the
    // activity is the one place that always sees left and right. AndroidX marks this override as
    // library-internal, but overriding it in an app is supported Activity behaviour.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = RemoteKeys.handle(event) || super.dispatchKeyEvent(event)

    override fun onResume() {
        super.onResume()
        canShowOverApps = Settings.canDrawOverlays(this) // may have changed in Settings
    }

    /** "Display over other apps" for KS Beam; the app's settings page where that screen is missing. */
    private fun openOverAppsSetting() {
        val uri = Uri.parse("package:$packageName")
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)) }
    }

    override fun onStop() {
        super.onStop()
        ReceiverService.visible = false
        // A video needs the screen: it pauses when KS Beam leaves it (the sender shows "paused"); a
        // song keeps playing. The receiver keeps running, and a new cast brings KS Beam back.
        val player = ReceiverService.target?.player ?: return
        if (ReceiverService.showing is NowShowing.Media && player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) player.pause()
    }
}

/** Android 17: the permission to reach the local network (not yet named in Build.VERSION_CODES here). */
private const val LOCAL_NETWORK_SDK = 37
private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
