package com.ks.beam.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.VolumeProvider
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import com.ks.beam.ui.CastLock
import com.ks.beam.ui.CastSession
import com.ks.beam.ui.VOLUME_STEP

/**
 * Runs while a cast is active (specs/plan-per-platform.md, step 2). The phone is the TV's file
 * server for the whole cast, so this keeps it reachable with the screen off: a foreground service
 * (the process is never frozen), a partial wake lock and Wi-Fi locks (no power-save stalls). Its
 * notification and the platform MediaSession give the lock screen, quick settings and headset
 * buttons play/pause, ±10 s, seeking and stop; every command goes to the cast through
 * [CastSession]. No dependency beyond the Android framework.
 */
class CastService : Service() {
    private lateinit var media: MediaSession
    private val locks = mutableListOf<() -> Unit>()
    private var foreground = false
    private var shown: List<Any?>? = null
    private var volume: VolumeProvider? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        media = MediaSession(this, "ks-beam-cast").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { session?.let { if (!it.playing) it.togglePlay() } }
                override fun onPause() { session?.let { if (it.playing) it.togglePlay() } }
                override fun onStop() { session?.stop() }
                override fun onSeekTo(pos: Long) { session?.seekTo(pos / 1000) }
                override fun onFastForward() { session?.seekBy(SEEK_STEP) }
                override fun onRewind() { session?.seekBy(-SEEK_STEP) }
                override fun onCustomAction(action: String, extras: Bundle?) = command(action)
            })
            // The TV plays, not this phone: a remote session, so the system's output chip and media
            // controls treat it as casting. Its volume is set in update(), from the TV's.
            isActive = true
        }
        holdLocks()
        running = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let(::command)
        update()
        if (session == null) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = null
        locks.forEach { it() }
        locks.clear()
        media.isActive = false
        media.release()
        super.onDestroy()
    }

    private fun command(action: String) {
        val s = session ?: return
        when (action) {
            ACTION_TOGGLE -> s.togglePlay()
            ACTION_BACK -> s.seekBy(-SEEK_STEP)
            ACTION_FORWARD -> s.seekBy(SEEK_STEP)
            ACTION_STOP -> s.stop()
        }
    }

    private fun holdLocks() {
        val power = getSystemService(PowerManager::class.java)
        power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KSBeam:cast").apply {
            // Released when the cast ends; the cap only guards against a cast that never ends.
            setReferenceCounted(false); acquire(WAKE_LOCK_CAP_MS)
            locks += { if (isHeld) release() }
        }
        val wifi = applicationContext.getSystemService(WifiManager::class.java)
        // High-performance keeps Wi-Fi out of power save with the screen off; low latency (Android
        // 10+) helps while the app is on screen. Both are released when the cast ends.
        @Suppress("DEPRECATION")
        val modes = buildList {
            add(WifiManager.WIFI_MODE_FULL_HIGH_PERF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(WifiManager.WIFI_MODE_FULL_LOW_LATENCY)
        }
        for (mode in modes) {
            runCatching {
                wifi.createWifiLock(mode, "KSBeam:cast:$mode").apply {
                    setReferenceCounted(false); acquire()
                    locks += { if (isHeld) release() }
                }
            }
        }
    }

    /** Shows the session's current state in the notification and the MediaSession. */
    fun update() {
        val s = session
        updateVolume(s)
        val labels = s?.labels
        val channel = labels?.channel?.ifBlank { null } ?: applicationInfo.loadLabel(packageManager).toString()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, channel, NotificationManager.IMPORTANCE_LOW))

        val playing = s?.playing == true
        val positionMs = (s?.position?.seconds ?: 0) * 1000
        val durationMs = (s?.position?.durationSeconds ?: 0) * 1000
        media.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, s?.title.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, labels?.castingTo.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, s?.rendererName.orEmpty())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .build(),
        )
        media.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND,
                )
                // Android 13+ shows custom actions in the media controls' side slots.
                .addCustomAction(ACTION_BACK, labels?.back.orEmpty(), android.R.drawable.ic_media_rew)
                .addCustomAction(ACTION_FORWARD, labels?.forward.orEmpty(), android.R.drawable.ic_media_ff)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs, if (playing) 1f else 0f, SystemClock.elapsedRealtime(),
                )
                .build(),
        )

        // The position alone changes every second: the session above has it, so the notification is
        // rebuilt only when what it shows changes.
        val key = listOf(s?.title, playing, labels, durationMs > 0)
        if (foreground && key == shown) return
        shown = key

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(icon: Int, label: String?, command: String) = Notification.Action.Builder(
            Icon.createWithResource(this, icon), label.orEmpty(),
            PendingIntent.getService(
                this, command.hashCode(), Intent(this, CastService::class.java).setAction(command),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(com.ks.beam.shared.R.drawable.ic_notification)
            .setContentTitle(s?.title.orEmpty())
            .setContentText(labels?.castingTo.orEmpty())
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(android.R.drawable.ic_media_rew, labels?.back, ACTION_BACK))
            .addAction(
                if (playing) action(android.R.drawable.ic_media_pause, labels?.pause, ACTION_TOGGLE)
                else action(android.R.drawable.ic_media_play, labels?.play, ACTION_TOGGLE),
            )
            .addAction(action(android.R.drawable.ic_media_ff, labels?.forward, ACTION_FORWARD))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, labels?.stop, ACTION_STOP))
            .setStyle(Notification.MediaStyle().setMediaSession(media.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()

        if (!foreground) {
            foreground = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    /**
     * The phone's volume buttons and the system volume panel set the TV's own volume while this
     * session is the active remote one (RenderingControl); a TV without it keeps a fixed volume.
     */
    private fun updateVolume(s: CastSession?) {
        val level = s?.volume
        val p = volume
        if (level == null) {
            if (p == null || p.volumeControl != VolumeProvider.VOLUME_CONTROL_FIXED) {
                volume = object : VolumeProvider(VOLUME_CONTROL_FIXED, 100, 100) {}.also(media::setPlaybackToRemote)
            }
            return
        }
        if (p != null && p.volumeControl == VolumeProvider.VOLUME_CONTROL_ABSOLUTE) {
            if (p.currentVolume != level) p.currentVolume = level
            return
        }
        volume = object : VolumeProvider(VOLUME_CONTROL_ABSOLUTE, 100, level) {
            override fun onSetVolumeTo(volume: Int) { session?.setVolume(volume) }
            override fun onAdjustVolume(direction: Int) {
                val current = session?.volume ?: return
                session?.setVolume(current + direction * VOLUME_STEP)
            }
        }.also(media::setPlaybackToRemote)
    }

    companion object {
        private const val CHANNEL = "cast"
        private const val NOTIFICATION_ID = 1
        private const val SEEK_STEP = 10L
        private const val WAKE_LOCK_CAP_MS = 6 * 60 * 60 * 1000L
        private const val ACTION_TOGGLE = "com.ks.beam.cast.TOGGLE"
        private const val ACTION_BACK = "com.ks.beam.cast.BACK"
        private const val ACTION_FORWARD = "com.ks.beam.cast.FORWARD"
        private const val ACTION_STOP = "com.ks.beam.cast.STOP"

        /** The running cast; set and read on the main thread. */
        private var session: CastSession? = null
        private var running: CastService? = null

        /** Starts the service for [cast]; the returned lock updates it and stops it. */
        fun lock(context: Context, cast: CastSession): CastLock {
            session = cast
            val app = context.applicationContext
            app.startForegroundService(Intent(app, CastService::class.java))
            return object : CastLock {
                override fun changed() { if (session === cast) running?.update() }
                override fun close() {
                    if (session !== cast) return
                    session = null
                    app.stopService(Intent(app, CastService::class.java))
                }
            }
        }
    }
}
