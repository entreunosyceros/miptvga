package com.toigo.miptvga

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal const val FullscreenOsdHideDelayMillis = 5_000L
internal enum class OsdKeyCommand { PASS, CONSUME, SHOW }

/** Only OK/arrows on the uncovered video are consumed. Media/colour/zap keys pass through. */
internal fun fullscreenOsdKeyCommand(code: Int, action: Int, visible: Boolean, videoFocused: Boolean): OsdKeyCommand {
    if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return OsdKeyCommand.PASS
    val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
    if (ok && (!visible || videoFocused)) return if (action == KeyEvent.ACTION_UP) OsdKeyCommand.SHOW else OsdKeyCommand.CONSUME
    val arrow = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
        code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
    if (arrow && !visible) return if (action == KeyEvent.ACTION_DOWN) OsdKeyCommand.SHOW else OsdKeyCommand.CONSUME
    return OsdKeyCommand.PASS
}

internal fun Modifier.fullscreenOsdInput(
    enabled: Boolean, visible: Boolean, videoFocused: Boolean,
    onActivity: () -> Unit, onRequestFocus: () -> Unit
): Modifier = onPreviewKeyEvent { event ->
    if (!enabled) return@onPreviewKeyEvent false
    val native = event.nativeKeyEvent
    when (fullscreenOsdKeyCommand(native.keyCode, native.action, visible, videoFocused)) {
        OsdKeyCommand.SHOW -> { onActivity(); onRequestFocus(); true }
        OsdKeyCommand.CONSUME -> true
        OsdKeyCommand.PASS -> {
            if (visible && native.action == KeyEvent.ACTION_DOWN && native.keyCode != KeyEvent.KEYCODE_BACK &&
                native.keyCode != KeyEvent.KEYCODE_ESCAPE) onActivity()
            false
        }
    }
}

internal fun adjacentOsdChannel(entries: List<ChannelListEntry>, selectedVisibleIndex: Int, forward: Boolean): Int? {
    if (entries.size < 2) return null
    val target = if (forward) {
        if (selectedVisibleIndex + 1 < entries.size) selectedVisibleIndex + 1 else 0
    } else {
        if (selectedVisibleIndex - 1 >= 0) selectedVisibleIndex - 1 else entries.size - 1
    }
    return entries.getOrNull(target)?.originalIndex
}

internal enum class OsdActionId { PLAY_PAUSE, PREVIOUS, NEXT, SEEK_BACK, SEEK_FORWARD, FAVORITE, GROUP_FAVORITE, INFO, SETTINGS, EXIT }
internal data class OsdAction(val id: OsdActionId, val label: String, val invoke: () -> Unit)

internal fun fullscreenOsdActions(
    state: PlaybackControllerState, playback: PlaybackControllerActions,
    previous: (() -> Unit)?, next: (() -> Unit)?, favorite: (() -> Unit)?, groupFavorite: (() -> Unit)?,
    info: (() -> Unit)?, settings: (() -> Unit)?, exit: () -> Unit,
    isFavorite: Boolean, isGroupFavorite: Boolean
): List<OsdAction> = buildList {
    if (state.canPause || state.isPlaying) add(OsdAction(OsdActionId.PLAY_PAUSE, if (state.isPlaying) "Pausa" else "Reproducir", playback.togglePlayPause))
    previous?.let { add(OsdAction(OsdActionId.PREVIOUS, "Canal anterior", it)) }
    next?.let { add(OsdAction(OsdActionId.NEXT, "Canal siguiente", it)) }
    if (state.canSeek) {
        add(OsdAction(OsdActionId.SEEK_BACK, "−10 s", playback.seekBack))
        add(OsdAction(OsdActionId.SEEK_FORWARD, "+10 s", playback.seekForward))
    }
    favorite?.let { add(OsdAction(OsdActionId.FAVORITE, if (isFavorite) "Quitar favorito" else "Añadir favorito", it)) }
    groupFavorite?.let { add(OsdAction(OsdActionId.GROUP_FAVORITE, if (isGroupFavorite) "Quitar grupo favorito" else "Favorito de grupo", it)) }
    info?.let { add(OsdAction(OsdActionId.INFO, "Información / EPG", it)) }
    settings?.let { add(OsdAction(OsdActionId.SETTINGS, "Ajustes", it)) }
    add(OsdAction(OsdActionId.EXIT, "Salir de pantalla completa", exit))
}

@Composable
internal fun FullscreenOsdBackHandler(enabled: Boolean, visible: Boolean, onHide: () -> Unit, onExit: () -> Unit) {
    BackHandler(enabled) { if (visible) onHide() else onExit() }
}

/** Owns UI focus and an inactivity timer only. Never owns or recreates a media engine. */
@Composable
internal fun FullscreenOsdEffects(
    enabled: Boolean, visible: Boolean, blocked: Boolean, activityToken: Int, playbackRequestId: Long,
    focusRequestToken: Int, controlsFocus: FocusRequester, videoFocus: FocusRequester,
    onHide: () -> Unit
) {
    val hide by rememberUpdatedState(onHide)
    LaunchedEffect(enabled, visible, blocked, activityToken, playbackRequestId) {
        if (enabled && visible && !blocked) { delay(FullscreenOsdHideDelayMillis); hide() }
    }
    // Neither polling position nor restarting the timer steals the user's focused control.
    LaunchedEffect(enabled, visible, blocked, focusRequestToken) {
        if (enabled && !blocked) {
            withFrameNanos { }
            runCatching { (if (visible) controlsFocus else videoFocus).requestFocus() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FullscreenOsdOverlay(
    title: String, current: String?, next: String?, status: String,
    actions: List<OsdAction>, primaryAction: OsdActionId, controlsFocus: FocusRequester,
    onActivity: () -> Unit, modifier: Modifier = Modifier, onFocused: (Boolean) -> Unit = {}
) {
    Column(modifier.fillMaxWidth().onFocusChanged { onFocused(it.hasFocus) }.focusGroup()
        .background(Color(0xEE151D29), RoundedCornerShape(6.dp))
        .border(1.dp, TvAccent, RoundedCornerShape(6.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        Text(current?.let { "Ahora · $it" } ?: "Sin información EPG disponible", color = TvMuted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        next?.let { Text("Después · $it", color = TvMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
        Text(status, color = TvAccent, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            actions.forEach { action -> key(action.id) {
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Button(onClick = { onActivity(); action.invoke() },
                    modifier = Modifier.heightIn(min = 48.dp)
                        .then(if (action.id == primaryAction) Modifier.focusRequester(controlsFocus) else Modifier)
                        .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFFE1FFF6) else Color(0xFF29443F), RoundedCornerShape(4.dp)),
                    interactionSource = interaction, shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (focused) Color(0xFF226E61) else Color(0xFF1B2635), contentColor = Color.White)) {
                    Text(action.label, style = MaterialTheme.typography.labelLarge)
                }
            } }
        }
    }
}
