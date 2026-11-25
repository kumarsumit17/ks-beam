package com.ks.beam.mac

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The power assertion appears in `pmset -g assertions` while held and disappears when released. */
class KeepAwakeTest {
    private fun assertions(): String =
        ProcessBuilder("pmset", "-g", "assertions").redirectErrorStream(true).start().inputStream.bufferedReader().readText()

    @Test fun holdsAndReleasesTheAssertion() {
        if (!System.getProperty("os.name").startsWith("Mac")) return
        val reason = "KS Beam test ${System.nanoTime()}"
        val lock = keepAwake(reason)
        try {
            assertTrue(reason in assertions(), "assertion not listed while held")
        } finally {
            lock.close()
        }
        assertFalse(reason in assertions(), "assertion still listed after close")
        lock.close() // closing twice is harmless
    }
}
