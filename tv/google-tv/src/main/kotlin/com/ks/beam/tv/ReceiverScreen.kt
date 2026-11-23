package com.ks.beam.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.ks.beam.resources.Res
import com.ks.beam.resources.receiver_back_hint
import com.ks.beam.resources.receiver_cant_play
import com.ks.beam.resources.receiver_how_to
import com.ks.beam.resources.receiver_no_network
import com.ks.beam.resources.receiver_ready
import com.ks.beam.ui.LanguageChoice
import com.ks.beam.ui.LanguageMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.net.HttpURLConnection
import java.net.URL

private val Accent = Color(0xFF7C5CFF)

/**
 * The receiver on the TV: the ready screen, or what a sender cast. D-pad: the language button on
 * the ready screen; Media3's controls during playback; Back stops what is showing.
 */
@Composable
fun ReceiverScreen(
    name: String,
    online: Boolean,
    showing: NowShowing?,
    target: ExoTarget,
    languageChoice: LanguageChoice,
    onLanguage: (LanguageChoice) -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Color(0xFF0F0F14))) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0F0F14))) {
            when (showing) {
                null -> Ready(name, online, languageChoice, onLanguage)
                is NowShowing.Media -> Playback(target)
                is NowShowing.Photo -> Photo(showing.url)
                is NowShowing.CannotPlay -> Message(stringResource(Res.string.receiver_cant_play, showing.title))
            }
            if (showing != null) BackHandler { target.stop() }
        }
    }
}

@Composable
private fun Ready(name: String, online: Boolean, choice: LanguageChoice, onLanguage: (LanguageChoice) -> Unit) {
    Box(Modifier.fillMaxSize().padding(48.dp)) {
        val focus = remember { FocusRequester() }
        Box(Modifier.align(Alignment.TopEnd).focusRequester(focus)) { LanguageMenu(choice, onLanguage) }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 900.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(R.drawable.tv_banner), contentDescription = null, modifier = Modifier.size(320.dp, 180.dp))
            Spacer(Modifier.height(32.dp))
            Text(stringResource(Res.string.receiver_ready), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Text(
                if (online) stringResource(Res.string.receiver_how_to, name) else stringResource(Res.string.receiver_no_network),
                color = Color(0xFFC9C6D6), fontSize = 24.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Playback(target: ExoTarget) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                player = target.player
                useController = true
                controllerShowTimeoutMs = 3000
                keepScreenOn = true
                requestFocus()
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun Photo(url: String) {
    val bitmap by produceState<Bitmap?>(null, url) { value = withContext(Dispatchers.IO) { loadPhoto(url) } }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
    Text(
        stringResource(Res.string.receiver_back_hint),
        color = Color(0x99FFFFFF),
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = Color.White, fontSize = 28.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.receiver_back_hint), color = Color(0x99FFFFFF))
        }
    }
}

/** Downloads a photo (at most 40 MB) and decodes it no larger than about 4K. */
private fun loadPhoto(url: String): Bitmap? = runCatching {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 5000
    c.readTimeout = 15000
    val bytes = c.inputStream.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > 40 * 1024 * 1024) error("photo too large")
        }
        out.toByteArray()
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 3840 || bounds.outHeight / (sample * 2) >= 2160) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
