package com.ks.beam.core

import java.io.InputStream
import java.io.OutputStream

/** One parsed HTTP/1.1 request head; header names are lower case. */
internal class HttpRequest(val method: String, val path: String, val headers: Map<String, String>) {
    /** Reads the body named by Content-Length (at most [max] bytes). */
    fun body(input: InputStream, max: Int = 256 * 1024): ByteArray {
        val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, max) ?: return ByteArray(0)
        val out = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(out, read, length - read)
            if (n < 0) break
            read += n
        }
        return if (read == length) out else out.copyOf(read)
    }

    companion object {
        /** Reads the request line and headers; null on a closed or malformed connection. */
        fun read(input: InputStream): HttpRequest? {
            val requestLine = readLine(input) ?: return null
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: return null
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                if (headers.size > 100) return null
            }
            val parts = requestLine.split(' ')
            if (parts.size < 2) return null
            return HttpRequest(parts[0], parts[1].substringBefore('?'), headers)
        }

        private fun readLine(input: InputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b == -1) return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code) return sb.toString().trimEnd('\r')
                if (sb.length > 8192) return null
                sb.append(b.toChar())
            }
        }
    }
}

/** Writes a complete response with a body and closes nothing. */
internal fun OutputStream.respond(code: Int, reason: String, contentType: String? = null, body: ByteArray = ByteArray(0)) {
    val head = buildString {
        append("HTTP/1.1 $code $reason\r\n")
        if (contentType != null) append("Content-Type: $contentType\r\n")
        append("Content-Length: ${body.size}\r\n")
        append("Server: KS-Beam/1 UPnP/1.1\r\n")
        append("Connection: close\r\n\r\n")
    }
    write(head.toByteArray(Charsets.US_ASCII))
    write(body)
    flush()
}
