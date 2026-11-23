package com.ks.beam.core

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Serves the files being cast over HTTP/1.1, with byte ranges, only at addresses carrying a
 * 128-bit random token (specs/casting.md, section 2; specs/constraints.md).
 */
class MediaServer(
    private val bindAddress: InetAddress,
    /** The host renderers use to reach this server; differs from [bindAddress] behind NAT (emulators). */
    private val advertisedHost: String = bindAddress.hostAddress,
) : AutoCloseable {
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(bindAddress, 0))
    }
    private val files = ConcurrentHashMap<String, MediaSource>()
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "ks-beam-http").apply { isDaemon = true } }
    @Volatile private var closed = false

    val port: Int get() = server.localPort

    init {
        pool.execute {
            while (!closed) {
                val socket = try { server.accept() } catch (_: SocketException) { break }
                pool.execute { socket.use(::handle) }
            }
        }
    }

    /** Registers [media]; returns the address a renderer can fetch it from. */
    fun publish(media: MediaSource): String {
        val token = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        files[token] = media
        val name = URLEncoder.encode(media.name, "UTF-8").replace("+", "%20")
        return "http://$advertisedHost:$port/m/$token/$name"
    }

    fun unpublishAll() = files.clear()

    override fun close() {
        closed = true
        files.clear()
        runCatching { server.close() }
        pool.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 30_000
        val input = BufferedInputStream(socket.getInputStream())
        val out = socket.getOutputStream()
        val request = HttpRequest.read(input) ?: return
        val headers = request.headers
        val method = request.method
        if (method != "GET" && method != "HEAD") return respond(out, 405, "Method Not Allowed")

        val segments = request.path.split('/')
        val media = if (segments.size >= 3 && segments[1] == "m") files[segments[2]] else null
        if (media == null) return respond(out, 404, "Not Found")

        val total = media.length
        val range = headers["range"]?.let { parseRange(it, total) }
        if (headers.containsKey("range") && range == null) {
            return respond(out, 416, "Range Not Satisfiable", "Content-Range: bytes */$total\r\n")
        }
        val (start, end) = range ?: (0L to total - 1)
        val length = (end - start + 1).coerceAtLeast(0)
        val head = buildString {
            append(if (range != null) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            append("Content-Type: ${media.mimeType}\r\n")
            append("Content-Length: $length\r\n")
            append("Accept-Ranges: bytes\r\n")
            if (range != null) append("Content-Range: bytes $start-$end/$total\r\n")
            append("transferMode.dlna.org: Streaming\r\n")
            append("contentFeatures.dlna.org: ${Didl.DLNA_FEATURES}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.US_ASCII))
        if (method == "GET" && length > 0) {
            try {
                media.openAt(start).use { copy(it, out, length) }
            } catch (_: SocketException) {
                // The renderer closed the connection (seeking or stopping): normal.
            }
        }
        out.flush()
    }

    private fun respond(out: OutputStream, code: Int, reason: String, extra: String = "") {
        out.write("HTTP/1.1 $code $reason\r\n${extra}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
    }

    companion object {
        private val random = SecureRandom()

        /**
         * `bytes=a-b`, `bytes=a-` or `bytes=-n` against a file of [total] bytes; the inclusive
         * byte range, or null when it cannot be satisfied. Only the first range is used.
         */
        fun parseRange(header: String, total: Long): Pair<Long, Long>? {
            if (!header.startsWith("bytes=") || total <= 0) return null
            val spec = header.removePrefix("bytes=").substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash < 0) return null
            val a = spec.substring(0, dash).trim()
            val b = spec.substring(dash + 1).trim()
            return when {
                a.isEmpty() -> {
                    val n = b.toLongOrNull()?.takeIf { it > 0 } ?: return null
                    (total - n).coerceAtLeast(0) to total - 1
                }
                else -> {
                    val start = a.toLongOrNull() ?: return null
                    val end = if (b.isEmpty()) total - 1 else (b.toLongOrNull() ?: return null).coerceAtMost(total - 1)
                    if (start >= total || start > end) null else start to end
                }
            }
        }

        /** The local address of the interface that reaches [host]. Sends nothing. */
        fun localAddressFor(host: String): InetAddress = DatagramSocket().use { s ->
            s.connect(InetSocketAddress(host, 1900))
            s.localAddress
        }

        private fun copy(input: InputStream, out: OutputStream, length: Long) {
            val buffer = ByteArray(64 * 1024)
            var left = length
            while (left > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) break
                out.write(buffer, 0, n)
                left -= n
            }
        }
    }
}
