package com.ks.beam.core

import java.util.concurrent.ConcurrentHashMap

/** Finds renderers: SSDP search, then each new device's description (specs/casting.md, section 1). */
class RendererFinder(private val ssdp: SsdpDiscovery = SsdpDiscovery()) {
    /** Blocks for about [timeoutMs]; calls [onFound] once per renderer, from the calling thread. */
    fun find(timeoutMs: Int = 3000, onFound: (Renderer) -> Unit) {
        val seen = ConcurrentHashMap.newKeySet<String>()
        ssdp.search(timeoutMs = timeoutMs) { reply ->
            if (seen.add(reply.deviceId.ifEmpty { reply.location })) {
                DeviceDescription.fetch(reply.location)?.let(onFound)
            }
        }
    }
}

/**
 * One cast: serves [media] and drives [renderer]. Blocking calls; the interface runs them off the
 * main thread. [serverFor] is replaceable in tests.
 */
class Cast(
    val renderer: Renderer,
    val media: MediaSource,
    serverFor: (Renderer) -> MediaServer = { MediaServer(MediaServer.localAddressFor(it.host)) },
) : AutoCloseable {
    private val server = serverFor(renderer)
    private val transport = AvTransport(renderer)
    private val rendering = RenderingControl.of(renderer)

    /** Whether the TV's volume can be read and set (it has a RenderingControl service). */
    val hasVolume: Boolean get() = rendering != null
    fun volume(): Int? = rendering?.volume()
    fun setVolume(level: Int) { rendering?.setVolume(level) }
    val url: String = server.publish(media)

    /** Loads the file on the renderer and starts it. */
    fun start() {
        // Some renderers refuse a new address while playing; stopping first is harmless.
        runCatching { transport.stop() }
        transport.setUri(url, Didl.item(media.name, url, media.mimeType, media.length))
        transport.play()
    }

    fun play() = transport.play()
    fun pause() = transport.pause()
    fun seek(seconds: Long) = transport.seek(seconds)
    fun position(): Position = transport.position()
    fun state(): String = transport.state()

    /** Stops the renderer (best effort) and the file server. */
    override fun close() {
        runCatching { transport.stop() }
        server.close()
    }
}
