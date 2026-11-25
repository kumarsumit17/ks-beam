package com.ks.beam.tv

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import androidx.media3.ui.DefaultTimeBar
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
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
import com.ks.beam.resources.receiver_over_apps
import com.ks.beam.resources.receiver_over_apps_allow
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
    target: ExoTarget?,
    languageChoice: LanguageChoice,
    onLanguage: (LanguageChoice) -> Unit,
    canShowOverApps: Boolean = true,
    onAllowOverApps: () -> Unit = {},
) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Color(0xFF0F0F14))) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0F0F14))) {
            when (showing) {
                null -> Ready(name, online, languageChoice, onLanguage, canShowOverApps, onAllowOverApps)
                is NowShowing.Media -> if (target != null) Playback(target)
                is NowShowing.Photo -> Photo(showing.url, showing.title)
                is NowShowing.CannotPlay -> Message(stringResource(Res.string.receiver_cant_play, showing.title))
            }
            if (showing != null) BackHandler { target?.stop() }
        }
    }
}

@Composable
private fun Ready(
    name: String,
    online: Boolean,
    choice: LanguageChoice,
    onLanguage: (LanguageChoice) -> Unit,
    canShowOverApps: Boolean,
    onAllowOverApps: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(48.dp)) {
        val focus = remember { FocusRequester() }
        Box(Modifier.align(Alignment.TopEnd).focusRequester(focus)) { LanguageMenu(choice, onLanguage) }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 900.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Every TV size lays out 960 x 540 dp; with the "over other apps" hint the banner is
            // smaller so everything fits inside the margins.
            val compact = !canShowOverApps
            Image(
                painterResource(R.drawable.tv_banner), contentDescription = null,
                modifier = if (compact) Modifier.size(224.dp, 126.dp) else Modifier.size(320.dp, 180.dp),
            )
            Spacer(Modifier.height(if (compact) 20.dp else 32.dp))
            Text(stringResource(Res.string.receiver_ready), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Text(
                if (online) stringResource(Res.string.receiver_how_to, name) else stringResource(Res.string.receiver_no_network),
                color = Color(0xFFC9C6D6), fontSize = 24.sp, textAlign = TextAlign.Center,
            )
            // Without "Display over other apps" a cast cannot come forward while another app is on
            // screen (Android blocks it); a video then waits, paused, until KS Beam is opened.
            if (!canShowOverApps) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(Res.string.receiver_over_apps),
                    color = Color(0xFFC9C6D6), fontSize = 20.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                var focused by remember { mutableStateOf(false) }
                Button(
                    onClick = onAllowOverApps,
                    // A clear focus ring for the remote (Google TV guidelines).
                    modifier = Modifier.heightIn(min = 56.dp).onFocusChanged { focused = it.isFocused }
                        .border(3.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(50)),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                ) {
                    // The button style's 20 sp line clips Devanagari at this size.
                    Text(stringResource(Res.string.receiver_over_apps_allow), fontSize = 20.sp, lineHeight = 30.sp)
                }
            }
        }
    }
}

@OptIn(UnstableApi::class) // PlayerView's controller timeout and the time bar's key step
@Composable
private fun Playback(target: ExoTarget) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                player = target.player
                useController = true
                controllerShowTimeoutMs = 3000
                keepScreenOn = true
                isFocusable = true
                findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.setKeyTimeIncrement(SEEK_STEP_MS)
                post { requestFocus() } // once attached, so the remote's keys reach the controls
                RemoteKeys.view = this
            }
        },
        onRelease = { if (RemoteKeys.view === it) RemoteKeys.view = null },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun Photo(url: String, title: String) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, url) { value = withContext(Dispatchers.IO) { loadPhoto(url, displaySize(context)) } }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
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

/** The display's real resolution (a 4K TV reports 4K here even when its interface runs at 1080p). */
private fun displaySize(context: Context): Pair<Int, Int> {
    @Suppress("DEPRECATION")
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display
    else (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    val mode = display?.mode
    val w = mode?.physicalWidth ?: 1920
    val h = mode?.physicalHeight ?: 1080
    return maxOf(w, h) to minOf(w, h)
}

/**
 * Downloads a photo (at most 40 MB), decodes it no larger than [screen] needs and turns it upright
 * from its EXIF orientation (phone photos are often stored sideways).
 */
// The framework ExifInterface reads streams correctly from API 25; minSdk is 26, so no AndroidX copy.
@SuppressLint("ExifInterface")
private fun loadPhoto(url: String, screen: Pair<Int, Int>): Bitmap? = runCatching {
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
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val sideways = orientation in listOf(
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
    )
    // The size as it will be shown (upright), against the screen.
    val (w, h) = if (sideways) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
    var sample = 1
    while (w / (sample * 2) >= screen.first || h / (sample * 2) >= screen.second) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> return@runCatching decoded
    }
    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}.getOrNull()

/** Held this many key repeats (about half a second), the remote seeks in [HELD_STEP_MS] steps. */
private const val HOLD_REPEATS = 3
private const val HELD_STEP_MS = 30_000L

/**
 * The remote during playback, before any view sees the key ([ReceiverActivity.dispatchKeyEvent]):
 * with the controls hidden, left and right seek straight away, 10 s a press and 30 s steps while
 * held. With the controls shown, the D-pad moves between them as usual.
 */
@OptIn(UnstableApi::class) // isControllerFullyVisible
internal object RemoteKeys {
    // Weak, so a player view the system drops without onRelease is never kept alive here.
    private var ref: WeakReference<PlayerView>? = null
    var view: PlayerView?
        get() = ref?.get()
        set(value) { ref = value?.let(::WeakReference) }

    fun handle(event: KeyEvent): Boolean {
        val v = view ?: return false
        val step = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            else -> return false
        }
        if (v.isControllerFullyVisible) return false
        val p = v.player ?: return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            val by = if (event.repeatCount >= HOLD_REPEATS) HELD_STEP_MS else SEEK_STEP_MS
            p.seekTo((p.currentPosition + step * by).coerceIn(0, p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        }
        return true
    }
}
