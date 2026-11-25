package com.ks.beam.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.provider.Settings
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaSession

/**
 * The receiver, kept running in the background from the moment KS Beam is opened until the TV
 * restarts (the owner's choice, specs/plan-per-platform.md, step 3). It owns the player
 * ([ExoTarget]), the DLNA receiver (specs/receiver.md) and a Media3 session, so the remote's media
 * keys, Google Assistant and the TV's now-playing card reach the player. [ReceiverActivity] only
 * shows its state; when a cast arrives while another app is on screen, the service brings it forward.
 */
class ReceiverService : Service() {
    private var receiver: AutoCloseable? = null
    private var session: MediaSession? = null
    @Volatile private var stopped = false

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val t = ExoTarget(this) { now ->
            showing = now
            if (now != null && !visible) bringToFront()
        }
        target = t
        // Without "Display over other apps" KS Beam cannot come forward from the background; a
        // video that starts then waits, paused, until KS Beam is opened (the sender shows paused).
        t.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val p = t.player
                if (isPlaying && !visible && p.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO) && !Settings.canDrawOverlays(this@ReceiverService)) {
                    p.pause()
                    held = true
                }
            }
        })
        session = MediaSession.Builder(this, t.player).setSessionActivity(openActivity()).build()
        goForeground()
        Thread({
            val started = startReceiver(this, tvName(this), t)
            if (stopped) started?.close() else {
                receiver = started
                Handler(Looper.getMainLooper()).post { online = started != null }
            }
        }, "ks-beam-receiver-start").start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        // Started again only by the activity; if the system ends the service it stays ended until
        // KS Beam is opened, as decided (no start at boot).
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopped = true
        running = null
        receiver?.let { r -> Thread { r.close() }.start() }
        receiver = null
        session?.release()
        target?.release()
        target = null
        showing = null
        super.onDestroy()
    }

    private fun openActivity(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, ReceiverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun bringToFront() {
        // Allowed from a foreground service only where the system permits it; otherwise the cast
        // still plays its sound and shows once KS Beam is opened.
        runCatching { startActivity(Intent(this, ReceiverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            .onFailure { Log.w("KSBeam", "could not bring the receiver forward", it) }
    }

    private fun goForeground() {
        running = this
        val manager = getSystemService(NotificationManager::class.java)
        val title = readyLabel.ifBlank { applicationInfo.loadLabel(packageManager).toString() }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, title, NotificationManager.IMPORTANCE_MIN))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(com.ks.beam.shared.R.drawable.ic_notification)
            .setContentTitle(applicationInfo.loadLabel(packageManager).toString())
            .setContentText(readyLabel)
            .setContentIntent(openActivity())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "receiver"
        private const val NOTIFICATION_ID = 1
        private var running: ReceiverService? = null

        /** The player, while the service runs; read by the activity on the main thread. */
        var target by mutableStateOf<ExoTarget?>(null)
            private set
        /** What the TV shows: the ready screen (null), a video or song, a photo, or why it can't play. */
        var showing by mutableStateOf<NowShowing?>(null)
            private set
        /** False when the TV has no network address to receive on. */
        var online by mutableStateOf(true)
            private set
        /** Whether [ReceiverActivity] is on screen. */
        var visible = false

        /** A video paused because KS Beam was not on screen and could not come forward. */
        private var held = false

        /** KS Beam is on screen again: a held video plays. */
        fun resumeHeld() {
            if (!held) return
            held = false
            target?.player?.play()
        }

        /** "Ready to receive" in the app's language, for the service's notification. */
        var readyLabel: String = ""
            set(value) {
                if (field == value) return
                field = value
                running?.goForeground()
            }

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ReceiverService::class.java))
        }
    }
}
