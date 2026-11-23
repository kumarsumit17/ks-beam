package com.ks.beam.core

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BytesMedia(override val name: String, val bytes: ByteArray, override val mimeType: String = "video/mp4") : MediaSource {
    override val length: Long get() = bytes.size.toLong()
    override fun openAt(offset: Long): InputStream = ByteArrayInputStream(bytes).apply { skipFully(offset) }
}

class CastingTest {
    private val loopback = InetAddress.getLoopbackAddress()

    @Test fun parsesSsdpReply() {
        val r = SsdpResponse.parse(
            "HTTP/1.1 200 OK\r\nlocation: http://192.168.1.20:8080/d.xml\r\nUSN: uuid:x::urn:a\r\nST: urn:a\r\n\r\n",
        )
        assertNotNull(r)
        assertEquals("http://192.168.1.20:8080/d.xml", r.location)
        assertEquals("uuid:x", r.deviceId)
        assertNull(SsdpResponse.parse("NOTIFY * HTTP/1.1\r\nLOCATION: http://x/\r\n\r\n"))
        assertNull(SsdpResponse.parse("HTTP/1.1 200 OK\r\nLOCATION: file:///etc/passwd\r\n\r\n"))
    }

    @Test fun parsesDescriptionWithUrlBaseAndEmbeddedDevice() {
        val xml = """
            <root xmlns="urn:schemas-upnp-org:device-1-0"><URLBase>http://10.0.0.5:9000/</URLBase>
            <device><friendlyName>Projector</friendlyName><UDN>uuid:p</UDN><deviceList><device>
            <serviceList><service><serviceType>urn:schemas-upnp-org:service:AVTransport:2</serviceType>
            <controlURL>upnp/control/avt</controlURL></service></serviceList></device></deviceList></device></root>
        """.trimIndent().toByteArray()
        val r = assertNotNull(DeviceDescription.parse(xml, "http://10.0.0.5:1234/desc.xml"))
        assertEquals("Projector", r.name)
        assertEquals("http://10.0.0.5:9000/upnp/control/avt", r.avTransportUrl)
        assertEquals("urn:schemas-upnp-org:service:AVTransport:2", r.avTransportType)
    }

    @Test fun skipsDevicesWithoutAvTransportAndRefusesDoctype() {
        val noAvt = "<root><device><friendlyName>NAS</friendlyName></device></root>".toByteArray()
        assertNull(DeviceDescription.parse(noAvt, "http://10.0.0.2/d.xml"))
        val doctype = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><root>&x;</root>"
        assertFailsWith<Exception> { DeviceDescription.parse(doctype.toByteArray(), "http://10.0.0.2/d.xml") }
    }

    @Test fun findsRendererThroughSsdpAndDescription() {
        FakeRenderer().use { tv ->
            FakeSsdpResponder(tv.location).use { ssdp ->
                val finder = RendererFinder(SsdpDiscovery(target = ssdp.address, interfaceAddresses = { listOf(loopback) }))
                val found = mutableListOf<Renderer>()
                finder.find(timeoutMs = 800) { found += it }
                assertEquals(1, found.size, "one device, though SSDP is sent twice")
                assertEquals("Living Room TV", found[0].name)
                assertTrue(found[0].avTransportUrl.endsWith("/ctl/avt"))
                assertContains(ssdp.lastRequest, "ST: urn:schemas-upnp-org:service:AVTransport:1")
                assertContains(ssdp.lastRequest, "MAN: \"ssdp:discover\"")
            }
        }
    }

