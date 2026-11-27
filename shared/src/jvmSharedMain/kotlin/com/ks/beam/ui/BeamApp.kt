package com.ks.beam.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import com.ks.beam.resources.cast_position_of
import com.ks.beam.resources.cast_position
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.ks.beam.resources.cast_volume
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.ks.beam.resources.devices_firewall
import com.ks.beam.resources.cast_pending
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ks.beam.core.AvTransport
import com.ks.beam.core.Renderer
import com.ks.beam.resources.Res
import com.ks.beam.resources.app_name
import com.ks.beam.resources.cast_channel
import com.ks.beam.resources.cast_choose_file
import com.ks.beam.resources.cast_play
import com.ks.beam.resources.cast_seek_back
import com.ks.beam.resources.cast_seek_forward
import com.ks.beam.resources.cast_starting
import com.ks.beam.resources.cast_stop
import com.ks.beam.resources.cast_to
import com.ks.beam.resources.cast_unsupported
import com.ks.beam.resources.common_close
import com.ks.beam.resources.common_language
import com.ks.beam.resources.common_language_english
import com.ks.beam.resources.common_language_hindi
import com.ks.beam.resources.common_language_system
import com.ks.beam.resources.common_pause
import com.ks.beam.resources.devices_choose
import com.ks.beam.resources.devices_empty
import com.ks.beam.resources.devices_refresh
import com.ks.beam.resources.devices_searching
import com.ks.beam.resources.devices_title
import com.ks.beam.resources.error_format
import com.ks.beam.resources.error_no_response
import com.ks.beam.resources.error_refused
import org.jetbrains.compose.resources.stringResource

private val Accent = Color(0xFF7C5CFF)

private val BeamColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondaryContainer = Color(0xFF2A2440),
    onSecondaryContainer = Color(0xFFE6E0FF),
    background = Color(0xFF0F0F14),
    surface = Color(0xFF0F0F14),
    surfaceVariant = Color(0xFF1C1B24),
)

/** The whole interface, shared by Android, Windows and macOS. */
/**
 * The sender's interface. Desktop entry points create [state] themselves so the window's menus and
 * keys reach it; [contentPadding] keeps content clear of a macOS title bar drawn over the window.
 */
@Composable
fun BeamApp(
    platform: BeamPlatform,
    state: BeamState = rememberBeamState(platform),
    contentPadding: PaddingValues = PaddingValues(),
) {
    LaunchedEffect(Unit) { state.search() }
    // The cast ends with the interface (window closed, app swiped away), releasing its lock.
    DisposableEffect(state) { onDispose { state.stop() } }

    MaterialTheme(colorScheme = platform.colorScheme() ?: BeamColors) {
        platform.ProvideLanguage(state.language) {
            // The OS's media controls (Android notification) show these, in the chosen language.
            val labels = CastLabels(
                castingTo = stringResource(Res.string.cast_to, state.cast?.renderer?.name.orEmpty()),
                play = stringResource(Res.string.cast_play),
                pause = stringResource(Res.string.common_pause),
                back = stringResource(Res.string.cast_seek_back),
                forward = stringResource(Res.string.cast_seek_forward),
                stop = stringResource(Res.string.cast_stop),
                channel = stringResource(Res.string.cast_channel),
            )
            SideEffect { state.labels = labels }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                BoxWithConstraints(
                    Modifier.fillMaxSize().safeDrawingPadding().padding(contentPadding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val cast = state.cast
                    // A wide window (a desktop, a tablet) keeps the TVs beside the cast controls.
                    val twoPanes = maxWidth >= 840.dp && cast != null
                    Column(Modifier.widthIn(max = if (twoPanes) 1200.dp else 720.dp).fillMaxSize().padding(20.dp)) {
                        Header(state)
                        Spacer(Modifier.height(16.dp))
                        platform.updater?.let { UpdateNotice(it, Modifier.padding(bottom = 16.dp)) }
                        state.error?.let { ErrorBanner(it) { state.error = null } }
                        if (twoPanes && cast != null) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                                Column(Modifier.weight(1f)) { DevicePanel(state) }
                                Column(Modifier.weight(1f)) { CastPanel(state, cast.renderer, cast.media.name) }
                            }
                        } else if (cast != null) {
                            CastPanel(state, cast.renderer, cast.media.name)
                        } else {
                            DevicePanel(state)
                        }
                    }
                }
            }
        }
    }
}

