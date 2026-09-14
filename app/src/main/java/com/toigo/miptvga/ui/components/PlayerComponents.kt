package com.toigo.miptvga.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.toigo.miptvga.ui.WelcomePlaceholder
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.toigo.miptvga.Channel
import com.toigo.miptvga.ChannelGroup
import com.toigo.miptvga.PlaybackBackend
import com.toigo.miptvga.PlaybackControllerActions
import com.toigo.miptvga.PlaybackControllerState
import com.toigo.miptvga.PlayerSurface
import com.toigo.miptvga.PlaylistSource
import com.toigo.miptvga.VideoCompatibilityMode

internal fun playbackProgressFraction(state: PlaybackControllerState): Float {
    if (!state.canSeek || state.durationMs <= 0L) return 0f
    return (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun formatPlaybackClock(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

internal fun livePlaybackDelayMs(state: PlaybackControllerState): Long {
    if (!state.isLive || !state.canSeek || state.durationMs <= 0L) return 0L
    return (state.durationMs - state.positionMs).coerceAtLeast(0L)
}

internal fun isNearLiveEdge(state: PlaybackControllerState): Boolean {
    return livePlaybackDelayMs(state) <= 3_000L
}

internal fun playbackModeLabel(state: PlaybackControllerState, backend: PlaybackBackend): String {
    return when {
        state.isLive && state.canSeek -> "DIRECTO · DVR"
        state.isLive -> "DIRECTO"
        else -> backend.displayName()
    }
}

internal fun playbackTimelineLabel(state: PlaybackControllerState): String {
    return when {
        state.isLive && state.canSeek -> {
            val delayMs = livePlaybackDelayMs(state)
            when {
                !state.isPlaying -> "Pausa en directo"
                delayMs <= 3_000L -> "En directo"
                else -> "Retraso · -${formatPlaybackClock(delayMs)}"
            }
        }
        state.isLive -> if (state.isPlaying) "En directo" else "Pausa en directo"
        state.canSeek && state.durationMs > 0L -> {
            "${formatPlaybackClock(state.positionMs)} / ${formatPlaybackClock(state.durationMs)}"
        }
        state.isPlaying -> "Reproduciendo"
        else -> "Pausado"
    }
}

@UnstableApi
@Composable
internal fun PlayerPanel(
    modifier: Modifier,
    selectedChannel: Channel?,
    selectedChannelIsFavorite: Boolean,
    selectedChannelIsIndividuallyFavorite: Boolean,
    selectedChannelGroup: ChannelGroup?,
    selectedChannelGroupIsFavorite: Boolean,
    showChannelLogos: Boolean,
    playlistSource: PlaylistSource?,
    playbackBackend: PlaybackBackend,
    videoCompatibilityMode: VideoCompatibilityMode,
    playbackMessage: String?,
    playbackMessageIsError: Boolean,
    controlsVisible: Boolean,
    controlsVisibilityToken: Int,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onToggleChannelLogos: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFavoriteGroup: () -> Unit,
    onToggleVideoCompatibilityMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onInputActivity: () -> Unit,
    onAutoHide: () -> Unit,
    onReconnectScheduled: () -> Unit,
    onPlaybackStarted: () -> Unit,
    onPlaybackError: (String?) -> Unit
) {
    val fullscreenTransitionToken = remember(selectedChannel?.playbackUrl) { mutableIntStateOf(0) }
    var playbackControllerState by remember(selectedChannel?.playbackUrl, playbackBackend) {
        mutableStateOf(PlaybackControllerState())
    }
    var playbackControllerActions by remember(selectedChannel?.playbackUrl, playbackBackend) {
        mutableStateOf(PlaybackControllerActions())
    }
    val handleFullscreenToggle = {
        fullscreenTransitionToken.intValue += 1
        onToggleFullscreen()
    }

    PlayerControlsAutoHideEffect(
        controlsVisible = controlsVisible,
        activityToken = controlsVisibilityToken,
        streamUrl = selectedChannel?.streamUrl,
        onAutoHide = onAutoHide
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isFullscreen) {
                    Modifier.background(Color.Black)
                } else {
                    Modifier
                        .padding(12.dp)
                        .clip(PanelShape)
                        .background(PanelColor)
                        .border(1.dp, PanelBorderColor, PanelShape)
                        .padding(10.dp)
                }
            ),
        verticalArrangement = Arrangement.spacedBy(if (isFullscreen) 0.dp else 10.dp)
    ) {
        if (!isFullscreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = selectedChannel?.name ?: "Vista previa",
                        color = Color.White,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = selectedChannel?.group?.takeIf { it.isNotBlank() }
                            ?: "Carga una lista y selecciona un canal para empezar",
                        color = SecondaryTextColor,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    playbackMessage?.let { message ->
                        StatusChip(
                            text = message,
                            accent = !playbackMessageIsError
                        )
                    }
                    AppActionButton(
                        text = "Fullscreen",
                        onClick = onToggleFullscreen,
                        modifier = Modifier.width(122.dp)
                    )
                    AppActionButton(
                        text = "Ajustes",
                        onClick = onOpenSettings,
                        modifier = Modifier.width(112.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(
                    if (isFullscreen) {
                        Modifier.background(Color.Black)
                    } else {
                        Modifier
                            .clip(PanelShape)
                            .background(Color.Black)
                            .border(1.dp, PanelBorderColor, PanelShape)
                    }
                )
        ) {
            if (selectedChannel == null || selectedChannel.playbackUrl.isBlank()) {
                WelcomePlaceholder()
            } else {
                PlayerSurface(
                    streamUrl = selectedChannel.playbackUrl,
                    requestHeaders = selectedChannel.requestHeaders,
                    playbackBackend = playbackBackend,
                    videoCompatibilityMode = videoCompatibilityMode,
                    fullscreenTransitionToken = fullscreenTransitionToken.intValue,
                    controlsVisible = controlsVisible,
                    onInputActivity = onInputActivity,
                    onAutoHide = onAutoHide,
                    onToggleFullscreen = handleFullscreenToggle,
                    onReconnectScheduled = onReconnectScheduled,
                    onPlaybackStarted = onPlaybackStarted,
                    onPlaybackError = onPlaybackError,
                    onPlaybackControllerStateChanged = { playbackControllerState = it },
                    onPlaybackControllerActionsChanged = { playbackControllerActions = it }
                )
            }

            if (controlsVisible && isFullscreen && selectedChannel != null && selectedChannel.playbackUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(if (isFullscreen) 12.dp else 16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ItemShape)
                            .background(PlayerOverlayColor)
                            .border(1.dp, PanelBorderColor, ItemShape)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = selectedChannel.name,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1
                            )
                            Text(
                                text = selectedChannel.group?.takeIf { it.isNotBlank() }
                                    ?: playlistSource.sourceDisplayLabel(),
                                color = MutedTextColor,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusChip(
                                text = when {
                                    selectedChannelGroupIsFavorite && selectedChannelIsIndividuallyFavorite -> "Canal + grupo"
                                    selectedChannelGroupIsFavorite -> "Grupo favorito"
                                    selectedChannelIsIndividuallyFavorite -> "Canal favorito"
                                    selectedChannelIsFavorite -> "Favorito"
                                    else -> "Directo"
                                },
                                accent = true
                            )
                            selectedChannelGroup?.let { group ->
                                StatusChip(text = "${group.count} canales · ${group.title}")
                            }
                            playbackMessage?.let { message ->
                                StatusChip(text = message, accent = !playbackMessageIsError)
                            }
                            if (isFullscreen) {
                                MiniOsdButton(
                                    text = "Salir",
                                    onClick = handleFullscreenToggle,
                                    active = true
                                )
                            }
                        }
                    }
                }

                PlayerBottomControlsOverlay(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    bottomPadding = 88.dp,
                    playbackControllerState = playbackControllerState,
                    playbackControllerActions = playbackControllerActions,
                    selectedChannelIsFavorite = selectedChannelIsIndividuallyFavorite,
                    selectedChannelGroup = selectedChannelGroup,
                    selectedChannelGroupIsFavorite = selectedChannelGroupIsFavorite,
                    playbackBackend = playbackBackend,
                    fullscreenButtonLabel = if (isFullscreen) "Salir" else "Fullscreen",
                    onToggleFavorite = onToggleFavorite,
                    onToggleFavoriteGroup = onToggleFavoriteGroup,
                    onToggleFullscreen = handleFullscreenToggle,
                    onOpenSettings = onOpenSettings,
                    onInputActivity = onInputActivity
                )
            }
        }
    }
}

