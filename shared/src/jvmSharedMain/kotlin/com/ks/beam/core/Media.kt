package com.ks.beam.core

import java.io.InputStream

enum class MediaKind { Video, Audio, Photo }

/** A file chosen by the user, read in place (desktop: a File; Android: a content URI). */
interface MediaSource {
    val name: String
    val mimeType: String
    /** Size in bytes; the file server needs it for Content-Length and ranges. */
    val length: Long

    /** A stream positioned at [offset]. */
    fun openAt(offset: Long): InputStream

    val kind: MediaKind get() = Mime.kind(mimeType)
}

object Mime {
    private val byExtension = mapOf(
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
        "mov" to "video/quicktime", "avi" to "video/x-msvideo", "ts" to "video/mp2t",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "flac" to "audio/flac",
        "wav" to "audio/wav", "ogg" to "audio/ogg",
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif",
        "webp" to "image/webp",
    )

    /** The MIME type for a file name, or null when KS Beam does not cast that kind of file. */
    fun forName(name: String): String? = byExtension[name.substringAfterLast('.', "").lowercase()]

    fun isSupported(mime: String?): Boolean = mime != null && mime in byExtension.values

    fun kind(mime: String): MediaKind = when {
        mime.startsWith("audio/") -> MediaKind.Audio
        mime.startsWith("image/") -> MediaKind.Photo
        else -> MediaKind.Video
    }
}

/** Skips exactly [n] bytes, or throws if the stream ends first. */
fun InputStream.skipFully(n: Long) {
    var left = n
    while (left > 0) {
        val skipped = skip(left)
        if (skipped > 0) {
            left -= skipped
        } else if (read() == -1) {
            throw java.io.EOFException("stream ended $left bytes early")
        } else {
            left--
        }
    }
}

object Didl {
    const val DLNA_FEATURES =
        "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    /** DIDL-Lite metadata for one item (specs/casting.md, section 3). */
    fun item(title: String, url: String, mime: String, size: Long): String {
        val upnpClass = when (Mime.kind(mime)) {
            MediaKind.Video -> "object.item.videoItem"
            MediaKind.Audio -> "object.item.audioItem.musicTrack"
            MediaKind.Photo -> "object.item.imageItem.photo"
        }
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>${xmlEscape(title)}</dc:title>" +
            "<upnp:class>$upnpClass</upnp:class>" +
            "<res protocolInfo=\"http-get:*:$mime:$DLNA_FEATURES\" size=\"$size\">${xmlEscape(url)}</res>" +
            "</item></DIDL-Lite>"
    }
}