/** The interface's state, kept for as long as the composition. */
@Composable
fun rememberBeamState(platform: BeamPlatform): BeamState {
    val scope = rememberCoroutineScope()
    return remember { BeamState(platform, scope) }
}

/**
 * The keys every sender shares, matching the TV remote ([Constraints](specs/constraints.md)):
 * Space plays or pauses, left and right seek 10 s, up and down change the TV's volume, Esc stops.
 * Only while casting.
 */
fun BeamState.handleKey(event: KeyEvent): Boolean {
    if (cast == null || event.type != KeyEventType.KeyDown) return false
    if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return false
    when (event.key) {
        Key.Spacebar -> togglePlay()
        Key.DirectionLeft -> seekBy(-10)
        Key.DirectionRight -> seekBy(10)
        Key.DirectionUp -> if (volume != null) changeVolume(VOLUME_STEP) else return false
        Key.DirectionDown -> if (volume != null) changeVolume(-VOLUME_STEP) else return false
        Key.Escape -> stop()
        else -> return false
    }
    return true
}

@Composable
private fun Header(state: BeamState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        state.platform.updater?.let { UpdatesButton(it) }
        LanguageMenu(state.languageChoice, state::chooseLanguage)
    }
}

/** The suite's language choice: Same as the system, English, हिंदी. Shared by the sender and the receiver. */
@Composable
fun LanguageMenu(choice: LanguageChoice, onChoose: (LanguageChoice) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(Res.string.common_language)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                LanguageChoice.System to Res.string.common_language_system,
                LanguageChoice.English to Res.string.common_language_english,
                LanguageChoice.Hindi to Res.string.common_language_hindi,
            ).forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text((if (option == choice) "✓  " else "     ") + stringResource(label)) },
                    onClick = { onChoose(option); open = false },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.DevicePanel(state: BeamState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.devices_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (state.searching) {
            val searchingLabel = stringResource(Res.string.devices_searching)
            CircularProgressIndicator(Modifier.size(18.dp).semantics { contentDescription = searchingLabel }, strokeWidth = 2.dp)
        } else {
            TextButton(onClick = state::search) { Text(stringResource(Res.string.devices_refresh)) }
        }
    }
    Spacer(Modifier.height(8.dp))
    val message = when {
        state.renderers.isNotEmpty() -> null
        state.searching -> stringResource(Res.string.devices_searching)
        else -> stringResource(Res.string.devices_empty)
    }
    if (message != null) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        if (!state.searching && state.explainFirewall) {
            Text(stringResource(Res.string.devices_firewall), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        }
    }
    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.renderers, key = { it.id }) { r -> DeviceRow(r, r.id == state.selected?.id) { state.selected = r } }
    }
    Spacer(Modifier.height(16.dp))
    val waiting = state.pendingMedia
    if (waiting != null) {
        Text(
            stringResource(Res.string.cast_pending, waiting.name), color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(8.dp))
    } else if (state.renderers.isNotEmpty() && state.selected == null) {
        Text(stringResource(Res.string.devices_choose), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
    Button(
        onClick = state::chooseFileAndCast,
        enabled = state.selected != null && !state.starting,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (state.starting) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            Spacer(Modifier.size(10.dp))
            Text(stringResource(Res.string.cast_starting))
        } else {
            Text(stringResource(Res.string.cast_choose_file))
        }
    }
}

