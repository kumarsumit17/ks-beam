package com.ks.beam.core

import com.ks.beam.core.receiver.Metadata
import com.ks.beam.core.receiver.PlaybackTarget
import com.ks.beam.core.receiver.RendererServer
import com.ks.beam.core.receiver.SsdpAdvertiser
import com.ks.beam.core.receiver.TransportState
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A player that records what it is told and fetches the address it is given, like Media3 would. */
class RecordingTarget : PlaybackTarget {
    var loaded: Triple<String, String, String?>? = null
    var fetched: ByteArray? = null
    val calls = mutableListOf<String>()
    override var positionMs = 0L
    override var durationMs = 0L
    override var state = TransportState.NO_MEDIA_PRESENT
    override var volume = 50
    override var muted = false

    override fun load(url: String, title: String, mimeType: String?) {
        loaded = Triple(url, title, mimeType)
        fetched = URL(url).openStream().use { it.readBytes() }
        durationMs = 95_000
        state = TransportState.STOPPED
        calls += "load"
    }
    override fun play() { state = TransportState.PLAYING; positionMs = 12_000; calls += "play" }
    override fun pause() { state = TransportState.PAUSED_PLAYBACK; calls += "pause" }
    override fun stop() { state = TransportState.STOPPED; positionMs = 0; calls += "stop" }
    override fun seek(positionMs: Long) { this.positionMs = positionMs; calls += "seek:$positionMs" }
}

class ReceiverTest {
    private val loopback = InetAddress.getLoopbackAddress()

    @Test fun senderFindsAndCastsToTheReceiver() {
        val target = RecordingTarget()
        val bytes = ByteArray(50_000) { (it * 7).toByte() }
        RendererServer("Bedroom Projector", "uuid:ks-beam-test", target, loopback).use { server ->
            SsdpAdvertiser(server.location, "uuid:ks-beam-test", port = 0, interfaceAddresses = { emptyList() }).use { ssdp ->
                val discovery = SsdpDiscovery(target = InetSocketAddress(loopback, ssdp.localPort), interfaceAddresses = { listOf(loopback) })
                val found = mutableListOf<Renderer>()
                RendererFinder(discovery).find(timeoutMs = 1500) { found += it }
                assertEquals(1, found.size)
                val renderer = found.single()
                assertEquals("Bedroom Projector", renderer.name)
                assertEquals("KS-Suite", renderer.manufacturer)

                Cast(renderer, BytesMedia("Trip & Fun.mp4", bytes), serverFor = { MediaServer(loopback) }).use { cast ->
                    cast.start()
                    val (url, title, mime) = assertNotNull(target.loaded)
                    assertEquals(cast.url, url)
                    assertEquals("Trip & Fun.mp4", title)
                    assertEquals("video/mp4", mime)
                    assertTrue(bytes.contentEquals(target.fetched), "the receiver fetched the whole file from the sender")
                    assertEquals(listOf("stop", "load", "play"), target.calls)

                    assertEquals("PLAYING", cast.state())
                    assertEquals(Position(12, 95), cast.position())
                    cast.pause()
                    assertEquals("PAUSED_PLAYBACK", cast.state())
                    cast.seek(61)
                    assertEquals("seek:61000", target.calls.last())
                }
                assertEquals("stop", target.calls.last(), "closing the cast stops the receiver")
            }
        }
    }

    @Test fun answersOnlyMatchingSearchTargets() {
        RendererServer("TV", "uuid:t", RecordingTarget(), loopback).use { server ->
            SsdpAdvertiser(server.location, "uuid:t", port = 0, interfaceAddresses = { emptyList() }).use { ssdp ->
                val to = InetSocketAddress(loopback, ssdp.localPort)
                fun search(st: String): List<SsdpResponse> {
                    val replies = mutableListOf<SsdpResponse>()
                    SsdpDiscovery(target = to, interfaceAddresses = { listOf(loopback) }).search(st, 700) { replies += it }
                    return replies
                }
                assertTrue(search("urn:schemas-upnp-org:service:ContentDirectory:1").isEmpty())
                val all = search("ssdp:all")
                assertTrue(all.any { it.st == "urn:schemas-upnp-org:device:MediaRenderer:1" })
                assertTrue(all.all { it.location == server.location })
            }
        }
    }

    @Test fun rendererRejectsBadRequests() {
        val target = RecordingTarget()
        RendererServer("TV", "uuid:t", target, loopback).use { server ->
            val renderer = assertNotNull(DeviceDescription.fetch(server.location))
            val avt = AvTransport(renderer)
            assertEquals(401, assertFailsWith<UpnpException> { avt.call("Record") }.code)
            assertEquals(402, assertFailsWith<UpnpException> { avt.call("SetAVTransportURI", "CurrentURI" to "file:///etc/passwd") }.code)
            assertEquals(402, assertFailsWith<UpnpException> { avt.call("Seek", "Unit" to "TRACK_NR", "Target" to "1") }.code)
            assertEquals(null, target.loaded, "a file: address is never loaded")
            assertEquals(404, (URL(server.location.replace("desc.xml", "secret")).openConnection() as HttpURLConnection).responseCode)
            val scpd = URL(server.location.replace("desc.xml", "avt.xml")).readText()
            assertContains(scpd, "<name>SetAVTransportURI</name>")
        }
    }

    @Test fun volumeAndProtocolInfo() {
        val target = RecordingTarget()
        RendererServer("TV", "uuid:t", target, loopback).use { server ->
            val base = server.location.removeSuffix("/desc.xml")
            val rc = Renderer("uuid:t", "TV", "", "", "$base/rc/control", "urn:schemas-upnp-org:service:RenderingControl:1")
            AvTransport(rc).call("SetVolume", "Channel" to "Master", "DesiredVolume" to "30")
            assertEquals(30, target.volume)
            assertEquals("30", AvTransport(rc).call("GetVolume", "Channel" to "Master")["CurrentVolume"])
            val cm = Renderer("uuid:t", "TV", "", "", "$base/cm/control", "urn:schemas-upnp-org:service:ConnectionManager:1")
            assertContains(AvTransport(cm).call("GetProtocolInfo")["Sink"].orEmpty(), "http-get:*:video/mp4:*")
        }
    }

    @Test fun readsTitleAndMimeFromDidl() {
        val didl = Didl.item("Song <1>", "http://h/x", "audio/mpeg", 10)
        assertEquals("Song <1>" to "audio/mpeg", Metadata.titleAndMime(didl))
        assertEquals(null to null, Metadata.titleAndMime("NOT_IMPLEMENTED"))
        assertEquals(null to null, Metadata.titleAndMime("<broken"))
    }
}
