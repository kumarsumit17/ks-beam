package com.ks.beam.mac

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.ks.beam.ui.CastLock
import com.ks.beam.ui.CastSession
import java.awt.EventQueue

/**
 * The running cast in macOS's Now Playing (Control Centre, the menu bar, the lock screen) and the
 * keyboard's media keys, AirPods and the Touch Bar controlling it (specs/plan-per-platform.md,
 * step 8). Apple's MediaPlayer framework (`MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`),
 * called through the Objective-C runtime with JNA; nothing native to build.
 *
 * [start] for a cast, then [update] when it changes and [stop] when it ends. Commands from the
 * system reach the session on the AWT event thread.
 */
internal class NowPlaying(private val session: CastSession) {
    private val registered = mutableListOf<Pair<Pointer, String>>() // command to selector name

    fun start() = pool {
        val target = Objc.target(this)
        val center = Objc.send(Objc.cls("MPRemoteCommandCenter"), "sharedCommandCenter")
        fun add(command: String, selector: String) {
            val cmd = Objc.send(center, command)
            Objc.sendVoid(cmd, "setEnabled:", true)
            Objc.sendVoid(cmd, "addTarget:action:", target, Objc.sel(selector))
            registered += cmd to selector
        }
        add("playCommand", "beamPlay:")
        add("pauseCommand", "beamPause:")
        add("togglePlayPauseCommand", "beamToggle:")
        add("stopCommand", "beamStop:")
        add("changePlaybackPositionCommand", "beamSeekTo:")
        val ten = Objc.send(Objc.cls("NSArray"), "arrayWithObject:", Objc.number(SKIP_SECONDS.toDouble()))
        for ((command, selector) in listOf("skipForwardCommand" to "beamForward:", "skipBackwardCommand" to "beamBack:")) {
            Objc.sendVoid(Objc.send(center, command), "setPreferredIntervals:", ten)
            add(command, selector)
        }
        update()
    }

    /** Publishes the session's title, TV, position and state. */
    fun update() = pool {
        val info = Objc.send(Objc.cls("NSMutableDictionary"), "dictionary")
        fun put(key: String, value: Pointer) = Objc.sendVoid(info, "setObject:forKey:", value, MediaPlayer.constant(key))
        put("MPMediaItemPropertyTitle", Objc.string(session.title))
        put("MPMediaItemPropertyArtist", Objc.string(session.rendererName))
        val position = session.position
        if (position.durationSeconds > 0) put("MPMediaItemPropertyPlaybackDuration", Objc.number(position.durationSeconds.toDouble()))
        put("MPNowPlayingInfoPropertyElapsedPlaybackTime", Objc.number(position.seconds.toDouble()))
        put("MPNowPlayingInfoPropertyPlaybackRate", Objc.number(if (session.playing) 1.0 else 0.0))
        val center = Objc.send(Objc.cls("MPNowPlayingInfoCenter"), "defaultCenter")
        Objc.sendVoid(center, "setNowPlayingInfo:", info)
        Objc.sendVoid(center, "setPlaybackState:", if (session.playing) STATE_PLAYING else STATE_PAUSED)
    }

    /** Removes the cast from Now Playing and hands the media keys back. */
    fun stop() = pool {
        val target = Objc.target(this)
        for ((cmd, selector) in registered) Objc.sendVoid(cmd, "removeTarget:action:", target, Objc.sel(selector))
        registered.clear()
        Objc.forget(this)
        val center = Objc.send(Objc.cls("MPNowPlayingInfoCenter"), "defaultCenter")
        Objc.sendVoid(center, "setNowPlayingInfo:", Objc.NIL)
        Objc.sendVoid(center, "setPlaybackState:", STATE_STOPPED)
    }

    /** A command from the system; [seconds] for a seek to a position. */
    internal fun command(selector: String, seconds: Double?) {
        EventQueue.invokeLater {
            when (selector) {
                "beamPlay:" -> if (!session.playing) session.togglePlay()
                "beamPause:" -> if (session.playing) session.togglePlay()
                "beamToggle:" -> session.togglePlay()
                "beamStop:" -> session.stop()
                "beamForward:" -> session.seekBy(SKIP_SECONDS)
                "beamBack:" -> session.seekBy(-SKIP_SECONDS)
                "beamSeekTo:" -> seconds?.let { session.seekTo(it.toLong()) }
            }
        }
    }

    companion object {
        private const val SKIP_SECONDS = 10L
        // MPNowPlayingPlaybackState
        private const val STATE_PLAYING = 1L
        private const val STATE_PAUSED = 2L
        private const val STATE_STOPPED = 3L

        /** Now Playing for a cast, as the cast lock's part on macOS; null where the frameworks are missing. */
        fun lock(session: CastSession): CastLock? = runCatching {
            val now = NowPlaying(session).apply { start() }
            object : CastLock {
                override fun changed() { runCatching { now.update() } }
                override fun close() { runCatching { now.stop() } }
            }
        }.getOrNull()
    }
}

