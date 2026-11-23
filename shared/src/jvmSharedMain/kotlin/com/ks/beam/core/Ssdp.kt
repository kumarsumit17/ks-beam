package com.ks.beam.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/** One SSDP search reply: the headers that matter (specs/casting.md, section 1). */
data class SsdpResponse(val location: String, val usn: String, val st: String) {
    /** The device part of the USN (`uuid:…`), which several replies for one device share. */
    val deviceId: String get() = usn.substringBefore("::")

    companion object {
        /** Parses a reply; null if it is not `HTTP/1.1 200` or has no LOCATION. */
        fun parse(text: String): SsdpResponse? {
            val lines = text.split("\r\n", "\n")
            if (!lines.first().trim().startsWith("HTTP/1.1 200", ignoreCase = true)) return null
            val headers = lines.drop(1).mapNotNull { line ->
                val i = line.indexOf(':')
                if (i <= 0) null else line.substring(0, i).trim().uppercase() to line.substring(i + 1).trim()
            }.toMap()
            val location = headers["LOCATION"]?.takeIf { it.startsWith("http://") } ?: return null
            return SsdpResponse(location, headers["USN"].orEmpty(), headers["ST"].orEmpty())
        }
    }
}

/**
 * Sends SSDP M-SEARCH requests and collects the replies. [target] is the multicast group; tests
 * point it at a local socket. [interfaceAddresses] lists the local addresses to search from.
 */
class SsdpDiscovery(
    private val target: InetSocketAddress = InetSocketAddress(InetAddress.getByName("239.255.255.250"), 1900),
    private val interfaceAddresses: () -> List<InetAddress> = ::lanAddresses,
) {
    fun search(
        searchTarget: String = AV_TRANSPORT_1,
        timeoutMs: Int = 3000,
        onReply: (SsdpResponse) -> Unit,
    ) {
        val request = (
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: $searchTarget\r\n" +
                "USER-AGENT: KS-Beam/1 UPnP/1.1\r\n\r\n"
            ).toByteArray(Charsets.US_ASCII)

        val sockets = interfaceAddresses().mapNotNull { address ->
            runCatching { DatagramSocket(InetSocketAddress(address, 0)) }.getOrNull()
        }.ifEmpty { listOf(DatagramSocket()) }
        try {
            repeat(2) {
                sockets.forEach { s -> runCatching { s.send(DatagramPacket(request, request.size, target)) } }
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(8192)
            // Round-robin over the sockets with short timeouts until the deadline.
            while (System.currentTimeMillis() < deadline) {
                for (s in sockets) {
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0) break
                    s.soTimeout = minOf(left, 100L).toInt().coerceAtLeast(1)
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        s.receive(packet)
                        SsdpResponse.parse(String(packet.data, 0, packet.length, Charsets.UTF_8))?.let(onReply)
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        } finally {
            sockets.forEach { it.close() }
        }
    }

    companion object {
        const val AV_TRANSPORT_1 = "urn:schemas-upnp-org:service:AVTransport:1"

        /** IPv4 addresses of interfaces that are up, not loopback, and support multicast. */
        fun lanAddresses(): List<InetAddress> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && it.supportsMulticast() && !it.isVirtual }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
        }.getOrDefault(emptyList())
    }
}
