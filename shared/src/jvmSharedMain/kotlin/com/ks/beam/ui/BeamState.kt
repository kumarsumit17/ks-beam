package com.ks.beam.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ks.beam.core.Cast
import com.ks.beam.core.DeviceDescription
import com.ks.beam.core.Mime
import com.ks.beam.core.Position
import com.ks.beam.core.Renderer
import com.ks.beam.core.RendererFinder
import com.ks.beam.core.UpnpException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BeamError {
    data object Unsupported : BeamError
    data object NoResponse : BeamError
    data class Format(val code: Int) : BeamError
    data class Refused(val code: Int) : BeamError
}

/** The interface's state. Runs network work on Dispatchers.IO; [scope] is the UI's scope. */
class BeamState(private val platform: BeamPlatform, private val scope: CoroutineScope) {
    var renderers by mutableStateOf(emptyList<Renderer>()); private set
    var searching by mutableStateOf(false); private set
    var selected by mutableStateOf<Renderer?>(null)
    var cast by mutableStateOf<Cast?>(null); private set
    var starting by mutableStateOf(false); private set
    var playing by mutableStateOf(false); private set
    var position by mutableStateOf(Position(0, 0)); private set
    var error by mutableStateOf<BeamError?>(null)

    var languageChoice by mutableStateOf(platform.settings.languageChoice()); private set
    val language: String get() = resolveLanguage(languageChoice, platform.systemLanguages)

    private var poll: Job? = null
    private var lock: CastLock? = null

    /** The control labels for OS media controls, set by the interface in its language. */
    var labels: CastLabels = CastLabels()
        set(value) {
            if (field == value) return
            field = value
            lock?.changed()
        }

    private val session = object : CastSession {
        override val rendererName get() = cast?.renderer?.name.orEmpty()
        override val title get() = cast?.media?.name.orEmpty()
        override val playing get() = this@BeamState.playing
        override val position get() = this@BeamState.position
        override val labels get() = this@BeamState.labels
        override fun togglePlay() = this@BeamState.togglePlay()
        override fun seekBy(delta: Long) = this@BeamState.seekBy(delta)
        override fun seekTo(seconds: Long) = this@BeamState.seekTo(seconds)
        override fun stop() = this@BeamState.stop()
    }

    fun chooseLanguage(choice: LanguageChoice) {
        languageChoice = choice
        platform.settings.setLanguageChoice(choice)
    }

    fun search() {
        if (searching) return
        searching = true
        scope.launch {
            val found = mutableListOf<Renderer>()
            try {
                withContext(Dispatchers.IO) {
                    val report = { r: Renderer -> scope.launch { found += r; renderers = mergeRenderers(renderers, r) }; Unit }
                    platform.extraRenderers.forEach { location -> DeviceDescription.fetch(location)?.let(report) }
                    platform.searchLock().use { RendererFinder().find(onFound = report) }
                }
            } finally {
                searching = false
                // Drop devices that no longer answer, but keep the one being cast to.
                renderers = renderers.filter { r -> found.any { it.id == r.id } || r.id == cast?.renderer?.id }
                if (selected != null && renderers.none { it.id == selected?.id }) selected = null
                if (selected == null && renderers.size == 1) selected = renderers.first()
            }
        }
    }

    fun chooseFileAndCast() {
        val renderer = selected ?: return
        scope.launch {
            val media = platform.pickMedia() ?: return@launch
            if (!Mime.isSupported(media.mimeType)) {
                error = BeamError.Unsupported
                return@launch
            }
            stop()
            error = null
            starting = true
            try {
                val started = withContext(Dispatchers.IO) {
                    val c = Cast(renderer, media)
                    try {
                        c.start(); c
                    } catch (e: Exception) {
                        c.close(); throw e
                    }
                }
                cast = started
                playing = true
                position = Position(0, 0)
                lock = platform.castLock(session)
                startPolling(started)
            } catch (e: UpnpException) {
                error = if (e.code == 714) BeamError.Format(e.code) else BeamError.Refused(e.code)
            } catch (e: Exception) {
                error = BeamError.NoResponse
            } finally {
                starting = false
            }
        }
    }

    fun togglePlay() {
        if (cast == null) return
        playing = !playing
        lock?.changed()
        val play = playing
        control { c -> if (play) c.play() else c.pause() }
    }

    fun seekBy(delta: Long) = seekTo(position.seconds + delta)

    fun seekTo(seconds: Long) {
        val max = position.durationSeconds.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = seconds.coerceIn(0, max)
        position = position.copy(seconds = target)
        lock?.changed()
        control { it.seek(target) }
    }

    fun stop() {
        poll?.cancel()
        poll = null
        lock?.close()
        lock = null
        val c = cast ?: return
        cast = null
        playing = false
        // Not on [scope]: stop() also runs while the interface's scope is being cancelled.
        Thread({ runCatching { c.close() } }, "ks-beam-stop").start()
    }

    private fun control(action: (Cast) -> Unit) {
        val c = cast ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action(c) }
            } catch (e: UpnpException) {
                error = BeamError.Refused(e.code)
            } catch (e: Exception) {
                error = BeamError.NoResponse
            }
        }
    }

    private fun startPolling(c: Cast) {
        poll?.cancel()
        poll = scope.launch {
            var failures = 0
            while (isActive && cast === c) {
                val result = runCatching { withContext(Dispatchers.IO) { c.position() to c.state() } }
                result.onSuccess { (p, state) ->
                    failures = 0
                    position = p
                    when (state) {
                        "PLAYING", "TRANSITIONING" -> playing = true
                        "PAUSED_PLAYBACK" -> playing = false
                        // Finished, or stopped from the TV's own remote.
                        "STOPPED", "NO_MEDIA_PRESENT" -> if (p.seconds == 0L) playing = false
                    }
                    lock?.changed()
                }.onFailure {
                    if (++failures >= 5) error = BeamError.NoResponse
                }
                delay(1000)
            }
        }
    }

    private fun mergeRenderers(list: List<Renderer>, r: Renderer) =
        (list.filterNot { it.id == r.id } + r).sortedBy { it.name.lowercase() }
}
