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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.ks.beam.tv.ExoTarget
import com.ks.beam.tv.NowShowing
import com.ks.beam.tv.ReceiverScreen
import com.ks.beam.tv.isTelevision
import com.ks.beam.tv.startReceiver
import com.ks.beam.tv.tvName
import com.ks.beam.ui.LanguageChoice
import com.ks.beam.ui.languageChoice
import com.ks.beam.ui.resolveLanguage
import com.ks.beam.ui.setLanguageChoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The one activity: the sender on phones (shared interface), the receiver on Google TV. */
class MainActivity : ComponentActivity(), BeamPlatform {
    private var pending: CompletableDeferred<MediaSource?>? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val result = uri?.let { runCatching { ContentMediaSource.from(contentResolver, it) }.getOrNull() }
        pending?.complete(result)
        pending = null
    }

    override val settings: SettingsStore by lazy { PrefsStore(getSharedPreferences("settings", MODE_PRIVATE)) }

    override val systemLanguages: List<String>
        get() {
            val list = LocaleList.getDefault()
            return (0 until list.size()).map { list[it].toLanguageTag() }
        }

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

    // ---- Google TV: the receiver (tv/google-tv) -----------------------------------------------
    private var tv = false
    private var target: ExoTarget? = null
    private var receiver: AutoCloseable? = null
    private var showing by mutableStateOf<NowShowing?>(null)
    private var online by mutableStateOf(true)
    private var languageChoice by mutableStateOf(LanguageChoice.System)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tv = isTelevision(this)
        if (!tv) {
            setContent { BeamApp(this) }
            return
        }
        target = ExoTarget(this) { runOnUiThread { showing = it } }
        languageChoice = settings.languageChoice()
        val name = tvName(this)
        setContent {
            ProvideLanguage(resolveLanguage(languageChoice, systemLanguages)) {
                ReceiverScreen(name, online, showing, target!!, languageChoice) {
                    languageChoice = it
                    settings.setLanguageChoice(it)
                }
            }
        }
    }

    // The receiver runs while the app is on screen.
    override fun onStart() {
        super.onStart()
        val t = target ?: return
        lifecycleScope.launch {
            val started = withContext(Dispatchers.IO) { startReceiver(this@MainActivity, tvName(this@MainActivity), t) }
            receiver = started
            online = started != null
        }
    }

    override fun onStop() {
        super.onStop()
        val r = receiver ?: return
        receiver = null
        target?.stop()
        Thread { r.close() }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        target?.release()
    }

}
