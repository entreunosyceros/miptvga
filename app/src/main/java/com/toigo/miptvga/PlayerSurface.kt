package com.toigo.miptvga

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.view.KeyEvent as AndroidKeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.util.Locale

private const val LiveReconnectInitialDelayMillis = 1_200L
private const val LiveReconnectMaxDelayMillis = 8_000L
private const val MaxEndReconnectAttempts = 3
private const val PointerMoveThresholdPx = 2f
private const val ExoMinBufferMs = 25_000
private const val ExoMaxBufferMs = 90_000
private const val ExoBufferForPlaybackMs = 2_500
private const val ExoBufferForPlaybackAfterRebufferMs = 6_000
private const val ExoLiveTargetOffsetMs = 4_000L
private const val ExoLiveMinPlaybackSpeed = 0.97f
private const val ExoLiveMaxPlaybackSpeed = 1.03f
private const val VlcNetworkCachingMs = 4_500
private const val VlcLiveCachingMs = 3_000
private const val VlcFileCachingMs = 1_500
private const val PlaybackSeekStepMillis = 10_000L
private const val PlaybackStatePollingMillis = 500L
private const val FullscreenTransitionGuardMillis = 1_100L
private const val FullscreenTransitionRecoveryCheckMillis = 900L
private val ProgressiveVodExtensions = setOf(
    ".mp4", ".mkv", ".avi", ".mov", ".mp3", ".aac", ".flac", ".wav", ".m4a", ".webm", ".ogg"
)

@UnstableApi
@Composable
internal fun PlayerSurface(
    streamUrl: String,
    requestHeaders: Map<String, String>,
    playbackBackend: PlaybackBackend,
    videoCompatibilityMode: VideoCompatibilityMode,
    fullscreenTransitionToken: Int,
    controlsVisible: Boolean,
    onInputActivity: () -> Unit,
    onAutoHide: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onReconnectScheduled: () -> Unit,
    onPlaybackStarted: () -> Unit,
    onPlaybackError: (String?) -> Unit,
    onPlaybackControllerStateChanged: (PlaybackControllerState) -> Unit,
    onPlaybackControllerActionsChanged: (PlaybackControllerActions) -> Unit
) {
    when (playbackBackend) {
        PlaybackBackend.VLC -> VlcPlayerSurface(
            streamUrl = streamUrl,
            requestHeaders = requestHeaders,
            videoCompatibilityMode = videoCompatibilityMode,
            fullscreenTransitionToken = fullscreenTransitionToken,
            controlsVisible = controlsVisible,
            onInputActivity = onInputActivity,
            onAutoHide = onAutoHide,
            onToggleFullscreen = onToggleFullscreen,
            onReconnectScheduled = onReconnectScheduled,
            onPlaybackStarted = onPlaybackStarted,
            onPlaybackError = onPlaybackError,
            onPlaybackControllerStateChanged = onPlaybackControllerStateChanged,
            onPlaybackControllerActionsChanged = onPlaybackControllerActionsChanged
        )

        PlaybackBackend.EXOPLAYER -> ExoPlayerSurface(
            streamUrl = streamUrl,
            requestHeaders = requestHeaders,
            videoCompatibilityMode = videoCompatibilityMode,
            fullscreenTransitionToken = fullscreenTransitionToken,
            controlsVisible = controlsVisible,
            onInputActivity = onInputActivity,
            onAutoHide = onAutoHide,
            onToggleFullscreen = onToggleFullscreen,
            onReconnectScheduled = onReconnectScheduled,
            onPlaybackStarted = onPlaybackStarted,
            onPlaybackError = onPlaybackError,
            onPlaybackControllerStateChanged = onPlaybackControllerStateChanged,
            onPlaybackControllerActionsChanged = onPlaybackControllerActionsChanged
        )
    }
}

