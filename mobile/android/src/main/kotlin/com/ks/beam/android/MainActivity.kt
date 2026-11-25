package com.ks.beam.android

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.ks.beam.core.MediaSource
import com.ks.beam.ui.BeamApp
import com.ks.beam.ui.BeamPlatform
import com.ks.beam.ui.CastLock
import com.ks.beam.ui.CastSession
import com.ks.beam.ui.SettingsStore
import kotlinx.coroutines.CompletableDeferred

/**
 * The phone app's activity: the sender (the shared interface). The receiver is the separate
 * Google TV app (tv/google-tv); this app is the sender on every device it is installed on.
 */
class MainActivity : ComponentActivity(), BeamPlatform {
    private var pending: CompletableDeferred<MediaSource?>? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val result = uri?.let { runCatching { ContentMediaSource.from(contentResolver, it) }.getOrNull() }
        pending?.complete(result)
        pending = null
    }

    // Asked once, when it matters: the notification when the first cast starts (Android 13+).
    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Android 17 asks before an app reaches the local network: the interface (and its first search)
    // starts once the user has answered.
    private val askLocalNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { show() }

    // Debug builds only: `adb shell am start -n com.ks.beam/.android.MainActivity --es ks.beam.renderer <desc.xml URL>`
    // casts to a renderer that SSDP cannot reach (emulators behind their own NAT).
    override val extraRenderers: List<String>
        get() = if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            listOfNotNull(intent?.getStringExtra("ks.beam.renderer"))
        } else {
            emptyList()
        }

    override val settings: SettingsStore by lazy { PrefsStore(getSharedPreferences("settings", MODE_PRIVATE)) }

    override val systemLanguages: List<String>
        get() = systemLanguageTags()

    override suspend fun pickMedia(): MediaSource? {
        pending?.complete(null)
        val deferred = CompletableDeferred<MediaSource?>()
        pending = deferred
        picker.launch(arrayOf("video/*", "audio/*", "image/*"))
        return deferred.await()
    }

    override fun searchLock(): AutoCloseable {
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("ks-beam-ssdp").apply { setReferenceCounted(false); acquire() }
        return AutoCloseable { if (lock.isHeld) lock.release() }
    }

    override fun castLock(session: CastSession): CastLock {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        return CastService.lock(this, session)
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    @Composable
    override fun ProvideLanguage(language: String, content: @Composable () -> Unit) =
        ProvideAndroidLanguage(language, content)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_SDK && !granted(LOCAL_NETWORK)) askLocalNetwork.launch(LOCAL_NETWORK) else show()
    }

    private var shown = false

    private fun show() {
        if (shown) return
        shown = true
        setContent { BeamApp(this) }
    }
}

/** Android 17: the permission to reach the local network (not yet named in Build.VERSION_CODES here). */
internal const val LOCAL_NETWORK_SDK = 37
internal const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
