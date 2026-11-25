package com.ks.beam.ui

import androidx.compose.runtime.Composable
import com.ks.beam.core.BytesMedia
import com.ks.beam.core.DeviceDescription
import com.ks.beam.core.FakeRenderer
import com.ks.beam.core.MediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A cast takes the platform's lock once, reports changes to it and closes it when it stops. */
class CastLockTest {
    private class TestPlatform : BeamPlatform {
        val taken = AtomicInteger()
        val changes = AtomicInteger()
        val closed = AtomicInteger()
        var session: CastSession? = null
        private val values = mutableMapOf<String, String>()
        override val settings = object : SettingsStore {
            override fun getString(key: String) = values[key]
            override fun putString(key: String, value: String) { values[key] = value }
        }
        override val systemLanguages = listOf("en")
        override suspend fun pickMedia(): MediaSource = BytesMedia("clip.mp4", ByteArray(4096))
        override fun castLock(session: CastSession): CastLock {
            taken.incrementAndGet()
            this.session = session
            return object : CastLock {
                override fun changed() { changes.incrementAndGet() }
                override fun close() { closed.incrementAndGet() }
            }
        }
        @Composable override fun ProvideLanguage(language: String, content: @Composable () -> Unit) = content()
    }

    private fun waitFor(check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) { if (check()) return true; Thread.sleep(50) }
        return false
    }

    @Test fun castHoldsTheLockUntilStop() {
        FakeRenderer().use { tv ->
            val platform = TestPlatform()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val state = BeamState(platform, scope)
                state.selected = assertNotNull(DeviceDescription.fetch(tv.location))
                state.chooseFileAndCast()
                assertTrue(waitFor { state.cast != null }, "cast did not start: ${state.error}")
                assertEquals(1, platform.taken.get())
                assertEquals(0, platform.closed.get())

                val session = assertNotNull(platform.session)
                assertEquals("clip.mp4", session.title)
                assertEquals("Living Room TV", session.rendererName)
                assertTrue(waitFor { platform.changes.get() > 0 }, "no change reported while polling")

                session.togglePlay() // what the OS controls call
                assertTrue(waitFor { tv.actions.any { it.first == "Pause" } }, "pause from the session did not reach the TV")

                session.stop()
                assertEquals(1, platform.closed.get())
                assertEquals(null, state.cast)
            } finally {
                scope.cancel()
            }
        }
    }
}
