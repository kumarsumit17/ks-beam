package com.ks.beam.core.receiver

import com.ks.beam.core.SsdpDiscovery
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketAddress
import java.net.SocketTimeoutException
import kotlin.random.Random

/**
 * Makes a receiver findable: answers SSDP M-SEARCH requests and sends `ssdp:alive` / `ssdp:byebye`
 * NOTIFYs (specs/receiver.md, section 1). [port] is 1900 on a network; tests use 0 and send to it directly.
 */
class SsdpAdvertiser(
    private val location: String,
    private val udn: String,
    private val port: Int = 1900,
    private val group: InetAddress = InetAddress.getByName("239.255.255.250"),
    private val interfaceAddresses: () -> List<InetAddress> = SsdpDiscovery::lanAddresses,
) : AutoCloseable {
    private val socket = MulticastSocket(null as SocketAddress?).apply {
        reuseAddress = true
        bind(InetSocketAddress(this@SsdpAdvertiser.port))
        soTimeout = 1000
    }
    @Volatile private var running = true

    /** The local port (useful when [port] is 0). */
    val localPort: Int get() = socket.localPort

    /** Every target a search can name, with the USN to answer it with. */
    private val targets: List<Pair<String, String>> = listOf(
        "upnp:rootdevice" to "$udn::upnp:rootdevice",
        udn to udn,
        RendererServer.DEVICE_TYPE to "$udn::${RendererServer.DEVICE_TYPE}",
        RendererServer.AVT to "$udn::${RendererServer.AVT}",
        RendererServer.CM to "$udn::${RendererServer.CM}",
        RendererServer.RC to "$udn::${RendererServer.RC}",
    )

    private val thread = Thread(::loop, "ks-beam-ssdp").apply { isDaemon = true }

    init {
        for (address in interfaceAddresses()) {
            runCatching { socket.joinGroup(InetSocketAddress(group, 1900), NetworkInterface.getByInetAddress(address)) }
        }
        thread.start()
    }

    private fun loop() {
        val buffer = ByteArray(4096)
        var nextNotify = 0L
        while (running) {
            if (System.currentTimeMillis() >= nextNotify) {
                notifyAll("ssdp:alive")
                nextNotify = System.currentTimeMillis() + 60_000
            }
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                break
            }
            val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
            if (!text.startsWith("M-SEARCH", ignoreCase = true)) continue
            val headers = text.split("\r\n").drop(1).mapNotNull { line ->
                val i = line.indexOf(':')
                if (i <= 0) null else line.substring(0, i).trim().uppercase() to line.substring(i + 1).trim()
            }.toMap()
            if (!headers["MAN"].orEmpty().contains("ssdp:discover")) continue
            val st = headers["ST"].orEmpty()
            val matches = if (st == "ssdp:all") targets else targets.filter { it.first == st }
            if (matches.isEmpty()) continue
            // MX asks for a random delay of up to that many seconds; a short one keeps searches quick.
            val mx = headers["MX"]?.toIntOrNull()?.coerceIn(0, 5) ?: 1
            Thread.sleep(Random.nextLong(0, (mx * 200L).coerceAtLeast(1)))
            for ((target, usn) in matches) {
                val reply = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nEXT:\r\nLOCATION: $location\r\n" +
                    "SERVER: KS-Beam/1 UPnP/1.1\r\nST: $target\r\nUSN: $usn\r\n\r\n"
                send(reply, packet.socketAddress)
            }
        }
    }

    private fun notifyAll(nts: String) {
        if (port == 0) return // Tests: no multicast announcements.
        val to = InetSocketAddress(group, 1900)
        for ((target, usn) in targets) {
            val message = "NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nCACHE-CONTROL: max-age=1800\r\n" +
                "LOCATION: $location\r\nNT: $target\r\nNTS: $nts\r\nSERVER: KS-Beam/1 UPnP/1.1\r\nUSN: $usn\r\n\r\n"
            send(message, to)
        }
    }

    private fun send(text: String, to: SocketAddress) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        runCatching { socket.send(DatagramPacket(bytes, bytes.size, to)) }
    }

    override fun close() {
        running = false
        notifyAll("ssdp:byebye")
        socket.close()
    }
}

/** A running receiver: the control server plus its SSDP announcements. */
class Receiver(name: String, udn: String, target: PlaybackTarget, bindAddress: InetAddress, ssdpPort: Int = 1900) : AutoCloseable {
    val server = RendererServer(name, udn, target, bindAddress)
    val advertiser = SsdpAdvertiser(server.location, udn, ssdpPort)

    override fun close() {
        advertiser.close()
        server.close()
    }
}