@Composable
internal fun PlayerBottomControlsOverlay(
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp,
    playbackControllerState: PlaybackControllerState,
    playbackControllerActions: PlaybackControllerActions,
    selectedChannelIsFavorite: Boolean,
    selectedChannelGroup: ChannelGroup?,
    selectedChannelGroupIsFavorite: Boolean,
    playbackBackend: PlaybackBackend,
    fullscreenButtonLabel: String,
    onToggleFavorite: () -> Unit,
    onToggleFavoriteGroup: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onInputActivity: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
                .clip(ItemShape)
                .background(PlayerOverlayColor)
                .border(1.dp, PanelBorderColor, ItemShape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val liveSeekLabelBack = if (playbackControllerState.isLive) "⏪ Atrás 10 s" else "⏪ 10 s"
                val liveSeekLabelForward = if (playbackControllerState.isLive) "10 s ⏩" else "10 s ⏩"

                MiniOsdButton(
                    text = if (playbackControllerState.isPlaying) "⏸ Pausa" else "▶ Play",
                    onClick = {
                        onInputActivity()
                        playbackControllerActions.togglePlayPause()
                    },
                    active = playbackControllerState.isPlaying,
                    onFocus = onInputActivity
                )

                if (playbackControllerState.canSeek) {
                    MiniOsdButton(
                        text = liveSeekLabelBack,
                        onClick = {
                            onInputActivity()
                            playbackControllerActions.seekBack()
                        },
                        onFocus = onInputActivity
                    )
                    MiniOsdButton(
                        text = liveSeekLabelForward,
                        onClick = {
                            onInputActivity()
                            playbackControllerActions.seekForward()
                        },
                        onFocus = onInputActivity
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                StatusChip(
                    text = playbackModeLabel(playbackControllerState, playbackBackend),
                    accent = playbackControllerState.isLive || playbackControllerState.isPlaying
                )
                Text(
                    text = playbackTimelineLabel(playbackControllerState),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            if (playbackControllerState.isLive && playbackControllerState.canSeek) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(
                        text = if (isNearLiveEdge(playbackControllerState)) "Punto en directo" else "Timeshift activo",
                        accent = isNearLiveEdge(playbackControllerState)
                    )
                    StatusChip(
                        text = if (isNearLiveEdge(playbackControllerState)) {
                            "Sin retraso"
                        } else {
                            "Retraso ${formatPlaybackClock(livePlaybackDelayMs(playbackControllerState))}"
                        }
                    )
                }
            } else if (playbackControllerState.canSeek && playbackControllerState.durationMs > 0L) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(ChipShape)
                        .background(StatusChipColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(playbackProgressFraction(playbackControllerState))
                            .fillMaxHeight()
                            .background(PrimaryButtonColor)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniOsdButton(
                    text = if (selectedChannelIsFavorite) "★ Canal" else "☆ Canal",
                    onClick = {
                        onInputActivity()
                        onToggleFavorite()
                    },
                    active = selectedChannelIsFavorite,
                    onFocus = onInputActivity
                )
                selectedChannelGroup?.let { group ->
                    MiniOsdButton(
                        text = if (selectedChannelGroupIsFavorite) "★ Grupo · ${group.count}" else "☆ Grupo · ${group.count}",
                        onClick = {
                            onInputActivity()
                            onToggleFavoriteGroup()
                        },
                        active = selectedChannelGroupIsFavorite,
                        onFocus = onInputActivity
                    )
                }
                MiniOsdButton(
                    text = fullscreenButtonLabel,
                    onClick = {
                        onInputActivity()
                        onToggleFullscreen()
                    },
                    onFocus = onInputActivity
                )
                MiniOsdButton(
                    text = "Ajustes",
                    onClick = {
                        onInputActivity()
                        onOpenSettings()
                    },
                    onFocus = onInputActivity
                )
            }
        }
    }
}

