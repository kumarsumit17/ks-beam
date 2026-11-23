package com.ks.beam.core

import com.ks.beam.desktop.FileMediaSource
import java.io.File
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Casts a real file to a running receiver, for example KS Beam on the Google TV emulator:
 *   adb forward tcp:49152 tcp:<receiver port>   (the port is in logcat: "receiver ready at …")
 *   KS_BEAM_RENDERER=http://127.0.0.1:49152/desc.xml KS_BEAM_MEDIA=clip.mp4 KS_BEAM_MEDIA_HOST=10.0.2.2 \
 *     ./gradlew :shared:desktopTest --tests '*EmulatorCastTest*'
 * Skipped when KS_BEAM_RENDERER is not set.
 */
class EmulatorCastTest {
    @Test fun castsToARealReceiver() {
        val location = System.getenv("KS_BEAM_RENDERER") ?: return
        val media = FileMediaSource(File(assertNotNull(System.getenv("KS_BEAM_MEDIA"))))
        val host = System.getenv("KS_BEAM_MEDIA_HOST") ?: InetAddress.getLocalHost().hostAddress
        val renderer = assertNotNull(DeviceDescription.fetch(location), "no receiver at $location")
        println("receiver: ${renderer.name} (${renderer.manufacturer} ${renderer.model})")
        Cast(renderer, media, serverFor = { MediaServer(InetAddress.getByName("0.0.0.0"), host) }).use { cast ->
            cast.start()
            assertTrue(waitFor { cast.state() == "PLAYING" && cast.position().seconds >= 2 }, "playing, state ${cast.state()}")
            val playing = cast.position()
            println("playing at ${playing.seconds}s of ${playing.durationSeconds}s")
            assertTrue(playing.durationSeconds > 0)
            Thread.sleep(System.getenv("KS_BEAM_HOLD_MS")?.toLongOrNull() ?: 3000)
            cast.pause()
            assertTrue(waitFor { cast.state() == "PAUSED_PLAYBACK" }, "paused, state ${cast.state()}")
            cast.seek(10)
            assertTrue(waitFor { cast.position().seconds in 9..11 }, "seeked to 10s, at ${cast.position().seconds}s")
            cast.play()
            assertTrue(waitFor { cast.state() == "PLAYING" })
            println("pause, seek to 10s and play again: OK")
        }
        assertTrue(waitFor { AvTransport(renderer).state() in setOf("STOPPED", "NO_MEDIA_PRESENT") })
    }

    private fun waitFor(timeoutMs: Long = 15_000, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (runCatching(check).getOrDefault(false)) return true
            Thread.sleep(300)
        }
        return false
    }
}
