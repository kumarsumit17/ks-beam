package com.ks.beam.core

import com.sun.net.httpserver.HttpServer
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A DLNA renderer for tests: serves a device description and records the SOAP actions it gets.
 * [faultFor] makes an action answer with a UPnP error code.
 */
class FakeRenderer(private val faultFor: Map<String, Int> = emptyMap()) : AutoCloseable {
    val actions = CopyOnWriteArrayList<Pair<String, String>>() // action to request body
    private val http = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    val location: String get() = "http://127.0.0.1:${http.address.port}/desc.xml"

    init {
        http.createContext("/desc.xml") { ex ->
            val body = DESCRIPTION.toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        http.createContext("/ctl/avt") { ex ->
            val soapAction = ex.requestHeaders.getFirst("SOAPACTION").orEmpty().trim('"')
            val action = soapAction.substringAfter('#')
            actions += action to ex.requestBody.readBytes().decodeToString()
            val code = faultFor[action]
            val (status, body) = if (code != null) {
                500 to envelope("<s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>" +
                    "<UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>$code</errorCode>" +
                    "<errorDescription>Illegal MIME-type</errorDescription></UPnPError></detail></s:Fault>")
            } else {
                200 to envelope("<u:${action}Response xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                    responseArgs(action) + "</u:${action}Response>")
            }
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        http.createContext("/ctl/rc") { ex ->
            val action = ex.requestHeaders.getFirst("SOAPACTION").orEmpty().trim('"').substringAfter('#')
            val request = ex.requestBody.readBytes().decodeToString()
            actions += action to request
            if (action == "SetVolume") {
                Regex("<DesiredVolume>(\\d+)</DesiredVolume>").find(request)?.let { volume = it.groupValues[1].toInt() }
            }
            val args = if (action == "GetVolume") "<CurrentVolume>$volume</CurrentVolume>" else ""
            val bytes = envelope("<u:${action}Response xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\">$args</u:${action}Response>").toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        http.start()
    }

    /** The TV's volume as RenderingControl reports and sets it. */
    @Volatile var volume = 30

    private fun responseArgs(action: String) = when (action) {
        "GetPositionInfo" -> "<Track>1</Track><TrackDuration>0:01:40</TrackDuration><RelTime>0:00:42</RelTime>"
        "GetTransportInfo" -> "<CurrentTransportState>PLAYING</CurrentTransportState><CurrentTransportStatus>OK</CurrentTransportStatus>"
        else -> ""
    }

    private fun envelope(inner: String) =
        "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>$inner</s:Body></s:Envelope>"

    override fun close() = http.stop(0)

    companion object {
        val DESCRIPTION = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <specVersion><major>1</major><minor>0</minor></specVersion>
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>Living Room TV</friendlyName>
                <manufacturer>Test Co</manufacturer>
                <modelName>TV-55</modelName>
                <UDN>uuid:1234-abcd</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>/ctl/rc</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/ctl/avt</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()
    }
}

/** Answers one SSDP M-SEARCH with a reply pointing at [location]; records the request. */
class FakeSsdpResponder(private val location: String) : AutoCloseable {
    private val socket = DatagramSocket(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
    val address: InetSocketAddress get() = InetSocketAddress(InetAddress.getLoopbackAddress(), socket.localPort)
    @Volatile var lastRequest: String = ""

    private val thread = Thread {
        val buf = ByteArray(2048)
        while (!socket.isClosed) {
            val p = DatagramPacket(buf, buf.size)
            runCatching { socket.receive(p) }.onFailure { return@Thread }
            lastRequest = String(p.data, 0, p.length)
            val reply = ("HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nEXT:\r\nLOCATION: $location\r\n" +
                "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n" +
                "USN: uuid:1234-abcd::urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n").toByteArray()
            socket.send(DatagramPacket(reply, reply.size, p.socketAddress))
        }
    }.apply { isDaemon = true; start() }

    override fun close() = socket.close()
}
