package com.ks.beam.android

import android.content.ContentResolver
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.ks.beam.core.MediaSource
import com.ks.beam.core.Mime
import com.ks.beam.core.skipFully
import com.ks.beam.ui.SettingsStore
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale

/** A file chosen with the system picker, read in place through its content URI. */
class ContentMediaSource private constructor(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val name: String,
    override val mimeType: String,
    override val length: Long,
) : MediaSource {
    override fun openAt(offset: Long): InputStream {
        // A file descriptor seeks directly; a plain stream (cloud providers) skips.
        resolver.openAssetFileDescriptor(uri, "r")?.let { afd ->
            if (afd.startOffset == 0L) {
                return FileInputStream(afd.fileDescriptor).apply { channel.position(offset) }
                    .let { stream -> AfdStream(stream, afd) }
            }
            afd.close()
        }
        val stream = resolver.openInputStream(uri) ?: error("cannot open $uri")
        stream.skipFully(offset)
        return stream
    }

    /** Closes the descriptor with the stream. */
    private class AfdStream(private val inner: FileInputStream, private val afd: android.content.res.AssetFileDescriptor) :
        InputStream() {
        override fun read() = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int) = inner.read(b, off, len)
        override fun close() { inner.close(); afd.close() }
    }

    companion object {
        fun from(resolver: ContentResolver, uri: Uri): ContentMediaSource {
            var name = uri.lastPathSegment ?: "media"
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
            if (size < 0) size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            val mime = resolver.getType(uri)?.takeIf(Mime::isSupported) ?: Mime.forName(name) ?: resolver.getType(uri).orEmpty()
            return ContentMediaSource(resolver, uri, name, mime, size.coerceAtLeast(0))
        }
    }
}

class PrefsStore(private val prefs: SharedPreferences) : SettingsStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}

/** Compose resources follow LocalConfiguration on Android, so the choice applies to this subtree only. */
@Composable
fun ProvideAndroidLanguage(language: String, content: @Composable () -> Unit) {
    val base = LocalConfiguration.current
    val configuration = remember(base, language) {
        // Compose resources read the JVM default locale as well as the configuration.
        Locale.setDefault(Locale.forLanguageTag(language))
        Configuration(base).apply { setLocale(Locale.forLanguageTag(language)) }
    }
    CompositionLocalProvider(LocalConfiguration provides configuration) { key(language) { content() } }
}
