package com.ks.beam.mac

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

/**
 * Keeps the Mac from idle sleep while a cast runs: the TV reads the file from this Mac, so sleep
 * would stop the film (specs/plan-per-platform.md, step 2). The display may still sleep. An IOKit
 * power assertion, called through JNA; released when the cast ends or the app quits.
 */
internal fun keepAwake(reason: String): AutoCloseable {
    val id = runCatching {
        val type = CoreFoundation.lib.CFStringCreateWithCString(null, "PreventUserIdleSystemSleep", UTF8)
        val name = CoreFoundation.lib.CFStringCreateWithCString(null, reason, UTF8)
        try {
            val out = IntByReference()
            if (IOKit.lib.IOPMAssertionCreateWithName(type, ASSERTION_ON, name, out) == 0) out.value else null
        } finally {
            CoreFoundation.lib.CFRelease(type)
            CoreFoundation.lib.CFRelease(name)
        }
    }.getOrNull() ?: return AutoCloseable {}
    var released = false
    return AutoCloseable {
        if (!released) {
            released = true
            runCatching { IOKit.lib.IOPMAssertionRelease(id) }
        }
    }
}

private const val UTF8 = 0x08000100 // kCFStringEncodingUTF8
private const val ASSERTION_ON = 255 // kIOPMAssertionLevelOn

@Suppress("FunctionName")
private interface IOKit : Library {
    fun IOPMAssertionCreateWithName(type: Pointer, level: Int, name: Pointer, id: IntByReference): Int
    fun IOPMAssertionRelease(id: Int): Int

    companion object {
        val lib: IOKit by lazy { Native.load("IOKit", IOKit::class.java) }
    }
}

@Suppress("FunctionName")
private interface CoreFoundation : Library {
    fun CFStringCreateWithCString(allocator: Pointer?, text: String, encoding: Int): Pointer
    fun CFRelease(ref: Pointer)

    companion object {
        val lib: CoreFoundation by lazy { Native.load("CoreFoundation", CoreFoundation::class.java) }
    }
}
