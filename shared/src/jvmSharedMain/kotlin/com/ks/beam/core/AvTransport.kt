package com.ks.beam.core

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A SOAP fault from the renderer, with its UPnP error code (for example 714: illegal MIME type). */
class UpnpException(val code: Int, message: String) : IOException("UPnP error $code: $message")

data class Position(val seconds: Long, val durationSeconds: Long)

/** Controls one renderer's AVTransport service (specs/casting.md, section 3). */
class AvTransport(private val renderer: Renderer, private val timeoutMs: Int = 5000) {

    fun setUri(url: String, metadata: String) =
        call("SetAVTransportURI", "CurrentURI" to url, "CurrentURIMetaData" to metadata)

    fun play() = call("Play", "Speed" to "1")
    fun pause() = call("Pause")
    fun stop() = call("Stop")
    fun seek(seconds: Long) = call("Seek", "Unit" to "REL_TIME", "Target" to formatTime(seconds))

    fun position(): Position {
        val r = call("GetPositionInfo")
        return Position(parseTime(r["RelTime"]), parseTime(r["TrackDuration"]))
    }

    /** `PLAYING`, `PAUSED_PLAYBACK`, `STOPPED`, `TRANSITIONING` or `NO_MEDIA_PRESENT`. */
    fun state(): String = call("GetTransportInfo")["CurrentTransportState"].orEmpty()

    /** Sends one action; returns the response's out-arguments by name. */
    fun call(action: String, vararg args: Pair<String, String>): Map<String, String> =
        soapCall(renderer.avTransportUrl, renderer.avTransportType, action, args.toList(), timeoutMs)

    companion object {
        /** `H:MM:SS`, as UPnP's REL_TIME expects. */
        fun formatTime(seconds: Long): String {
            val s = seconds.coerceAtLeast(0)
            return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
        }

        /** Parses `H:MM:SS[.fff]`; `NOT_IMPLEMENTED`, blanks and garbage count as 0. */
        fun parseTime(text: String?): Long {
            val parts = text?.substringBefore('.')?.split(':') ?: return 0
            if (parts.size != 3) return 0
            val (h, m, s) = parts.map { it.trim().toLongOrNull() ?: return 0 }
            return h * 3600 + m * 60 + s
        }
    }
}

internal fun xmlEscape(text: String): String = buildString(text.length) {
    for (ch in text) when (ch) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&apos;")
        else -> append(ch)
    }
}

/**
 * Controls one renderer's RenderingControl service: the TV's own volume (specs/casting.md,
 * section 3). Only for a renderer that has one ([Renderer.renderingControlUrl]).
 */
class RenderingControl(private val url: String, private val type: String, private val timeoutMs: Int = 5000) {
    /** The volume, 0 to 100 (DLNA's usual range; values above 100 are clamped). */
    fun volume(): Int = call("GetVolume")["CurrentVolume"]?.toIntOrNull()?.coerceIn(0, 100) ?: 0

    fun setVolume(level: Int) { call("SetVolume", "DesiredVolume" to level.coerceIn(0, 100).toString()) }

    private fun call(action: String, vararg args: Pair<String, String>) =
        soapCall(url, type, action, listOf("Channel" to "Master") + args, timeoutMs)

    companion object {
        fun of(renderer: Renderer): RenderingControl? {
            val url = renderer.renderingControlUrl ?: return null
            return RenderingControl(url, renderer.renderingControlType ?: return null)
        }
    }
}

/**
 * Sends one UPnP SOAP action to [url] (service [type], instance 0); returns its out-arguments. A
 * failure of the connection itself (often a kept-alive connection the TV already closed) is tried
 * once more; the TV refusing the action (a UPnP error) is not.
 */
internal fun soapCall(url: String, type: String, action: String, args: List<Pair<String, String>>, timeoutMs: Int): Map<String, String> =
    try {
        soapCallOnce(url, type, action, args, timeoutMs)
    } catch (e: UpnpException) {
        throw e
    } catch (e: IOException) {
        soapCallOnce(url, type, action, args, timeoutMs)
    }

private fun soapCallOnce(url: String, type: String, action: String, args: List<Pair<String, String>>, timeoutMs: Int): Map<String, String> {
    val body = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
        append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
        append("<u:$action xmlns:u=\"$type\"><InstanceID>0</InstanceID>")
        for ((name, value) in args) append("<$name>${xmlEscape(value)}</$name>")
        append("</u:$action></s:Body></s:Envelope>")
    }.toByteArray(Charsets.UTF_8)

    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = timeoutMs
    c.readTimeout = timeoutMs
    c.requestMethod = "POST"
    c.doOutput = true
    c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
    c.setRequestProperty("SOAPACTION", "\"$type#$action\"")
    c.setFixedLengthStreamingMode(body.size)
    try {
        c.outputStream.use { it.write(body) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val response = stream?.use { it.readBytes() } ?: ByteArray(0)
        if (code !in 200..299) throw soapFault(response, code)
        return soapOutArguments(response, action)
    } finally {
        c.disconnect()
    }
}

private fun soapOutArguments(xml: ByteArray, action: String): Map<String, String> {
    if (xml.isEmpty()) return emptyMap()
    val doc = safeXml().newDocumentBuilder().parse(ByteArrayInputStream(xml))
    val response = doc.getElementsByTagNameNS("*", "${action}Response").item(0) as? Element
        ?: return emptyMap()
    val out = mutableMapOf<String, String>()
    val children = response.childNodes
    for (i in 0 until children.length) {
        val child = children.item(i) as? Element ?: continue
        out[child.localName ?: child.tagName] = child.textContent.trim()
    }
    return out
}

private fun soapFault(xml: ByteArray, httpCode: Int): IOException = runCatching {
    val doc = safeXml().newDocumentBuilder().parse(ByteArrayInputStream(xml))
    val error = doc.documentElement
    val code = error.firstText("errorCode")?.toIntOrNull() ?: return@runCatching null
    UpnpException(code, error.firstText("errorDescription").orEmpty())
}.getOrNull() ?: IOException("HTTP $httpCode from the renderer")