@UnstableApi
@Composable
internal fun FullscreenPlayerPanel(
    modifier: Modifier,
    selectedChannel: Channel?,
    selectedChannelIsFavorite: Boolean,
    selectedChannelIsIndividuallyFavorite: Boolean,
    selectedChannelGroup: ChannelGroup?,
    selectedChannelGroupIsFavorite: Boolean,
    showChannelLogos: Boolean,
    playlistSource: PlaylistSource?,
    playbackBackend: PlaybackBackend,
    videoCompatibilityMode: VideoCompatibilityMode,
    playbackMessage: String?,
    playbackMessageIsError: Boolean,
    controlsVisible: Boolean,
    onToggleFullscreen: () -> Unit,
    onToggleChannelLogos: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFavoriteGroup: () -> Unit,
    onToggleVideoCompatibilityMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onInputActivity: () -> Unit,
    onAutoHide: () -> Unit,
    onReconnectScheduled: () -> Unit,
    onPlaybackStarted: () -> Unit,
    onPlaybackError: (String?) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (selectedChannel == null || selectedChannel.playbackUrl.isBlank()) {
            WelcomePlaceholder()
        } else {
            PlayerSurface(
                streamUrl = selectedChannel.playbackUrl,
                requestHeaders = selectedChannel.requestHeaders,
                playbackBackend = playbackBackend,
                videoCompatibilityMode = videoCompatibilityMode,
                fullscreenTransitionToken = 0,
                controlsVisible = controlsVisible,
                onInputActivity = onInputActivity,
                onAutoHide = onAutoHide,
                onToggleFullscreen = onToggleFullscreen,
                onReconnectScheduled = onReconnectScheduled,
                onPlaybackStarted = onPlaybackStarted,
                onPlaybackError = onPlaybackError,
                onPlaybackControllerStateChanged = { },
                onPlaybackControllerActionsChanged = { }
            )
        }

        if (controlsVisible && selectedChannel != null && selectedChannel.playbackUrl.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ItemShape)
                        .background(PlayerOverlayColor)
                        .border(1.dp, PanelBorderColor, ItemShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = selectedChannel.name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1
                        )
                        Text(
                            text = selectedChannel.group?.takeIf { it.isNotBlank() }
                                ?: playlistSource.sourceDisplayLabel(),
                            color = MutedTextColor,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusChip(
                            text = when {
                                selectedChannelGroupIsFavorite && selectedChannelIsIndividuallyFavorite -> "Canal + grupo"
                                selectedChannelGroupIsFavorite -> "Grupo favorito"
                                selectedChannelIsIndividuallyFavorite -> "Canal favorito"
                                selectedChannelIsFavorite -> "Favorito"
                                else -> "Directo"
                            },
                            accent = true
                        )
                        selectedChannelGroup?.let { group ->
                            StatusChip(text = "${group.count} canales · ${group.title}")
                        }
                        playbackMessage?.let { message ->
                            StatusChip(text = message, accent = !playbackMessageIsError)
                        }
                        MiniOsdButton(
                            text = "Salir",
                            onClick = onToggleFullscreen,
                            active = true
                        )
                    }
                }
            }

            PlayerBottomControlsOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                bottomPadding = 32.dp,
                playbackControllerState = PlaybackControllerState(
                    isPlaying = true,
                    isLive = true
                ),
                playbackControllerActions = PlaybackControllerActions(),
                selectedChannelIsFavorite = selectedChannelIsIndividuallyFavorite,
                selectedChannelGroup = selectedChannelGroup,
                selectedChannelGroupIsFavorite = selectedChannelGroupIsFavorite,
                playbackBackend = playbackBackend,
                fullscreenButtonLabel = "Salir",
                onToggleFavorite = onToggleFavorite,
                onToggleFavoriteGroup = onToggleFavoriteGroup,
                onToggleFullscreen = onToggleFullscreen,
                onOpenSettings = onOpenSettings,
                onInputActivity = onInputActivity
            )
        }
    }
}
