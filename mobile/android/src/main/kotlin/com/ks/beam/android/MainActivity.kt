package com.ks.beam.android

import android.net.wifi.WifiManager
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

    @Composable
    override fun ProvideLanguage(language: String, content: @Composable () -> Unit) =
        ProvideAndroidLanguage(language, content)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BeamApp(this) }
    }
}