/** The Objective-C runtime, called non-variadically through JNA (required on Apple silicon). */
internal object Objc {
    private val lib = NativeLibrary.getInstance("objc")
    private val msgSend: Function = lib.getFunction("objc_msgSend")
    private val getClass: Function = lib.getFunction("objc_getClass")
    private val registerName: Function = lib.getFunction("sel_registerName")
    private val poolPush: Function = lib.getFunction("objc_autoreleasePoolPush")
    private val poolPop: Function = lib.getFunction("objc_autoreleasePoolPop")

    init {
        // NSString and friends, and MediaPlayer's classes.
        NativeLibrary.getInstance("/System/Library/Frameworks/Foundation.framework/Foundation")
        MediaPlayer.load()
    }

    fun cls(name: String): Pointer = getClass.invokePointer(arrayOf(name)) ?: error("no Objective-C class $name")
    fun sel(name: String): Pointer = registerName.invokePointer(arrayOf(name))
    /** Objective-C's nil as a JNA pointer (JNA's own Pointer.NULL is a Java null). */
    val NIL = Pointer(0L)

    /** A message whose answer may be nil ([NIL] then). */
    fun send(receiver: Pointer, selector: String, vararg args: Any): Pointer =
        msgSend.invokePointer(arrayOf(receiver, sel(selector), *args)) ?: NIL
    fun sendVoid(receiver: Pointer, selector: String, vararg args: Any?) {
        msgSend.invoke(Void::class.java, arrayOf(receiver, sel(selector), *args))
    }
    fun sendDouble(receiver: Pointer, selector: String): Double = msgSend.invokeDouble(arrayOf(receiver, sel(selector)))
    fun string(text: String): Pointer = send(cls("NSString"), "stringWithUTF8String:", text)
    fun number(value: Double): Pointer = send(cls("NSNumber"), "numberWithDouble:", value)

    fun <T> pool(block: () -> T): T {
        val p = poolPush.invokePointer(arrayOf())
        try {
            return block()
        } finally {
            poolPop.invokeVoid(arrayOf(p))
        }
    }

    // ---- One target object per NowPlaying, of a class made at run time --------------------------
    private interface Imp : Callback {
        fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long
    }

    private val selectors = listOf("beamPlay:", "beamPause:", "beamToggle:", "beamStop:", "beamForward:", "beamBack:", "beamSeekTo:")
    private val targets = mutableMapOf<NowPlaying, Pointer>()
    private val owners = mutableMapOf<Long, NowPlaying>()
    private val imp = object : Imp { // kept for the life of the process: the runtime holds the pointer
        override fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long {
            val owner = self?.let { synchronized(owners) { owners[Pointer.nativeValue(it)] } } ?: return COMMAND_FAILED
            val name = cmd?.let { lib.getFunction("sel_getName").invokeString(arrayOf(it), false) } ?: return COMMAND_FAILED
            val seconds = if (name == "beamSeekTo:" && event != null) sendDouble(event, "positionTime") else null
            owner.command(name, seconds)
            return COMMAND_SUCCESS
        }
    }
    private val targetClass: Pointer by lazy {
        val c = lib.getFunction("objc_allocateClassPair").invokePointer(arrayOf(cls("NSObject"), "KSBeamRemoteTarget", 0L))
        // "q@:@": returns NSInteger (MPRemoteCommandHandlerStatus), takes self, _cmd and the event.
        for (s in selectors) lib.getFunction("class_addMethod").invoke(Boolean::class.java, arrayOf(c, sel(s), imp, "q@:@"))
        lib.getFunction("objc_registerClassPair").invokeVoid(arrayOf(c))
        c
    }

    fun target(owner: NowPlaying): Pointer = synchronized(owners) {
        targets.getOrPut(owner) {
            send(send(targetClass, "alloc"), "init").also { owners[Pointer.nativeValue(it)] = owner }
        }
    }

    fun forget(owner: NowPlaying) = synchronized(owners) {
        targets.remove(owner)?.let { t ->
            owners.remove(Pointer.nativeValue(t))
            sendVoid(t, "release")
        }
    }

    private const val COMMAND_SUCCESS = 0L
    private const val COMMAND_FAILED = 200L // MPRemoteCommandHandlerStatusCommandFailed
}

/** MediaPlayer.framework: the Now Playing classes and their dictionary keys. */
internal object MediaPlayer {
    private val lib by lazy { NativeLibrary.getInstance("/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer") }
    fun load() { lib }
    /** A framework string constant such as `MPMediaItemPropertyTitle`. */
    fun constant(name: String): Pointer = lib.getGlobalVariableAddress(name).getPointer(0)
}

private fun <T> pool(block: () -> T): T = Objc.pool(block)
