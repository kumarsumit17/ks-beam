package com.ks.beam.windows

import com.ks.beam.desktop.OpenedFiles
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.prefs.Preferences

/**
 * One running KS Beam on Windows. The running copy listens on a loopback port; a second launch
 * connects, sends a token and its file paths, and exits. The port and a random token are kept in the
 * user's own settings, so only this user's processes can hand files over, and only to cast them.
 */
internal object SingleInstance {
    private val prefs = Preferences.userRoot().node("com/ks/beam/instance")

    /** True when a running copy took [files] (and came to the front); false when this is the first. */
    fun handOver(files: List<File>): Boolean {
        val port = prefs.getInt("port", 0).takeIf { it > 0 } ?: return false
        val token = prefs.get("token", null) ?: return false
        return runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500)
                s.soTimeout = 1000
                val out = s.getOutputStream().bufferedWriter(Charsets.UTF_8)
                out.write(token + "\n")
                files.forEach { out.write(it.absolutePath + "\n") }
                out.write("\n")
                out.flush()
                s.getInputStream().bufferedReader().readLine() == "ok"
            }
        }.getOrDefault(false)
    }

    /** Becomes the running copy: listens for later launches. */
    fun listen() {
        val server = runCatching { ServerSocket(0, 8, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        val token = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        prefs.putInt("port", server.localPort)
        prefs.put("token", token)
        prefs.flush()
        Thread({
            while (true) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    s.use {
                        it.soTimeout = 2000
                        val input = it.getInputStream().bufferedReader(Charsets.UTF_8)
                        if (input.readLine() != token) return@use
                        generateSequence { input.readLine()?.takeIf(String::isNotEmpty) }
                            .map(::File).filter(File::isFile).firstOrNull()
                            ?.let(OpenedFiles::open)
                            ?: OpenedFiles.bringToFront()
                        it.getOutputStream().write("ok\n".toByteArray())
                    }
                }
            }
        }, "ks-beam-single-instance").apply { isDaemon = true; start() }
    }
}
