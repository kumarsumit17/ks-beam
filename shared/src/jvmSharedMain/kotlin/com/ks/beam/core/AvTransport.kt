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
    fun call(action: String, vararg args: Pair<String, String>): Map<String, String> {
        val body = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
            append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
            append("<u:$action xmlns:u=\"${renderer.avTransportType}\"><InstanceID>0</InstanceID>")
            for ((name, value) in args) append("<$name>${xmlEscape(value)}</$name>")
            append("</u:$action></s:Body></s:Envelope>")
        }.toByteArray(Charsets.UTF_8)

        val c = URL(renderer.avTransportUrl).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        c.setRequestProperty("SOAPACTION", "\"${renderer.avTransportType}#$action\"")
        c.setFixedLengthStreamingMode(body.size)
        try {
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val response = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) throw fault(response, code)
            return outArguments(response, action)
        } finally {
            c.disconnect()
        }
    }

    private fun outArguments(xml: ByteArray, action: String): Map<String, String> {
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

    private fun fault(xml: ByteArray, httpCode: Int): IOException = runCatching {
        val doc = safeXml().newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val error = doc.documentElement
        val code = error.firstText("errorCode")?.toIntOrNull() ?: return@runCatching null
        UpnpException(code, error.firstText("errorDescription").orEmpty())
    }.getOrNull() ?: IOException("HTTP $httpCode from the renderer")

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