@UnstableApi
@Composable
private fun VlcPlayerSurface(
    streamUrl: String,
    requestHeaders: Map<String, String>,
    videoCompatibilityMode: VideoCompatibilityMode,
    fullscreenTransitionToken: Int,
    controlsVisible: Boolean,
    onInputActivity: () -> Unit,
    onAutoHide: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onReconnectScheduled: () -> Unit,
    onPlaybackStarted: () -> Unit,
    onPlaybackError: (String?) -> Unit,
    onPlaybackControllerStateChanged: (PlaybackControllerState) -> Unit,
    onPlaybackControllerActionsChanged: (PlaybackControllerActions) -> Unit
) {
    val applicationContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val playbackUrlInfo = remember(streamUrl) { analyzePlaybackUrl(streamUrl) }
    val currentOnInputActivity = rememberUpdatedState(onInputActivity)
    val currentOnToggleFullscreen = rememberUpdatedState(onToggleFullscreen)
    val currentOnReconnectScheduled = rememberUpdatedState(onReconnectScheduled)
    val currentOnPlaybackStarted = rememberUpdatedState(onPlaybackStarted)
    val currentOnPlaybackError = rememberUpdatedState(onPlaybackError)
    val currentOnPlaybackControllerStateChanged = rememberUpdatedState(onPlaybackControllerStateChanged)
    val currentOnPlaybackControllerActionsChanged = rememberUpdatedState(onPlaybackControllerActionsChanged)
    val currentStreamUrl = rememberUpdatedState(playbackUrlInfo.trimmed)
    val currentIsLiveStream = rememberUpdatedState(shouldApplyLivePlaybackHandling(playbackUrlInfo))
    val currentShouldReconnectAfterEnd = rememberUpdatedState(shouldReconnectAfterEnd(playbackUrlInfo))
    val currentShouldAutoReconnectOnFailure = rememberUpdatedState(shouldAutoReconnectOnFailure(playbackUrlInfo))
    val reconnectAttempt = remember(streamUrl, requestHeaders) { mutableIntStateOf(0) }
    val endReconnectAttempt = remember(streamUrl, requestHeaders) { mutableIntStateOf(0) }
    val reconnectToken = remember(streamUrl, requestHeaders) { mutableIntStateOf(0) }
    val reconnectPending = remember(streamUrl, requestHeaders) { mutableStateOf(false) }
    val fullscreenTransitionActive = remember(streamUrl, requestHeaders) { mutableStateOf(false) }
    val pointerTracker = remember(streamUrl, requestHeaders) { PointerActivityTracker() }
    val playbackStateSnapshot = remember(streamUrl, requestHeaders) { mutableStateOf(PlaybackControllerState()) }
    val playbackActionSnapshot = remember(streamUrl, requestHeaders) { mutableStateOf(PlaybackControllerActions()) }

    fun requestSoftReconnect() {
        if (currentStreamUrl.value.isBlank() || reconnectPending.value) return
        reconnectPending.value = true
        reconnectAttempt.intValue = 0
        reconnectToken.intValue += 1
    }

    val libVlc = remember(applicationContext) {
        LibVLC(
            applicationContext,
            arrayListOf(
                "--aout=opensles",
                "--audio-time-stretch",
                "--network-caching=$VlcNetworkCachingMs",
                "--live-caching=$VlcLiveCachingMs",
                "--file-caching=$VlcFileCachingMs",
                "--http-reconnect"
            )
        )
    }
    val mediaPlayer = remember(libVlc) {
        MediaPlayer(libVlc).apply {
            setVideoTrackEnabled(true)
            setAudioTrack(-1)
        }
    }

    LaunchedEffect(fullscreenTransitionToken, streamUrl, requestHeaders) {
        if (fullscreenTransitionToken <= 0 || currentStreamUrl.value.isBlank()) return@LaunchedEffect

        fullscreenTransitionActive.value = true
        kotlinx.coroutines.delay(FullscreenTransitionRecoveryCheckMillis)
        if (
            fullscreenTransitionActive.value &&
            !mediaPlayer.isPlaying &&
            currentShouldAutoReconnectOnFailure.value
        ) {
            requestSoftReconnect()
        }
        kotlinx.coroutines.delay((FullscreenTransitionGuardMillis - FullscreenTransitionRecoveryCheckMillis).coerceAtLeast(0L))
        fullscreenTransitionActive.value = false
    }

    DisposableEffect(mediaPlayer) {
        val playbackActions = PlaybackControllerActions(
            togglePlayPause = {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                } else {
                    mediaPlayer.play()
                }
            },
            play = {
                mediaPlayer.play()
            },
            pause = {
                mediaPlayer.pause()
            },
            seekBack = {
                if (mediaPlayer.isSeekable()) {
                    val targetTime = (mediaPlayer.time - PlaybackSeekStepMillis).coerceAtLeast(0L)
                    mediaPlayer.time = targetTime
                }
            },
            seekForward = {
                if (mediaPlayer.isSeekable()) {
                    val maxDuration = mediaPlayer.length.takeIf { it > 0L } ?: Long.MAX_VALUE
                    val targetTime = (mediaPlayer.time + PlaybackSeekStepMillis).coerceAtMost(maxDuration)
                    mediaPlayer.time = targetTime
                }
            }
        )
        playbackActionSnapshot.value = playbackActions
        currentOnPlaybackControllerActionsChanged.value(playbackActions)
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    reconnectAttempt.intValue = 0
                    endReconnectAttempt.intValue = 0
                    reconnectPending.value = false
                    fullscreenTransitionActive.value = false
                    currentOnPlaybackStarted.value()
                }

                MediaPlayer.Event.EndReached -> {
                    val shouldReconnect =
                        shouldRecoverEndedPlayback(playbackUrlInfo) &&
                            currentStreamUrl.value.isNotBlank() &&
                            !reconnectPending.value

                    if (shouldReconnect) {
                        if (fullscreenTransitionActive.value) {
                            requestSoftReconnect()
                        } else {
                            reconnectPending.value = true
                            reconnectAttempt.intValue += 1
                            reconnectToken.intValue += 1
                            currentOnReconnectScheduled.value()
                        }
                    } else {
                        currentOnPlaybackError.value("La emisión ha finalizado")
                    }
                }

                MediaPlayer.Event.EncounteredError -> {
                    if (
                        currentShouldAutoReconnectOnFailure.value &&
                        currentStreamUrl.value.isNotBlank() &&
                        !reconnectPending.value
                    ) {
                        if (fullscreenTransitionActive.value) {
                            requestSoftReconnect()
                        } else {
                            reconnectPending.value = true
                            reconnectAttempt.intValue += 1
                            reconnectToken.intValue += 1
                            currentOnReconnectScheduled.value()
                        }
                    } else {
                        currentOnPlaybackError.value("Error al reproducir con VLC")
                    }
                }

                else -> Unit
            }
        }
        mediaPlayer.setEventListener(listener)
        onDispose {
            currentOnPlaybackControllerActionsChanged.value(PlaybackControllerActions())
            currentOnPlaybackControllerStateChanged.value(PlaybackControllerState())
            playbackActionSnapshot.value = PlaybackControllerActions()
            playbackStateSnapshot.value = PlaybackControllerState()
            mediaPlayer.setEventListener(null)
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.detachViews() }
            runCatching { mediaPlayer.release() }
            runCatching { libVlc.release() }
        }
    }

    LaunchedEffect(mediaPlayer, streamUrl, requestHeaders) {
        while (true) {
            val playbackState = PlaybackControllerState(
                isPlaying = mediaPlayer.isPlaying,
                canPause = mediaPlayer.isPlaying || currentStreamUrl.value.isNotBlank(),
                canSeek = mediaPlayer.isSeekable() && mediaPlayer.length > 0L,
                isLive = currentIsLiveStream.value,
                positionMs = mediaPlayer.time.coerceAtLeast(0L),
                durationMs = mediaPlayer.length.coerceAtLeast(0L)
            )
            playbackStateSnapshot.value = playbackState
            currentOnPlaybackControllerStateChanged.value(playbackState)
            kotlinx.coroutines.delay(PlaybackStatePollingMillis)
        }
    }

    DisposableEffect(lifecycleOwner, mediaPlayer, applicationContext) {
        var shouldResumeOnStart = mediaPlayer.isPlaying
        var playbackStoppedForSleep = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    shouldResumeOnStart = mediaPlayer.isPlaying
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (!applicationContext.isDeviceInteractive()) {
                        playbackStoppedForSleep = true
                        shouldResumeOnStart = false
                        reconnectPending.value = false
                        reconnectAttempt.intValue = 0
                        endReconnectAttempt.intValue = 0
                        runCatching { mediaPlayer.stop() }
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (!playbackStoppedForSleep && shouldResumeOnStart) {
                        runCatching { mediaPlayer.play() }
                    }
                    playbackStoppedForSleep = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(mediaPlayer, streamUrl, requestHeaders, reconnectToken.intValue) {
        val sanitizedUrl = streamUrl.trim()
        if (sanitizedUrl.isBlank()) {
            runCatching { mediaPlayer.stop() }
            return@LaunchedEffect
        }

        val shouldDelayReconnect = reconnectPending.value && reconnectAttempt.intValue > 0
        if (shouldDelayReconnect) {
            kotlinx.coroutines.delay(reconnectDelayMillis(reconnectAttempt.intValue))
        }

        runCatching {
            reconnectPending.value = false
            val media = Media(libVlc, normalizePlaybackUri(sanitizedUrl)).apply {
                setHWDecoderEnabled(true, false)
                addOption(":http-reconnect=true")
                addOption(":network-caching=$VlcNetworkCachingMs")
                addOption(":live-caching=$VlcLiveCachingMs")
                addOption(":file-caching=$VlcFileCachingMs")
                addVlcHeaderOptions(requestHeaders)
                addPlaybackSpecificOptions(sanitizedUrl)
            }
            mediaPlayer.stop()
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
        }.onFailure { error ->
            if (
                currentShouldAutoReconnectOnFailure.value &&
                currentStreamUrl.value.isNotBlank() &&
                !reconnectPending.value
            ) {
                if (fullscreenTransitionActive.value) {
                    requestSoftReconnect()
                } else {
                    reconnectPending.value = true
                    reconnectAttempt.intValue += 1
                    reconnectToken.intValue += 1
                    currentOnReconnectScheduled.value()
                }
            } else {
                currentOnPlaybackError.value(error.localizedMessage ?: error.message ?: "Error al iniciar la reproducción")
            }
        }
    }

    key(videoCompatibilityMode) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { event ->
                    if (
                        handlePlaybackRemoteKeyEvent(
                            event = event,
                            playbackState = playbackStateSnapshot.value,
                            playbackActions = playbackActionSnapshot.value,
                            onInputActivity = currentOnInputActivity.value
                        )
                    ) {
                        true
                    } else {
                        val toggleFullscreen = event.type == KeyEventType.KeyUp && (
                            event.key == Key.Menu || event.key == Key.F
                        )
                        if (toggleFullscreen) {
                            currentOnInputActivity.value()
                            currentOnToggleFullscreen.value()
                            true
                        } else {
                            false
                        }
                    }
                },
            factory = { context ->
                VLCVideoLayout(context).apply {
                    keepScreenOn = true
                    runCatching { mediaPlayer.detachViews() }
                    runCatching { mediaPlayer.attachViews(this, null, false, false) }
                    setOnTouchListener { view, event ->
                        handleMouseMotionEvent(view, event, pointerTracker, currentOnInputActivity.value)
                    }
                    setOnGenericMotionListener { _, event ->
                        handleGenericMouseMotionEvent(event, pointerTracker, currentOnInputActivity.value)
                    }
                }
            },
            update = { videoLayout ->
                videoLayout.keepScreenOn = true
            }
        )
    }
}

