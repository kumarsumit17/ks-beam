package com.ks.beam.windows

import com.ks.beam.desktop.OpenedFiles
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A second launch hands its file to the running copy (plain loopback sockets: runs on any OS). */
class SingleInstanceTest {
    @Test fun aSecondLaunchHandsItsFileOver() {
        SingleInstance.listen()
        val file = File.createTempFile("ks-beam", ".mp4").apply { deleteOnExit() }
        assertTrue(SingleInstance.handOver(listOf(file)), "the running copy did not take the file")
        try {
            val got = runBlocking { withTimeout(3000) { OpenedFiles.nextForTest() } }
            assertEquals(file.absolutePath, got?.absolutePath)
        } finally {
            Preferences.userRoot().node("com/ks/beam/instance").removeNode() // the test's port and token
        }
    }
}
