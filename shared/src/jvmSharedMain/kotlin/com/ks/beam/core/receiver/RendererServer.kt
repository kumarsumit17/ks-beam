package com.ks.beam.core.receiver

import com.ks.beam.core.AvTransport
import com.ks.beam.core.HttpRequest
import com.ks.beam.core.Mime
import com.ks.beam.core.firstText
import com.ks.beam.core.respond
import com.ks.beam.core.safeXml
import com.ks.beam.core.xmlEscape
import org.w3c.dom.Element
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors

enum class TransportState { NO_MEDIA_PRESENT, STOPPED, PLAYING, PAUSED_PLAYBACK, TRANSITIONING }

/**
 * The player a receiver drives (specs/receiver.md). Called from network threads; implementations
 * hop to their own thread when the player needs it (Media3: the main thread).
 */
interface PlaybackTarget {
    fun load(url: String, title: String, mimeType: String?)
    fun play()
    fun pause()
    fun stop()
    fun seek(positionMs: Long)
    val positionMs: Long
    val durationMs: Long
    val state: TransportState
    /** 0 to 100. */
    var volume: Int
    var muted: Boolean
}

/**
 * A DLNA MediaRenderer's description and control endpoints over HTTP: AVTransport, ConnectionManager
 * and RenderingControl (specs/receiver.md, section 2).
 */