@UnstableApi
@Composable
private fun ExoPlayerSurface(
    streamUrl: String,
    requestHeaders: Map<String, String>,
    videoCompatibilityMode: VideoCompatibilityMode,
    fullscreenTransitionToken: Int,
    controlsVisible: Boolean,
    onInputActivity: () -> Unit,
    onAutoHide: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onReconnectScheduled: () -> Unit,
    onPlaybackStarted: () -> Unit,
    onPlaybackError: (String?) -> Unit,
    onPlaybackControllerStateChanged: (PlaybackControllerState) -> Unit,
    onPlaybackControllerActionsChanged: (PlaybackControllerActions) -> Unit
) {
    val applicationContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val playbackUrlInfo = remember(streamUrl) { analyzePlaybackUrl(streamUrl) }
    val currentOnInputActivity = rememberUpdatedState(onInputActivity)
    val currentOnToggleFullscreen = rememberUpdatedState(onToggleFullscreen)
    val currentOnReconnectScheduled = rememberUpdatedState(onReconnectScheduled)
    val currentOnPlaybackStarted = rememberUpdatedState(onPlaybackStarted)
    val currentOnPlaybackError = rememberUpdatedState(onPlaybackError)
    val currentOnPlaybackControllerStateChanged = rememberUpdatedState(onPlaybackControllerStateChanged)
    val currentOnPlaybackControllerActionsChanged = rememberUpdatedState(onPlaybackControllerActionsChanged)
    val currentStreamUrl = rememberUpdatedState(playbackUrlInfo.trimmed)
    val currentIsLiveStream = rememberUpdatedState(shouldApplyLivePlaybackHandling(playbackUrlInfo))
    val currentShouldReconnectAfterEnd = rememberUpdatedState(shouldReconnectAfterEnd(playbackUrlInfo))
    val currentShouldAutoReconnectOnFailure = rememberUpdatedState(shouldAutoReconnectOnFailure(playbackUrlInfo))
    val reconnectAttempt = remember(streamUrl, requestHeaders) { mutableIntStateOf(0) }
    val endReconnectAttempt = remember(streamUrl, requestHeaders) { mutableIntStateOf(0) }
    val reconnectToken = remember(streamUrl, requestHeaders) { mutableIntStateOf(0) }
    val reconnectPending = remember(streamUrl, requestHeaders) { mutableStateOf(false) }
    val fullscreenTransitionActive = remember(streamUrl, requestHeaders) { mutableStateOf(false) }
    val pointerTracker = remember(streamUrl, requestHeaders) { PointerActivityTracker() }
    val playbackStateSnapshot = remember(streamUrl, requestHeaders) { mutableStateOf(PlaybackControllerState()) }
    val playbackActionSnapshot = remember(streamUrl, requestHeaders) { mutableStateOf(PlaybackControllerActions()) }
    val playerAudioAttributes = remember {
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }
    val usePlaybackCache = remember(streamUrl) { shouldUsePlaybackCache(streamUrl) }
    val exoPlayer = remember(applicationContext, requestHeaders, usePlaybackCache) {
        val dataSourceFactory = PlaybackCache.createDataSourceFactory(
            context = applicationContext,
            requestHeaders = requestHeaders,
            useCache = usePlaybackCache
        )
        ExoPlayer.Builder(applicationContext)
            .setRenderersFactory(
                DefaultRenderersFactory(applicationContext)
                    .setEnableDecoderFallback(true)
            )
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(createExoLoadControl())
            .build().apply {
                setAudioAttributes(playerAudioAttributes, true)
                setWakeMode(C.WAKE_MODE_NETWORK)
                volume = 1f
                playWhenReady = true
            }
    }

    fun requestSoftReconnect() {
        if (currentStreamUrl.value.isBlank() || reconnectPending.value) return
        reconnectPending.value = true
        reconnectAttempt.intValue = 0
        reconnectToken.intValue += 1
    }

    LaunchedEffect(fullscreenTransitionToken, streamUrl, requestHeaders) {
        if (fullscreenTransitionToken <= 0 || currentStreamUrl.value.isBlank()) return@LaunchedEffect

        fullscreenTransitionActive.value = true
        kotlinx.coroutines.delay(FullscreenTransitionRecoveryCheckMillis)
        val needsRecovery =
            fullscreenTransitionActive.value &&
                !exoPlayer.isPlaying &&
                currentShouldAutoReconnectOnFailure.value &&
                exoPlayer.playbackState != Player.STATE_READY
        if (needsRecovery) {
            requestSoftReconnect()
        }
        kotlinx.coroutines.delay((FullscreenTransitionGuardMillis - FullscreenTransitionRecoveryCheckMillis).coerceAtLeast(0L))
        fullscreenTransitionActive.value = false
    }

    DisposableEffect(exoPlayer) {
        val playbackActions = PlaybackControllerActions(
            togglePlayPause = {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                } else {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
            },
            play = {
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            },
            pause = {
                exoPlayer.pause()
            },
            seekBack = {
                if (exoPlayer.isCurrentMediaItemSeekable) {
                    exoPlayer.seekTo((exoPlayer.currentPosition - PlaybackSeekStepMillis).coerceAtLeast(0L))
                }
            },
            seekForward = {
                if (exoPlayer.isCurrentMediaItemSeekable) {
                    val duration = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                    exoPlayer.seekTo((exoPlayer.currentPosition + PlaybackSeekStepMillis).coerceAtMost(duration))
                }
            }
        )
        playbackActionSnapshot.value = playbackActions
        currentOnPlaybackControllerActionsChanged.value(playbackActions)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    reconnectAttempt.intValue = 0
                    endReconnectAttempt.intValue = 0
                    reconnectPending.value = false
                    fullscreenTransitionActive.value = false
                    currentOnPlaybackStarted.value()
                } else if (
                    playbackState == Player.STATE_ENDED &&
                    shouldRecoverEndedPlayback(playbackUrlInfo) &&
                    currentStreamUrl.value.isNotBlank() &&
                    !reconnectPending.value
                ) {
                    if (fullscreenTransitionActive.value) {
                        requestSoftReconnect()
                    } else {
                        reconnectPending.value = true
                        reconnectAttempt.intValue += 1
                        reconnectToken.intValue += 1
                        currentOnReconnectScheduled.value()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (
                    currentShouldAutoReconnectOnFailure.value &&
                    currentStreamUrl.value.isNotBlank() &&
                    !reconnectPending.value
                ) {
                    if (fullscreenTransitionActive.value) {
                        requestSoftReconnect()
                    } else {
                        reconnectPending.value = true
                        reconnectAttempt.intValue += 1
                        reconnectToken.intValue += 1
                        currentOnReconnectScheduled.value()
                    }
                } else {
                    currentOnPlaybackError.value(formatPlaybackError(error))
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            currentOnPlaybackControllerActionsChanged.value(PlaybackControllerActions())
            currentOnPlaybackControllerStateChanged.value(PlaybackControllerState())
            playbackActionSnapshot.value = PlaybackControllerActions()
            playbackStateSnapshot.value = PlaybackControllerState()
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, streamUrl, requestHeaders) {
        while (true) {
            val playbackState = PlaybackControllerState(
                isPlaying = exoPlayer.isPlaying,
                canPause = exoPlayer.mediaItemCount > 0,
                canSeek = exoPlayer.isCurrentMediaItemSeekable && exoPlayer.duration > 0L,
                isLive = currentIsLiveStream.value || exoPlayer.isCurrentMediaItemLive,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                durationMs = exoPlayer.duration.takeIf { it > 0L }?.coerceAtLeast(0L) ?: 0L
            )
            playbackStateSnapshot.value = playbackState
            currentOnPlaybackControllerStateChanged.value(playbackState)
            kotlinx.coroutines.delay(PlaybackStatePollingMillis)
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer, applicationContext) {
        var shouldResumeOnStart = exoPlayer.playWhenReady
        var playbackStoppedForSleep = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    shouldResumeOnStart = exoPlayer.isPlaying || exoPlayer.playWhenReady
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_STOP -> {
                    if (!applicationContext.isDeviceInteractive()) {
                        playbackStoppedForSleep = true
                        shouldResumeOnStart = false
                        reconnectPending.value = false
                        reconnectAttempt.intValue = 0
                        endReconnectAttempt.intValue = 0
                        exoPlayer.playWhenReady = false
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (!playbackStoppedForSleep && shouldResumeOnStart && exoPlayer.mediaItemCount > 0) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                    playbackStoppedForSleep = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(exoPlayer, streamUrl, reconnectToken.intValue) {
        val sanitizedUrl = streamUrl.trim()
        if (sanitizedUrl.isBlank()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            return@LaunchedEffect
        }

        val shouldDelayReconnect = reconnectPending.value && reconnectAttempt.intValue > 0
        if (shouldDelayReconnect) {
            kotlinx.coroutines.delay(reconnectDelayMillis(reconnectAttempt.intValue))
        }

        runCatching {
            reconnectPending.value = false
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(buildMediaItem(sanitizedUrl, playbackUrlInfo))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        }.onFailure { error ->
            if (
                currentShouldAutoReconnectOnFailure.value &&
                currentStreamUrl.value.isNotBlank() &&
                !reconnectPending.value
            ) {
                if (fullscreenTransitionActive.value) {
                    requestSoftReconnect()
                } else {
                    reconnectPending.value = true
                    reconnectAttempt.intValue += 1
                    reconnectToken.intValue += 1
                    currentOnReconnectScheduled.value()
                }
            } else {
                currentOnPlaybackError.value(error.localizedMessage ?: error.message)
            }
        }
    }

    key(videoCompatibilityMode) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { event ->
                    if (
                        handlePlaybackRemoteKeyEvent(
                            event = event,
                            playbackState = playbackStateSnapshot.value,
                            playbackActions = playbackActionSnapshot.value,
                            onInputActivity = currentOnInputActivity.value
                        )
                    ) {
                        true
                    } else {
                        val toggleFullscreen = event.type == KeyEventType.KeyUp && (
                            event.key == Key.Menu || event.key == Key.F
                        )
                        if (toggleFullscreen) {
                            currentOnInputActivity.value()
                            currentOnToggleFullscreen.value()
                            true
                        } else {
                            false
                        }
                    }
                },
            factory = { context ->
                val layoutId = when (videoCompatibilityMode) {
                    VideoCompatibilityMode.TEXTURE_VIEW -> R.layout.player_view_texture
                    VideoCompatibilityMode.SURFACE_VIEW -> R.layout.player_view_surface
                }

                (LayoutInflater.from(context).inflate(layoutId, null, false) as PlayerView).apply {
                    useController = true
                    controllerAutoShow = false
                    controllerShowTimeoutMs = ControlsAutoHideDelayMillis.toInt()
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setOnTouchListener { view, event ->
                        handleMouseMotionEvent(view, event, pointerTracker, currentOnInputActivity.value)
                    }
                    setOnGenericMotionListener { _, event ->
                        handleGenericMouseMotionEvent(event, pointerTracker, currentOnInputActivity.value)
                    }
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.keepScreenOn = true
                playerView.useController = false
                playerView.hideController()
            }
        )
    }
}

private fun handleMouseMotionEvent(
    view: android.view.View,
    event: MotionEvent,
    pointerTracker: PointerActivityTracker,
    onInputActivity: () -> Unit
): Boolean {
    return if (event.isSecondaryMouseAction()) {
        onInputActivity()
        true
    } else {
        if (
            event.action == MotionEvent.ACTION_DOWN ||
            event.action == MotionEvent.ACTION_SCROLL ||
            (event.action == MotionEvent.ACTION_MOVE && pointerTracker.shouldReportMove(event))
        ) {
            onInputActivity()
        }
        if (event.action == MotionEvent.ACTION_UP) {
            view.performClick()
        }
        false
    }
}

private fun handleGenericMouseMotionEvent(
    event: MotionEvent,
    pointerTracker: PointerActivityTracker,
    onInputActivity: () -> Unit
): Boolean {
    return if (event.isSecondaryMouseAction()) {
        onInputActivity()
        true
    } else {
        if (
            event.action == MotionEvent.ACTION_SCROLL ||
            (event.action == MotionEvent.ACTION_HOVER_MOVE && pointerTracker.shouldReportMove(event))
        ) {
            onInputActivity()
        }
        false
    }
}

private class PointerActivityTracker {
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    fun shouldReportMove(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val shouldReport =
            lastX.isNaN() ||
                lastY.isNaN() ||
                kotlin.math.abs(x - lastX) >= PointerMoveThresholdPx ||
                kotlin.math.abs(y - lastY) >= PointerMoveThresholdPx

        lastX = x
        lastY = y
        return shouldReport
    }
}

private fun handlePlaybackRemoteKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    playbackState: PlaybackControllerState,
    playbackActions: PlaybackControllerActions,
    onInputActivity: () -> Unit
): Boolean {
    val nativeEvent = event.nativeKeyEvent

    fun consumeOnKeyUp(block: () -> Unit): Boolean {
        return when (nativeEvent.action) {
            AndroidKeyEvent.ACTION_DOWN -> true
            AndroidKeyEvent.ACTION_UP -> {
                onInputActivity()
                block()
                true
            }
            else -> false
        }
    }

    fun consumeSeekOnKeyDown(block: () -> Unit): Boolean {
        return when (nativeEvent.action) {
            AndroidKeyEvent.ACTION_DOWN -> {
                onInputActivity()
                block()
                true
            }
            AndroidKeyEvent.ACTION_UP -> true
            else -> false
        }
    }

    return when (nativeEvent.keyCode) {
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> consumeOnKeyUp(playbackActions.togglePlayPause)
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> consumeOnKeyUp {
            if (!playbackState.isPlaying) {
                playbackActions.play()
            }
        }
        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> consumeOnKeyUp {
            if (playbackState.isPlaying || playbackState.canPause) {
                playbackActions.pause()
            }
        }
        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
            if (!playbackState.canSeek) return false
            consumeSeekOnKeyDown(playbackActions.seekBack)
        }
        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
            if (!playbackState.canSeek) return false
            consumeSeekOnKeyDown(playbackActions.seekForward)
        }
        else -> false
    }
}

private fun MotionEvent.isSecondaryMouseAction(): Boolean {
    val secondaryPressed = buttonState and MotionEvent.BUTTON_SECONDARY != 0
    return secondaryPressed && (
        action == MotionEvent.ACTION_BUTTON_PRESS ||
            action == MotionEvent.ACTION_BUTTON_RELEASE ||
            action == MotionEvent.ACTION_DOWN ||
            action == MotionEvent.ACTION_UP
        )
}

private fun normalizePlaybackUri(rawUrl: String): Uri {
    val trimmed = rawUrl.trim()
    val sanitized = trimmed.replace(" ", "%20")

    return when {
        sanitized.startsWith("content://", ignoreCase = true) -> Uri.parse(sanitized)
        sanitized.startsWith("file://", ignoreCase = true) -> Uri.parse(sanitized)
        sanitized.startsWith("/") -> Uri.fromFile(File(trimmed))
        else -> Uri.parse(sanitized)
    }
}

private data class PlaybackUrlInfo(
    val trimmed: String,
    val normalized: String,
    val uri: Uri?,
    val scheme: String,
    val path: String
) {
    val isRemoteHttp: Boolean =
        scheme == "http" || scheme == "https" ||
            normalized.startsWith("http://") || normalized.startsWith("https://")

    val isLocalResource: Boolean =
        normalized.startsWith("file://") ||
            normalized.startsWith("content://") ||
            normalized.startsWith("/")

    fun hasProgressiveVodExtension(): Boolean {
        return ProgressiveVodExtensions.any { extension ->
            path.endsWith(extension) || normalized.endsWith(extension) || normalized.contains("$extension?")
        }
    }
}

private fun analyzePlaybackUrl(url: String): PlaybackUrlInfo {
    val trimmed = url.trim()
    val normalized = trimmed.lowercase(Locale.ROOT)
    val uri = runCatching { normalizePlaybackUri(trimmed) }.getOrNull()
    return PlaybackUrlInfo(
        trimmed = trimmed,
        normalized = normalized,
        uri = uri,
        scheme = uri?.scheme?.lowercase(Locale.ROOT).orEmpty(),
        path = uri?.path.orEmpty().lowercase(Locale.ROOT)
    )
}

private fun buildMediaItem(url: String): MediaItem {
    return buildMediaItem(url, analyzePlaybackUrl(url))
}

private fun buildMediaItem(url: String, playbackUrlInfo: PlaybackUrlInfo): MediaItem {
    val uri = normalizePlaybackUri(playbackUrlInfo.trimmed.ifBlank { url })
    val mimeType = inferMimeType(playbackUrlInfo)
    val liveStream = shouldApplyLivePlaybackHandling(playbackUrlInfo)
    return MediaItem.Builder()
        .setUri(uri)
        .apply {
            if (mimeType != null) setMimeType(mimeType)
            if (liveStream) {
                setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(ExoLiveTargetOffsetMs)
                        .setMinPlaybackSpeed(ExoLiveMinPlaybackSpeed)
                        .setMaxPlaybackSpeed(ExoLiveMaxPlaybackSpeed)
                        .build()
                )
            }
        }
        .build()
}

internal fun shouldUsePlaybackCache(url: String): Boolean {
    return shouldUsePlaybackCache(analyzePlaybackUrl(url))
}

private fun shouldUsePlaybackCache(urlInfo: PlaybackUrlInfo): Boolean {
    val normalized = urlInfo.normalized
    return when {
        shouldApplyLivePlaybackHandling(urlInfo) -> false
        looksLikeVodStream(urlInfo) -> false
        normalized.startsWith("rtsp://") -> false
        normalized.startsWith("content://") -> false
        normalized.startsWith("file://") -> false
        else -> true
    }
}


internal fun inferMimeType(url: String): String? {
    return inferMimeType(analyzePlaybackUrl(url))
}

private fun inferMimeType(urlInfo: PlaybackUrlInfo): String? {
    val normalized = urlInfo.normalized
    return when {
        normalized.startsWith("rtsp://") -> null
        normalized.contains("output=ts") -> MimeTypes.VIDEO_MP2T
        urlInfo.path.endsWith(".ts") || normalized.endsWith(".ts") || normalized.contains(".ts?") -> MimeTypes.VIDEO_MP2T
        normalized.contains(".m3u8") || normalized.contains("format=m3u8") || normalized.contains("output=m3u8") -> MimeTypes.APPLICATION_M3U8
        normalized.contains(".mpd") -> MimeTypes.APPLICATION_MPD
        normalized.contains(".ism") || normalized.contains("manifest") && normalized.contains("format=ism") -> MimeTypes.APPLICATION_SS
        else -> null
    }
}

internal fun isLikelyLiveStream(url: String): Boolean {
    return isLikelyLiveStream(analyzePlaybackUrl(url))
}

private fun isLikelyLiveStream(urlInfo: PlaybackUrlInfo): Boolean {
    val normalized = urlInfo.normalized
    if (normalized.isBlank()) return false

    if (looksLikeVodStream(urlInfo)) return false

    val inferredMimeType = inferMimeType(urlInfo)
    if (inferredMimeType == MimeTypes.APPLICATION_MPD || inferredMimeType == MimeTypes.APPLICATION_SS) {
        return false
    }

    if (
        normalized.startsWith("rtsp://") ||
        normalized.startsWith("rtmp://") ||
        normalized.startsWith("udp://")
    ) {
        return true
    }

    if (isXtreamLiveStreamUrl(urlInfo.trimmed)) {
        return true
    }

    return when {
        normalized.contains(".m3u8") -> true
        normalized.contains("format=m3u8") -> true
        normalized.contains("output=m3u8") -> true
        normalized.contains("/live/") -> true
        normalized.contains("type=live") -> true
        normalized.contains("stream=live") -> true
        normalized.contains("type=m3u_plus") -> true
        normalized.contains("action=live") -> true
        normalized.contains("action=get_live") -> true
        normalized.contains("output=ts") -> true
        urlInfo.isRemoteHttp && (urlInfo.path.endsWith(".ts") || normalized.contains(".ts?")) -> true
        else -> false
    }
}

internal fun shouldReconnectAfterEnd(url: String): Boolean {
    return shouldReconnectAfterEnd(analyzePlaybackUrl(url))
}

private fun shouldReconnectAfterEnd(urlInfo: PlaybackUrlInfo): Boolean {
    val normalized = urlInfo.normalized
    if (normalized.isBlank()) return false

    if (looksLikeVodStream(urlInfo)) return false

    if (urlInfo.isLocalResource) {
        return false
    }

    if (!urlInfo.isRemoteHttp) return false

    if (urlInfo.hasProgressiveVodExtension()) return false

    return when {
        isLikelyLiveStream(urlInfo) -> true
        normalized.contains("get.php") -> true
        normalized.contains("player_api.php") -> true
        normalized.contains("username=") && normalized.contains("password=") -> true
        inferMimeType(urlInfo) in setOf(MimeTypes.VIDEO_MP2T, MimeTypes.APPLICATION_MPD, MimeTypes.APPLICATION_SS) -> true
        normalized.contains(".ts") -> true
        normalized.contains("/live") -> true
        else -> false
    }
}

internal fun shouldApplyLivePlaybackHandling(url: String): Boolean {
    return shouldApplyLivePlaybackHandling(analyzePlaybackUrl(url))
}

private fun shouldApplyLivePlaybackHandling(urlInfo: PlaybackUrlInfo): Boolean {
    if (urlInfo.normalized.isBlank() || urlInfo.isLocalResource) return false
    if (looksLikeVodStream(urlInfo)) return false
    return isLikelyLiveStream(urlInfo) || shouldReconnectAfterEnd(urlInfo)
}

private fun shouldAutoReconnectOnFailure(urlInfo: PlaybackUrlInfo): Boolean {
    if (urlInfo.normalized.isBlank() || urlInfo.isLocalResource) return false
    return shouldApplyLivePlaybackHandling(urlInfo)
}

internal fun shouldRecoverEndedPlayback(url: String): Boolean {
    return shouldRecoverEndedPlayback(analyzePlaybackUrl(url))
}

private fun shouldRecoverEndedPlayback(urlInfo: PlaybackUrlInfo): Boolean {
    if (urlInfo.normalized.isBlank() || urlInfo.isLocalResource) return false
    if (looksLikeVodStream(urlInfo)) return false
    if (shouldApplyLivePlaybackHandling(urlInfo)) return true

    return when (urlInfo.scheme) {
        "http", "https", "rtsp", "rtmp", "udp" -> true
        else -> false
    }
}

private fun looksLikeVodStream(url: String): Boolean {
    return looksLikeVodStream(analyzePlaybackUrl(url))
}

private fun looksLikeVodStream(urlInfo: PlaybackUrlInfo): Boolean {
    val normalized = urlInfo.normalized
    if (normalized.isBlank()) return false

    if (isXtreamVodStreamUrl(urlInfo.trimmed)) return true

    return when {
        urlInfo.isLocalResource -> true
        normalized.contains("/movie/") -> true
        normalized.contains("/series/") -> true
        normalized.contains("/vod/") -> true
        normalized.contains("type=movie") -> true
        normalized.contains("type=vod") -> true
        normalized.contains("type=series") -> true
        normalized.contains("action=get_vod_stream") -> true
        normalized.contains("action=get_series_stream") -> true
        normalized.contains("action=get_series_info") -> true
        urlInfo.hasProgressiveVodExtension() -> true
        else -> false
    }
}

private fun reconnectDelayMillis(attempt: Int): Long {
    val safeAttempt = attempt.coerceAtLeast(1)
    val delayFactor = 1L shl (safeAttempt - 1).coerceAtMost(3)
    return (LiveReconnectInitialDelayMillis * delayFactor)
        .coerceAtMost(LiveReconnectMaxDelayMillis)
}

private fun Media.addPlaybackSpecificOptions(url: String) {
    val normalized = url.lowercase(Locale.ROOT)
    when {
        normalized.startsWith("rtsp://") -> {
            addOption(":rtsp-tcp")
            addOption(":network-caching=$VlcNetworkCachingMs")
        }
        normalized.contains(".m3u8") || normalized.contains("format=m3u8") || normalized.contains("output=m3u8") -> {
            addOption(":demux=hls")
            addOption(":network-caching=$VlcNetworkCachingMs")
            if (isLikelyLiveStream(url)) {
                addOption(":live-caching=$VlcLiveCachingMs")
            } else {
                addOption(":file-caching=$VlcFileCachingMs")
            }
        }
        else -> addOption(":input-repeat=0")
    }
}

private fun Media.addVlcHeaderOptions(headers: Map<String, String>) {
    headers.forEach { (name, value) ->
        if (value.isBlank()) return@forEach
        when (name.lowercase(Locale.ROOT)) {
            "user-agent" -> addOption(":http-user-agent=$value")
            "referer" -> addOption(":http-referrer=$value")
            "referrer" -> addOption(":http-referrer=$value")
            "cookie" -> addOption(":http-cookie=$value")
            else -> addOption(":http-header=$name: $value")
        }
    }
}

private fun Context.isDeviceInteractive(): Boolean {
    val powerManager = getSystemService(PowerManager::class.java) ?: return true
    return powerManager.isInteractive
}

private fun formatPlaybackError(error: PlaybackException): String {
    val message = error.localizedMessage?.trim().orEmpty()
    return when {
        message.contains("cleartext", ignoreCase = true) || message.contains("CLEARTEXT", ignoreCase = true) -> {
            "El canal usa HTTP y el dispositivo lo ha bloqueado"
        }
        message.contains("decoder init failed", ignoreCase = true) || message.contains("decod", ignoreCase = true) -> {
            "El formato de vídeo/audio no es compatible con esta caja Android"
        }
        message.contains("unable to connect", ignoreCase = true) || message.contains("connection", ignoreCase = true) -> {
            "No se pudo conectar con el servidor del canal"
        }
        message.contains("source error", ignoreCase = true) -> {
            "El origen del stream no pudo abrirse"
        }
        message.isNotBlank() -> message
        else -> "No se pudo reproducir este canal"
    }
}

@UnstableApi
private fun createExoLoadControl(): DefaultLoadControl {
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            ExoMinBufferMs,
            ExoMaxBufferMs,
            ExoBufferForPlaybackMs,
            ExoBufferForPlaybackAfterRebufferMs
        )
        .build()
}
