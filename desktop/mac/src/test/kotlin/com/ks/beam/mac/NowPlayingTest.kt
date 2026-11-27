package com.ks.beam.mac

import com.ks.beam.core.Position
import com.ks.beam.ui.CastLabels
import com.ks.beam.ui.CastSession
import com.sun.jna.Pointer
import java.awt.EventQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Now Playing on macOS: what the system holds after [NowPlaying.start], and the commands macOS sends
 * (invoked here the way MPRemoteCommandCenter invokes them) reaching the cast.
 */
class NowPlayingTest {
    private class FakeSession : CastSession {
        val calls = CopyOnWriteArrayList<String>()
        override val rendererName = "Living Room TV"
        override val title = "holiday.mp4"
        override var playing = true
        override val position = Position(42, 600)
        override val labels = CastLabels()
        override val volume: Int? = null
        override fun setVolume(level: Int) {}
        override fun togglePlay() { calls += "toggle"; playing = !playing }
        override fun seekBy(delta: Long) { calls += "seekBy $delta" }
        override fun seekTo(seconds: Long) { calls += "seekTo $seconds" }
        override fun stop() { calls += "stop" }
    }

    private fun nowPlayingTitle(): String? = Objc.pool {
        val info = Objc.send(Objc.send(Objc.cls("MPNowPlayingInfoCenter"), "defaultCenter"), "nowPlayingInfo")
        if (Pointer.nativeValue(info) == 0L) return@pool null
        val title = Objc.send(info, "objectForKey:", MediaPlayer.constant("MPMediaItemPropertyTitle"))
        if (Pointer.nativeValue(title) == 0L) null else Objc.send(title, "UTF8String").getString(0)
    }

    private fun drainEvents() = EventQueue.invokeAndWait {}

    /** macOS applies Now Playing asynchronously: the value read back, once it has settled. */
    private fun settledTitle(expected: String?): String? {
        val end = System.currentTimeMillis() + 3000
        var t = nowPlayingTitle()
        while (t != expected && System.currentTimeMillis() < end) { Thread.sleep(100); t = nowPlayingTitle() }
        return t
    }

    @Test fun publishesTheCastAndTakesItsCommands() {
        if (!System.getProperty("os.name").startsWith("Mac")) return
        val session = FakeSession()
        val now = NowPlaying(session)
        now.start()
        try {
            assertEquals("holiday.mp4", settledTitle("holiday.mp4"))
            val target = Objc.target(now)
            for (selector in listOf("beamToggle:", "beamForward:", "beamBack:", "beamPause:", "beamPlay:", "beamStop:")) {
                Objc.sendVoid(target, selector, Objc.NIL)
            }
            drainEvents()
            // toggle (playing → paused), +10, −10, pause (already paused: nothing), play (toggles back), stop
            assertEquals(listOf("toggle", "seekBy 10", "seekBy -10", "toggle", "stop"), session.calls.toList())
        } finally {
            now.stop()
        }
        assertEquals(null, settledTitle(null), "Now Playing still shows the cast after stop")
    }

    @Test fun theLockUpdatesAndClears() {
        if (!System.getProperty("os.name").startsWith("Mac")) return
        val lock = NowPlaying.lock(FakeSession())
        assertTrue(lock != null, "Now Playing could not start")
        lock!!.changed()
        assertEquals("holiday.mp4", settledTitle("holiday.mp4"))
        lock.close()
        assertEquals(null, settledTitle(null))
    }
}
