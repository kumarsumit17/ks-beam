package com.ks.beam.core

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

/** A DLNA media renderer: a TV, projector or speaker that can play an address it is given. */
data class Renderer(
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    /** Absolute control URL of the AVTransport service. */
    val avTransportUrl: String,
    /** The exact AVTransport service type the device declared (`…:AVTransport:1` or `:2`). */
    val avTransportType: String,
) {
    /** The renderer's host, used to pick the local interface that reaches it. */
    val host: String get() = URI(avTransportUrl).host
}

object DeviceDescription {
    /**
     * Parses a UPnP device description fetched from [location]. Returns null when the device has
     * no AVTransport service (specs/casting.md, section 1).
     */
    fun parse(xml: ByteArray, location: String): Renderer? {
        val doc = safeXml().newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val root = doc.documentElement
        val base = root.firstText("URLBase")?.takeIf { it.isNotBlank() } ?: location
        val device = root.getElementsByTagNameNS("*", "device").item(0) as? Element ?: return null

        val services = root.getElementsByTagNameNS("*", "service")
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val type = service.firstText("serviceType").orEmpty()
            if (!type.contains(":service:AVTransport:")) continue
            val control = service.firstText("controlURL") ?: continue
            return Renderer(
                id = device.firstText("UDN").orEmpty().ifEmpty { location },
                name = device.firstText("friendlyName").orEmpty().ifEmpty { URI(location).host },
                manufacturer = device.firstText("manufacturer").orEmpty(),
                model = device.firstText("modelName").orEmpty(),
                avTransportUrl = URL(URL(base), control).toString(),
                avTransportType = type,
            )
        }
        return null
    }

    /** Fetches and parses the description at [location]; null on any failure. */
    fun fetch(location: String, timeoutMs: Int = 3000): Renderer? = runCatching {
        val c = URL(location).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        try {
            if (c.responseCode != 200) return null
            parse(c.inputStream.use { it.readBytes() }, location)
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}

/** The first descendant element named [name] (any namespace), its trimmed text. */
internal fun Element.firstText(name: String): String? =
    (getElementsByTagNameNS("*", name).item(0) as? Element)?.textContent?.trim()

/** A namespace-aware parser that refuses DOCTYPEs and never expands entities. */
internal fun safeXml(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
    isExpandEntityReferences = false
    // Not every platform's parser knows every feature (Android's does not); each is best effort.
    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
}
