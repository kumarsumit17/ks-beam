package com.ks.beam.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ks.beam.core.receiver.PlaybackTarget
import com.ks.beam.core.receiver.TransportState

/** What the TV shows: nothing (the ready screen), a video or song, a photo, or why it can't play. */
sealed interface NowShowing {
    data class Media(val title: String) : NowShowing
    data class Photo(val url: String, val title: String) : NowShowing
    data class CannotPlay(val title: String) : NowShowing
}

/**
 * The receiver's player: Media3 for video and audio, an image view for photos. Network threads call
 * in; every player call is posted to the main thread, and the state they read is cached here.
 * Create it on the main thread.
 */
class ExoTarget(context: Context, private val onShow: (NowShowing?) -> Unit) : PlaybackTarget {
    private val main = Handler(Looper.getMainLooper())
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    @Volatile private var cachedPosition = 0L
    @Volatile private var cachedDuration = 0L
    @Volatile private var cachedState = TransportState.NO_MEDIA_PRESENT
    @Volatile private var photo: NowShowing.Photo? = null
    @Volatile private var photoPlaying = false
    @Volatile private var title = ""

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, 500)
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = refresh()
            override fun onPlayerError(error: PlaybackException) {
                onShow(NowShowing.CannotPlay(title))
                refresh()
            }
        })
        main.post(ticker)
    }

    private fun refresh() {
        if (photo != null) {
            cachedPosition = 0; cachedDuration = 0
            cachedState = if (photoPlaying) TransportState.PLAYING else TransportState.STOPPED
            return
        }
        cachedPosition = player.currentPosition.coerceAtLeast(0)
        cachedDuration = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
        cachedState = when {
            player.mediaItemCount == 0 -> TransportState.NO_MEDIA_PRESENT
            player.playerError != null -> TransportState.STOPPED
            player.playbackState == Player.STATE_BUFFERING -> TransportState.TRANSITIONING
            player.playbackState == Player.STATE_READY && player.playWhenReady -> TransportState.PLAYING
            player.playbackState == Player.STATE_READY -> TransportState.PAUSED_PLAYBACK
            else -> TransportState.STOPPED // IDLE (loaded, not started) or ENDED
        }
    }

    private fun onMain(block: () -> Unit) { main.post { block(); refresh() } }

    override fun load(url: String, title: String, mimeType: String?) = onMain {
        this.title = title
        if (mimeType?.startsWith("image/") == true) {
            player.stop()
            player.clearMediaItems()
            photoPlaying = false
            photo = NowShowing.Photo(url, title).also(onShow)
        } else {
            photo = null
            val item = MediaItem.Builder()
                .setUri(url)
                .apply { if (mimeType != null) setMimeType(mimeType) }
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
            player.setMediaItem(item)
            player.playWhenReady = false
            player.prepare()
            onShow(NowShowing.Media(title))
        }
    }

    override fun play() = onMain {
        if (photo != null) photoPlaying = true
        else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    override fun pause() = onMain { if (photo == null) player.pause() }

    override fun stop() = onMain {
        photo = null
        photoPlaying = false
        player.stop()
        player.clearMediaItems()
        onShow(null)
    }

    override fun seek(positionMs: Long) = onMain { if (photo == null) player.seekTo(positionMs.coerceAtLeast(0)) }

    override val positionMs: Long get() = cachedPosition
    override val durationMs: Long get() = cachedDuration
    override val state: TransportState get() = cachedState

    @Volatile private var level = 100
    @Volatile private var silent = false
    override var volume: Int
        get() = level
        set(value) { level = value.coerceIn(0, 100); onMain { player.volume = if (silent) 0f else level / 100f } }
    override var muted: Boolean
        get() = silent
        set(value) { silent = value; onMain { player.volume = if (silent) 0f else level / 100f } }

    fun release() {
        main.removeCallbacks(ticker)
        player.release()
    }
}