class RendererServer(
    private val friendlyName: String,
    private val udn: String,
    private val target: PlaybackTarget,
    bindAddress: InetAddress,
) : AutoCloseable {
    // Listens on every interface (a TV may have Wi-Fi and Ethernet, an emulator is reached through
    // adb forward on localhost) and announces [bindAddress] as its location.
    private val server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(0)) }
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "ks-beam-renderer").apply { isDaemon = true } }
    @Volatile private var closed = false
    @Volatile private var currentUri = ""
    @Volatile private var currentMetadata = ""

    val port: Int get() = server.localPort
    val location: String = "http://${bindAddress.hostAddress}:${server.localPort}/desc.xml"

    init {
        pool.execute {
            while (!closed) {
                val socket = try { server.accept() } catch (_: SocketException) { break }
                pool.execute { socket.use(::handle) }
            }
        }
    }

    override fun close() {
        closed = true
        runCatching { server.close() }
        pool.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val input = BufferedInputStream(socket.getInputStream())
        val out = socket.getOutputStream()
        val request = HttpRequest.read(input) ?: return
        when {
            request.method == "GET" && request.path == "/desc.xml" -> out.xml(description())
            request.method == "GET" && request.path == "/avt.xml" -> out.xml(Scpd.AV_TRANSPORT)
            request.method == "GET" && request.path == "/cm.xml" -> out.xml(Scpd.CONNECTION_MANAGER)
            request.method == "GET" && request.path == "/rc.xml" -> out.xml(Scpd.RENDERING_CONTROL)
            request.method == "POST" && request.path in SERVICES -> control(request, request.body(input), out)
            request.method == "SUBSCRIBE" || request.method == "UNSUBSCRIBE" ->
                out.respond(501, "Not Implemented") // Eventing is not offered; senders poll.
            else -> out.respond(404, "Not Found")
        }
    }

    private fun control(request: HttpRequest, body: ByteArray, out: OutputStream) {
        val serviceType = SERVICES.getValue(request.path)
        val action = request.headers["soapaction"].orEmpty().trim('"').substringAfter('#')
        val args = runCatching { arguments(body, action) }.getOrNull()
            ?: return out.fault(402, "Invalid Args")
        val result: List<Pair<String, String>> = try {
            when (request.path) {
                "/avt/control" -> avTransport(action, args)
                "/cm/control" -> connectionManager(action)
                else -> renderingControl(action, args)
            } ?: return out.fault(401, "Invalid Action")
        } catch (e: IllegalArgumentException) {
            return out.fault(402, "Invalid Args")
        }
        val response = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
            append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
            append("<u:${action}Response xmlns:u=\"$serviceType\">")
            for ((k, v) in result) append("<$k>${xmlEscape(v)}</$k>")
            append("</u:${action}Response></s:Body></s:Envelope>")
        }
        out.respond(200, "OK", "text/xml; charset=\"utf-8\"", response.toByteArray(Charsets.UTF_8))
    }

    private fun avTransport(action: String, a: Map<String, String>): List<Pair<String, String>>? = when (action) {
        "SetAVTransportURI" -> {
            val uri = a["CurrentURI"]?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: throw IllegalArgumentException("CurrentURI")
            val metadata = a["CurrentURIMetaData"].orEmpty()
            currentUri = uri
            currentMetadata = metadata
            val (title, mime) = Metadata.titleAndMime(metadata)
            target.load(uri, title ?: uri.substringAfterLast('/').substringBefore('?'), mime ?: Mime.forName(uri))
            emptyList()
        }
        "Play" -> { target.play(); emptyList() }
        "Pause" -> { target.pause(); emptyList() }
        "Stop" -> { target.stop(); emptyList() }
        "Seek" -> {
            val unit = a["Unit"].orEmpty()
            if (unit != "REL_TIME" && unit != "ABS_TIME") throw IllegalArgumentException("Unit")
            target.seek(AvTransport.parseTime(a["Target"]) * 1000)
            emptyList()
        }
        "GetPositionInfo" -> {
            val duration = AvTransport.formatTime(target.durationMs / 1000)
            val position = AvTransport.formatTime(target.positionMs / 1000)
            listOf(
                "Track" to if (currentUri.isEmpty()) "0" else "1", "TrackDuration" to duration,
                "TrackMetaData" to currentMetadata, "TrackURI" to currentUri,
                "RelTime" to position, "AbsTime" to position, "RelCount" to "2147483647", "AbsCount" to "2147483647",
            )
        }
        "GetTransportInfo" -> listOf(
            "CurrentTransportState" to target.state.name, "CurrentTransportStatus" to "OK", "CurrentSpeed" to "1",
        )
        "GetMediaInfo" -> listOf(
            "NrTracks" to if (currentUri.isEmpty()) "0" else "1",
            "MediaDuration" to AvTransport.formatTime(target.durationMs / 1000),
            "CurrentURI" to currentUri, "CurrentURIMetaData" to currentMetadata, "NextURI" to "", "NextURIMetaData" to "",
            "PlayMedium" to "NETWORK", "RecordMedium" to "NOT_IMPLEMENTED", "WriteStatus" to "NOT_IMPLEMENTED",
        )
        "GetTransportSettings" -> listOf("PlayMode" to "NORMAL", "RecQualityMode" to "NOT_IMPLEMENTED")
        "GetDeviceCapabilities" -> listOf(
            "PlayMedia" to "NETWORK", "RecMedia" to "NOT_IMPLEMENTED", "RecQualityModes" to "NOT_IMPLEMENTED",
        )
        "GetCurrentTransportActions" -> listOf("Actions" to "Play,Pause,Stop,Seek")
        else -> null
    }

    private fun connectionManager(action: String): List<Pair<String, String>>? = when (action) {
        "GetProtocolInfo" -> listOf("Source" to "", "Sink" to SINK_PROTOCOLS)
        "GetCurrentConnectionIDs" -> listOf("ConnectionIDs" to "0")
        "GetCurrentConnectionInfo" -> listOf(
            "RcsID" to "0", "AVTransportID" to "0", "ProtocolInfo" to "", "PeerConnectionManager" to "",
            "PeerConnectionID" to "-1", "Direction" to "Input", "Status" to "OK",
        )
        else -> null
    }

    private fun renderingControl(action: String, a: Map<String, String>): List<Pair<String, String>>? = when (action) {
        "GetVolume" -> listOf("CurrentVolume" to target.volume.toString())
        "SetVolume" -> {
            target.volume = a["DesiredVolume"]?.toIntOrNull()?.coerceIn(0, 100) ?: throw IllegalArgumentException("DesiredVolume")
            emptyList()
        }
        "GetMute" -> listOf("CurrentMute" to if (target.muted) "1" else "0")
        "SetMute" -> {
            target.muted = a["DesiredMute"] == "1" || a["DesiredMute"].equals("true", ignoreCase = true)
            emptyList()
        }
        else -> null
    }

    private fun arguments(body: ByteArray, action: String): Map<String, String> {
        val doc = safeXml().newDocumentBuilder().parse(ByteArrayInputStream(body))
        val element = doc.getElementsByTagNameNS("*", action).item(0) as? Element ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            out[child.localName ?: child.tagName] = child.textContent
        }
        return out
    }

    private fun description() = """<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
<specVersion><major>1</major><minor>0</minor></specVersion>
<device>
<deviceType>$DEVICE_TYPE</deviceType>
<friendlyName>${xmlEscape(friendlyName)}</friendlyName>
<manufacturer>KS-Suite</manufacturer>
<manufacturerURL>https://github.com/kumarsumit17/ks-beam</manufacturerURL>
<modelName>KS Beam</modelName>
<modelDescription>KS Beam receiver</modelDescription>
<UDN>$udn</UDN>
<dlna:X_DLNADOC>DMR-1.50</dlna:X_DLNADOC>
<serviceList>
<service><serviceType>$AVT</serviceType><serviceId>urn:upnp-org:serviceId:AVTransport</serviceId><SCPDURL>/avt.xml</SCPDURL><controlURL>/avt/control</controlURL><eventSubURL>/avt/event</eventSubURL></service>
<service><serviceType>$CM</serviceType><serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId><SCPDURL>/cm.xml</SCPDURL><controlURL>/cm/control</controlURL><eventSubURL>/cm/event</eventSubURL></service>
<service><serviceType>$RC</serviceType><serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId><SCPDURL>/rc.xml</SCPDURL><controlURL>/rc/control</controlURL><eventSubURL>/rc/event</eventSubURL></service>
</serviceList>
</device>
</root>
"""

    private fun OutputStream.xml(text: String) =
        respond(200, "OK", "text/xml; charset=\"utf-8\"", text.toByteArray(Charsets.UTF_8))

    private fun OutputStream.fault(code: Int, description: String) {
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><s:Fault><faultcode>s:Client</faultcode>" +
            "<faultstring>UPnPError</faultstring><detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\">" +
            "<errorCode>$code</errorCode><errorDescription>$description</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"
        respond(500, "Internal Server Error", "text/xml; charset=\"utf-8\"", body.toByteArray(Charsets.UTF_8))
    }

    companion object {
        const val DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1"
        const val AVT = "urn:schemas-upnp-org:service:AVTransport:1"
        const val CM = "urn:schemas-upnp-org:service:ConnectionManager:1"
        const val RC = "urn:schemas-upnp-org:service:RenderingControl:1"
        private val SERVICES = mapOf("/avt/control" to AVT, "/cm/control" to CM, "/rc/control" to RC)

        /** The formats the receiver accepts (Media3 plays all of them). */
        val SINK_PROTOCOLS = listOf(
            "video/mp4", "video/x-matroska", "video/webm", "video/quicktime", "video/mp2t", "video/x-msvideo",
            "audio/mpeg", "audio/mp4", "audio/aac", "audio/flac", "audio/wav", "audio/ogg",
            "image/jpeg", "image/png", "image/gif", "image/webp",
        ).joinToString(",") { "http-get:*:$it:*" }
    }
}

/** Reads the title and MIME type out of a sender's DIDL-Lite metadata. */
object Metadata {
    fun titleAndMime(didl: String): Pair<String?, String?> {
        if (didl.isBlank() || didl == "NOT_IMPLEMENTED") return null to null
        return runCatching {
            val doc = safeXml().newDocumentBuilder().parse(ByteArrayInputStream(didl.toByteArray(Charsets.UTF_8)))
            val root = doc.documentElement
            val title = root.firstText("title")?.takeIf { it.isNotBlank() }
            val protocol = (root.getElementsByTagNameNS("*", "res").item(0) as? Element)?.getAttribute("protocolInfo")
            val mime = protocol?.split(':')?.getOrNull(2)?.takeIf { it.contains('/') }
            title to mime
        }.getOrDefault(null to null)
    }
}
