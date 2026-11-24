package com.ks.beam.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.ks.beam.android.PrefsStore
import com.ks.beam.android.ProvideAndroidLanguage
import com.ks.beam.android.systemLanguageTags
import com.ks.beam.ui.LanguageChoice
import com.ks.beam.ui.languageChoice
import com.ks.beam.ui.resolveLanguage
import com.ks.beam.ui.setLanguageChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Google TV app's activity: the receiver (specs/receiver.md). It runs while the app is on
 * screen. The sender is the separate phone app (mobile/android).
 */
class ReceiverActivity : ComponentActivity() {
    // The same settings file as the phone app and as v1.0.1's single APK, so a TV that had the
    // single APK keeps its language choice.
    private val settings by lazy { PrefsStore(getSharedPreferences("settings", MODE_PRIVATE)) }
    private lateinit var target: ExoTarget
    private var receiver: AutoCloseable? = null
    private var showing by mutableStateOf<NowShowing?>(null)
    private var online by mutableStateOf(true)
    private var languageChoice by mutableStateOf(LanguageChoice.System)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        target = ExoTarget(this) { runOnUiThread { showing = it } }
        languageChoice = settings.languageChoice()
        val name = tvName(this)
        setContent {
            ProvideAndroidLanguage(resolveLanguage(languageChoice, systemLanguageTags())) {
                ReceiverScreen(name, online, showing, target, languageChoice) {
                    languageChoice = it
                    settings.setLanguageChoice(it)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val started = withContext(Dispatchers.IO) { startReceiver(this@ReceiverActivity, tvName(this@ReceiverActivity), target) }
            receiver = started
            online = started != null
        }
    }

    override fun onStop() {
        super.onStop()
        val r = receiver ?: return
        receiver = null
        target.stop()
        Thread { r.close() }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        target.release()
    }
}