    @Test fun castSendsUriWithMetadataThenPlaysAndServesTheFile() {
        val bytes = ByteArray(10_000) { (it % 251).toByte() }
        FakeRenderer().use { tv ->
            val renderer = assertNotNull(DeviceDescription.fetch(tv.location))
            Cast(renderer, BytesMedia("My <Holiday> & more.mp4", bytes), serverFor = { MediaServer(loopback) }).use { cast ->
                cast.start()
                val names = tv.actions.map { it.first }
                assertEquals(listOf("Stop", "SetAVTransportURI", "Play"), names)
                val set = tv.actions[1].second
                assertContains(set, "<CurrentURI>${cast.url}</CurrentURI>")
                assertContains(set, "&lt;dc:title&gt;My &amp;lt;Holiday&amp;gt; &amp;amp; more.mp4&lt;/dc:title&gt;")
                assertContains(set, "http-get:*:video/mp4:DLNA.ORG_OP=01")

                val c = URL(cast.url).openConnection() as HttpURLConnection
                c.setRequestProperty("Range", "bytes=100-199")
                assertEquals(206, c.responseCode)
                assertEquals("bytes 100-199/10000", c.getHeaderField("Content-Range"))
                assertEquals("Streaming", c.getHeaderField("transferMode.dlna.org"))
                assertTrue(bytes.copyOfRange(100, 200).contentEquals(c.inputStream.readBytes()))

                val full = URL(cast.url).openConnection() as HttpURLConnection
                assertEquals(200, full.responseCode)
                assertTrue(bytes.contentEquals(full.inputStream.readBytes()))

                assertEquals(Position(42, 100), cast.position())
                assertEquals("PLAYING", cast.state())
                cast.seek(3725)
                assertContains(tv.actions.last().second, "<Target>1:02:05</Target>")
            }
            assertEquals("Stop", tv.actions.last().first, "closing stops the renderer")
        }
    }

    @Test fun serverAnswersOnlyPublishedTokensAndGetHead() {
        MediaServer(loopback).use { server ->
            val url = server.publish(BytesMedia("a.mp3", ByteArray(10), "audio/mpeg"))
            val guess = url.replace(Regex("/m/[0-9a-f]{32}/"), "/m/00000000000000000000000000000000/")
            assertEquals(404, (URL(guess).openConnection() as HttpURLConnection).responseCode)
            assertEquals(404, (URL("http://127.0.0.1:${server.port}/").openConnection() as HttpURLConnection).responseCode)
            val post = URL(url).openConnection() as HttpURLConnection
            post.requestMethod = "DELETE"
            assertEquals(405, post.responseCode)
            val bad = URL(url).openConnection() as HttpURLConnection
            bad.setRequestProperty("Range", "bytes=50-60")
            assertEquals(416, bad.responseCode)
            val head = URL(url).openConnection() as HttpURLConnection
            head.requestMethod = "HEAD"
            assertEquals(200, head.responseCode)
            assertEquals("10", head.getHeaderField("Content-Length"))
            assertEquals("audio/mpeg", head.contentType)
        }
    }

    @Test fun faultCarriesUpnpCode() {
        FakeRenderer(faultFor = mapOf("SetAVTransportURI" to 714)).use { tv ->
            val renderer = assertNotNull(DeviceDescription.fetch(tv.location))
            Cast(renderer, BytesMedia("x.mkv", ByteArray(5), "video/x-matroska"), serverFor = { MediaServer(loopback) }).use { cast ->
                val e = assertFailsWith<UpnpException> { cast.start() }
                assertEquals(714, e.code)
            }
        }
    }

    @Test fun parsesRanges() {
        assertEquals(0L to 99L, MediaServer.parseRange("bytes=0-99", 1000))
        assertEquals(900L to 999L, MediaServer.parseRange("bytes=900-", 1000))
        assertEquals(950L to 999L, MediaServer.parseRange("bytes=-50", 1000))
        assertEquals(0L to 999L, MediaServer.parseRange("bytes=-5000", 1000))
        assertEquals(10L to 999L, MediaServer.parseRange("bytes=10-5000", 1000))
        assertNull(MediaServer.parseRange("bytes=1000-", 1000))
        assertNull(MediaServer.parseRange("bytes=5-2", 1000))
        assertNull(MediaServer.parseRange("items=0-1", 1000))
        assertNull(MediaServer.parseRange("bytes=abc", 1000))
    }

    @Test fun timesAndMimeTypes() {
        assertEquals("0:00:00", AvTransport.formatTime(-3))
        assertEquals("1:02:05", AvTransport.formatTime(3725))
        assertEquals(3725, AvTransport.parseTime("1:02:05.500"))
        assertEquals(0, AvTransport.parseTime("NOT_IMPLEMENTED"))
        assertEquals("video/x-matroska", Mime.forName("Film.MKV"))
        assertEquals("audio/mpeg", Mime.forName("a.mp3"))
        assertNull(Mime.forName("notes.txt"))
        assertEquals(MediaKind.Photo, Mime.kind("image/jpeg"))
    }
}
