package com.ks.beam.windows

import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.EventQueue

/** A dark title bar to match the app (Windows 10 2004+ and 11), through DWM; ignored elsewhere. */
internal fun useDarkTitleBar(window: ComposeWindow) {
    EventQueue.invokeLater {
        runCatching {
            val hwnd = Pointer(Native.getComponentID(window))
            Dwm.lib.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, IntByReference(1), 4)
        }
    }
}

private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

@Suppress("FunctionName")
private interface Dwm : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int

    companion object {
        val lib: Dwm by lazy { Native.load("dwmapi", Dwm::class.java) }
    }
}
