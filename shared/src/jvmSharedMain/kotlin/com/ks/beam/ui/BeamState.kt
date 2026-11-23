package com.ks.beam.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ks.beam.core.Cast
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
                    platform.searchLock().use {
                        RendererFinder().find { r -> scope.launch { found += r; renderers = mergeRenderers(renderers, r) } }
                    }
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

    fun togglePlay() = control { c -> if (playing) c.pause() else c.play(); playing = !playing }

    fun seekBy(delta: Long) = seekTo(position.seconds + delta)

    fun seekTo(seconds: Long) {
        val max = position.durationSeconds.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = seconds.coerceIn(0, max)
        position = position.copy(seconds = target)
        control { it.seek(target) }
    }

    fun stop() {
        poll?.cancel()
        poll = null
        val c = cast ?: return
        cast = null
        playing = false
        scope.launch(Dispatchers.IO) { c.close() }
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