@Composable
private fun DeviceRow(r: Renderer, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (selected) BorderStroke(2.dp, Accent) else null,
        // Screen readers say which TV is chosen.
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = listOf(r.manufacturer, r.model).filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ColumnScope.CastPanel(state: BeamState, renderer: Renderer, fileName: String) {
    Text(stringResource(Res.string.cast_to, renderer.name), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Text(fileName, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(24.dp))

    val duration = state.position.durationSeconds
    var dragging by remember { mutableStateOf<Float?>(null) }
    // Screen readers: "Position in the file, 0:12:30 of 1:42:00", with back and forward 10 s as actions.
    val positionLabel = stringResource(Res.string.cast_position)
    val positionText = stringResource(
        Res.string.cast_position_of,
        AvTransport.formatTime(dragging?.toLong() ?: state.position.seconds),
        AvTransport.formatTime(duration),
    )
    val backLabel = stringResource(Res.string.cast_seek_back)
    val forwardLabel = stringResource(Res.string.cast_seek_forward)
    Slider(
        value = dragging ?: state.position.seconds.toFloat(),
        onValueChange = { dragging = it },
        onValueChangeFinished = { dragging?.let { state.seekTo(it.toLong()) }; dragging = null },
        valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
        enabled = duration > 0,
        modifier = Modifier.semantics {
            contentDescription = positionLabel
            stateDescription = positionText
            customActions = listOf(
                CustomAccessibilityAction(backLabel) { state.seekBy(-10); true },
                CustomAccessibilityAction(forwardLabel) { state.seekBy(10); true },
            )
        },
    )
    Row {
        Text(AvTransport.formatTime(dragging?.toLong() ?: state.position.seconds), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        if (duration > 0) Text(AvTransport.formatTime(duration), style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(20.dp))
    val playPause: @Composable (Modifier) -> Unit = { m ->
        Button(onClick = state::togglePlay, modifier = m) {
            Text(stringResource(if (state.playing) Res.string.common_pause else Res.string.cast_play))
        }
    }
    // Large text (the system's font size, 130% and up): play/pause gets its own row, so no word has to
    // break in a third of the width.
    val largeText = LocalDensity.current.fontScale >= 1.3f
    if (largeText) {
        playPause(Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(onClick = { state.seekBy(-10) }, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.cast_seek_back)) }
        if (!largeText) playPause(Modifier.weight(1f))
        FilledTonalButton(onClick = { state.seekBy(10) }, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.cast_seek_forward)) }
    }
    state.volume?.let { level ->
        // The TV's own volume (RenderingControl), when it has one.
        Spacer(Modifier.height(20.dp))
        Text(stringResource(Res.string.cast_volume), style = MaterialTheme.typography.bodySmall)
        var dragging by remember { mutableStateOf<Float?>(null) }
        val volumeLabel = stringResource(Res.string.cast_volume)
        Slider(
            value = dragging ?: level.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { state.setVolume(it.toInt()) }; dragging = null },
            valueRange = 0f..100f,
            modifier = Modifier.semantics {
                contentDescription = volumeLabel
                stateDescription = "${(dragging ?: level.toFloat()).toInt()}%"
            },
        )
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = state::stop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.cast_stop)) }
}

@Composable
private fun ErrorBanner(error: BeamError, onClose: () -> Unit) {
    val text = when (error) {
        BeamError.Unsupported -> stringResource(Res.string.cast_unsupported)
        BeamError.NoResponse -> stringResource(Res.string.error_no_response)
        is BeamError.Format -> stringResource(Res.string.error_format, error.code)
        is BeamError.Refused -> stringResource(Res.string.error_refused, error.code)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1D24)),
        // Read out as soon as it appears.
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable(onClick = onClose)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            TextButton(onClick = onClose) { Text(stringResource(Res.string.common_close)) }
        }
    }
}

/** The volume step for keys and the phone's volume buttons. */
const val VOLUME_STEP = 5
