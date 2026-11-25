package com.ks.beam.windows

import com.sun.jna.Library
import com.sun.jna.Native
import java.util.concurrent.CountDownLatch

/**
 * Keeps Windows from sleeping while a cast runs: the TV reads the file from this PC, so sleep would
 * stop the film (specs/plan-per-platform.md, step 2). The display may still turn off.
 * SetThreadExecutionState through JNA. Its effect belongs to the calling thread, so one thread sets
 * it, waits for the cast to end and clears it.
 */
internal fun keepAwake(): AutoCloseable {
    val done = CountDownLatch(1)
    val thread = Thread({
        val set = runCatching { Kernel32.lib.SetThreadExecutionState(ES_CONTINUOUS or ES_SYSTEM_REQUIRED) }.getOrDefault(0)
        try {
            done.await()
        } finally {
            if (set != 0) runCatching { Kernel32.lib.SetThreadExecutionState(ES_CONTINUOUS) }
        }
    }, "ks-beam-keep-awake").apply { isDaemon = true; start() }
    return AutoCloseable { done.countDown(); thread.join(1000) }
}

private const val ES_CONTINUOUS = 0x80000000.toInt()
private const val ES_SYSTEM_REQUIRED = 0x00000001

@Suppress("FunctionName")
private interface Kernel32 : Library {
    fun SetThreadExecutionState(flags: Int): Int

    companion object {
        val lib: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }
    }
}
