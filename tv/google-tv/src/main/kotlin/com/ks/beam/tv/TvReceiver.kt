package com.ks.beam.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.ks.beam.core.SsdpDiscovery
import com.ks.beam.core.receiver.PlaybackTarget
import com.ks.beam.core.receiver.Receiver
import java.util.UUID

/** True on Google TV and Android TV (the one APK switches to the receiver there). */
fun isTelevision(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/** The name senders show for this TV: its device name from Settings, else the model. */
fun tvName(context: Context): String =
    runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: Build.MODEL

/**
 * Starts the receiver on the TV's network address with a Wi-Fi multicast lock (specs/receiver.md).
 * Returns null when the TV has no network address. Blocking: call it off the main thread.
 */
fun startReceiver(context: Context, name: String, target: PlaybackTarget): AutoCloseable? {
    val address = SsdpDiscovery.lanAddresses().firstOrNull() ?: return null
    val prefs = context.getSharedPreferences("receiver", Context.MODE_PRIVATE)
    // A stable UDN, so senders see the same device after a restart.
    val id = prefs.getString("udn", null) ?: "uuid:${UUID.randomUUID()}".also { prefs.edit().putString("udn", it).apply() }
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val lock = wifi.createMulticastLock("ks-beam-receiver").apply { setReferenceCounted(false); acquire() }
    val receiver = try {
        Receiver(name, id, target, address)
    } catch (e: Exception) {
        lock.release()
        Log.w("KSBeam", "receiver did not start", e)
        return null
    }
    Log.i("KSBeam", "receiver ready at ${receiver.server.location}")
    return AutoCloseable {
        receiver.close()
        if (lock.isHeld) lock.release()
    }
}
