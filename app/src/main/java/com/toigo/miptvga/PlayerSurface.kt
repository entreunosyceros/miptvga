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
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.first

private const val LiveReconnectInitialDelayMillis = 2_000L
private const val LiveReconnectMaxDelayMillis = 30_000L
private const val MaxReconnectAttempts = 12
private const val MaxNetworkReconnectAttempts = 8
private const val MaxDecoderReconnectAttempts = 3
private const val MaxNonLiveReconnectAttempts = 3
private const val MaxXtreamReconnectAttempts = 12
private const val PointerMoveThresholdPx = 2f
private const val ExoMinBufferMs = 25_000
private const val ExoMaxBufferMs = 70_000
private const val ExoBufferForPlaybackMs = 2_500
private const val ExoBufferForPlaybackAfterRebufferMs = 6_000
private const val ExoLiveMinBufferMs = 20_000
private const val ExoLiveMaxBufferMs = 55_000
private const val ExoLiveBufferForPlaybackMs = 3_000
private const val ExoLiveBufferForPlaybackAfterRebufferMs = 6_000
private const val ExoVodMinBufferMs = 35_000
private const val ExoVodMaxBufferMs = 140_000
private const val ExoVodBufferForPlaybackMs = 3_500
private const val ExoVodBufferForPlaybackAfterRebufferMs = 8_000
private const val ExoLiveTargetOffsetMs = 4_000L
private const val ExoLiveMinPlaybackSpeed = 0.97f
private const val ExoLiveMaxPlaybackSpeed = 1.03f
private const val VlcNetworkCachingMs = 15_000
private const val VlcLiveCachingMs = 10_000
private const val VlcFileCachingMs = 6_000
private const val VlcXtreamNetworkCachingMs = 18_000
private const val VlcXtreamLiveCachingMs = 12_000
private const val VlcVodFileCachingMs = 10_000
private const val PlaybackSeekStepMillis = 10_000L
private const val PlaybackStatePollingMillis = 500L
private const val FullscreenTransitionGuardMillis = 1_100L
private const val FullscreenTransitionRecoveryCheckMillis = 900L
/** Replenish an application retry budget only after sustained healthy playback. */
private const val StablePlaybackBeforeRetryResetMillis = 8_000L
private val ProgressiveVodExtensions = setOf(
    ".mp4", ".mkv", ".avi", ".mov", ".mp3", ".aac", ".flac", ".wav", ".m4a", ".webm", ".ogg"
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlayerSurface(
    streamUrl: String,
    playbackRequestId: Long = 0L,
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
    onPlaybackEnded: () -> Unit,
    onPlaybackError: (PlaybackFailure) -> Unit,
    onPlaybackControllerStateChanged: (PlaybackControllerState) -> Unit,
    onPlaybackControllerActionsChanged: (PlaybackControllerActions) -> Unit
) {
    when (playbackBackend) {
        PlaybackBackend.VLC -> VlcPlayerSurface(
            streamUrl = streamUrl,
            playbackRequestId = playbackRequestId,
            requestHeaders = requestHeaders,
            videoCompatibilityMode = videoCompatibilityMode,
            fullscreenTransitionToken = fullscreenTransitionToken,
            controlsVisible = controlsVisible,
            onInputActivity = onInputActivity,
            onAutoHide = onAutoHide,
            onToggleFullscreen = onToggleFullscreen,
            onReconnectScheduled = onReconnectScheduled,
            onPlaybackStarted = onPlaybackStarted,
            onPlaybackEnded = onPlaybackEnded,
            onPlaybackError = onPlaybackError,
            onPlaybackControllerStateChanged = onPlaybackControllerStateChanged,
            onPlaybackControllerActionsChanged = onPlaybackControllerActionsChanged
        )

        PlaybackBackend.EXOPLAYER -> ExoPlayerSurface(
            streamUrl = streamUrl,
            playbackRequestId = playbackRequestId,
            requestHeaders = requestHeaders,
            videoCompatibilityMode = videoCompatibilityMode,
            fullscreenTransitionToken = fullscreenTransitionToken,
            controlsVisible = controlsVisible,
            onInputActivity = onInputActivity,
            onAutoHide = onAutoHide,
            onToggleFullscreen = onToggleFullscreen,
            onReconnectScheduled = onReconnectScheduled,
            onPlaybackStarted = onPlaybackStarted,
            onPlaybackEnded = onPlaybackEnded,
            onPlaybackError = onPlaybackError,
            onPlaybackControllerStateChanged = onPlaybackControllerStateChanged,
            onPlaybackControllerActionsChanged = onPlaybackControllerActionsChanged
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VlcPlayerSurface(
    streamUrl: String,
    playbackRequestId: Long = 0L,
    requestHeaders: Map<String, String>,
    videoCompatibilityMode: VideoCompatibilityMode,
    fullscreenTransitionToken: Int,
    controlsVisible: Boolean,
    onInputActivity: () -> Unit,
    onAutoHide: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onReconnectScheduled: () -> Unit,
    onPlaybackStarted: () -> Unit,
    onPlaybackEnded: () -> Unit,
    onPlaybackError: (PlaybackFailure) -> Unit,
    onPlaybackControllerStateChanged: (PlaybackControllerState) -> Unit,
    onPlaybackControllerActionsChanged: (PlaybackControllerActions) -> Unit
) {
    val applicationContext = LocalContext.current.applicationContext
    PlaybackTrace.enabled = applicationContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    val lifecycleOwner = LocalLifecycleOwner.current
    val playbackUrlInfo = remember(streamUrl) { analyzePlaybackUrl(streamUrl) }
    val currentOnInputActivity = rememberUpdatedState(onInputActivity)
    val currentOnToggleFullscreen = rememberUpdatedState(onToggleFullscreen)
    val currentOnReconnectScheduled = rememberUpdatedState(onReconnectScheduled)
    val currentOnPlaybackStarted = rememberUpdatedState(onPlaybackStarted)
    val currentOnPlaybackEnded = rememberUpdatedState(onPlaybackEnded)
    val currentOnPlaybackError = rememberUpdatedState(onPlaybackError)
    val currentOnPlaybackControllerStateChanged = rememberUpdatedState(onPlaybackControllerStateChanged)
    val currentOnPlaybackControllerActionsChanged = rememberUpdatedState(onPlaybackControllerActionsChanged)
    val currentStreamUrl = rememberUpdatedState(playbackUrlInfo.trimmed)
    val currentIsLiveStream = rememberUpdatedState(shouldApplyLivePlaybackHandling(playbackUrlInfo))
    val currentShouldReconnectAfterEnd = rememberUpdatedState(shouldReconnectAfterEnd(playbackUrlInfo))
    val currentShouldAutoReconnectOnFailure = rememberUpdatedState(shouldAutoReconnectOnFailure(playbackUrlInfo))
    val sessionGate = remember { PlaybackSessionGate() }
    val session = remember(streamUrl, requestHeaders, playbackRequestId) { PlaybackSessionGate.Session() }
    val trace = remember(session) { PlaybackTrace(playbackRequestId, "VLC") }
    val initialTransitionToken = remember(session) { fullscreenTransitionToken }
    val retryBudget = remember(session) { PlaybackRetryBudget(StablePlaybackBeforeRetryResetMillis) }
    val sessionPrepared = remember(session) { mutableStateOf(false) }
    val terminalReported = remember(session) { mutableStateOf(false) }
    val stopOnTerminal = remember(session) { mutableStateOf<() -> Unit>({}) }
    val userPaused = remember(session) { mutableStateOf(false) }
    val installSessionListener = remember(session) { mutableStateOf<() -> Unit>({}) }
    fun reportSessionError(detail: String?, allowBackendFallback: Boolean = true) {
        if (!sessionGate.isCurrent(session) || terminalReported.value) return
        trace.event(PlaybackEvent.TERMINAL_ERROR)
        terminalReported.value = true
        sessionPrepared.value = false
        runCatching { stopOnTerminal.value() }
        currentOnPlaybackError.value(PlaybackFailure(detail, retryBudget.attempts + 1, allowBackendFallback))
    }
    val reconnectAttempt = remember(streamUrl, requestHeaders, playbackRequestId) { mutableIntStateOf(0) }
    val reconnectToken = remember(streamUrl, requestHeaders, playbackRequestId) { mutableIntStateOf(0) }
    val reconnectPending = remember(streamUrl, requestHeaders, playbackRequestId) { mutableStateOf(false) }
    val fullscreenTransitionActive = remember(streamUrl, requestHeaders, playbackRequestId) { mutableStateOf(false) }
    // Do not key on streamUrl/headers: recomposition was resetting the surface and restarting playback.
    val surfaceReady = remember(videoCompatibilityMode) { mutableStateOf(false) }
    val attachedLayout = remember { arrayOfNulls<VLCVideoLayout>(1) }
    val pointerTracker = remember(streamUrl, requestHeaders, playbackRequestId) { PointerActivityTracker() }
    val playbackStateSnapshot = remember(streamUrl, requestHeaders, playbackRequestId) { mutableStateOf(PlaybackControllerState()) }
    val playbackActionSnapshot = remember(streamUrl, requestHeaders, playbackRequestId) { mutableStateOf(PlaybackControllerActions()) }

    fun requestSoftReconnect() {
        if (!sessionGate.isCurrent(session) || terminalReported.value || currentStreamUrl.value.isBlank() || reconnectPending.value) return
        if (!retryBudget.acquire(android.os.SystemClock.elapsedRealtime(), maxReconnectAttemptsForStream(currentStreamUrl.value, currentIsLiveStream.value))) {
            reportSessionError("Se ha agotado el presupuesto de reconexión")
            return
        }
        reconnectPending.value = true
        reconnectAttempt.intValue = retryBudget.attempts
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
    // Reuse one MediaPlayer: recreating it every zap caused decoder/surface thrash and buffering.
    // Connection close is handled by stop + media=null before opening the next URL.
    val mediaPlayer = remember(libVlc) {
        MediaPlayer(libVlc).apply {
            setVideoTrackEnabled(true)
        }
    }
    val stableRequestHeaders = remember(streamUrl, requestHeaders) { requestHeaders.toMap() }

    LaunchedEffect(fullscreenTransitionToken, session) {
        if (fullscreenTransitionToken == initialTransitionToken || fullscreenTransitionToken <= 0 || currentStreamUrl.value.isBlank()) return@LaunchedEffect

        fullscreenTransitionActive.value = true
        kotlinx.coroutines.delay(FullscreenTransitionRecoveryCheckMillis)
        if (
            fullscreenTransitionActive.value &&
            !mediaPlayer.isPlaying &&
            currentShouldAutoReconnectOnFailure.value && !userPaused.value
        ) {
            requestSoftReconnect()
        }
        kotlinx.coroutines.delay((FullscreenTransitionGuardMillis - FullscreenTransitionRecoveryCheckMillis).coerceAtLeast(0L))
        fullscreenTransitionActive.value = false
    }

    DisposableEffect(mediaPlayer, session) {
        sessionGate.activate(session)
        trace.event(PlaybackEvent.SESSION_ACTIVE)
        stopOnTerminal.value = { trace.stop { releaseVlcPlaybackSession(mediaPlayer, trace) }; Unit }
        val playbackActions = PlaybackControllerActions(
            togglePlayPause = playbackAction@{
                if (!sessionGate.isCurrent(session) || terminalReported.value) return@playbackAction
                if (!sessionPrepared.value) { userPaused.value = !userPaused.value; return@playbackAction }
                if (mediaPlayer.isPlaying) {
                    userPaused.value = true
                    retryBudget.paused(android.os.SystemClock.elapsedRealtime())
                    mediaPlayer.pause()
                } else {
                    userPaused.value = false
                    mediaPlayer.play()
                }
            },
            play = playbackAction@{
                if (!sessionGate.isCurrent(session) || terminalReported.value) return@playbackAction
                userPaused.value = false
                if (!sessionPrepared.value) return@playbackAction
                mediaPlayer.play()
            },
            pause = playbackAction@{
                if (!sessionGate.isCurrent(session) || terminalReported.value) return@playbackAction
                userPaused.value = true
                retryBudget.paused(android.os.SystemClock.elapsedRealtime())
                mediaPlayer.pause()
            },
            seekBack = playbackAction@{
                if (!sessionGate.isCurrent(session) || terminalReported.value || !sessionPrepared.value) return@playbackAction
                if (mediaPlayer.isSeekable()) {
                    val targetTime = (mediaPlayer.time - PlaybackSeekStepMillis).coerceAtLeast(0L)
                    mediaPlayer.time = targetTime
                }
            },
            seekForward = playbackAction@{
                if (!sessionGate.isCurrent(session) || terminalReported.value || !sessionPrepared.value) return@playbackAction
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
            if (!sessionGate.isCurrent(session) || terminalReported.value) return@EventListener
            when (event.type) {
                MediaPlayer.Event.Opening -> trace.event(PlaybackEvent.OPENING)
                MediaPlayer.Event.Playing -> {
                    trace.event(PlaybackEvent.PLAYING)
                    retryBudget.playing(android.os.SystemClock.elapsedRealtime())
                    reconnectPending.value = false
                    fullscreenTransitionActive.value = false
                    currentOnPlaybackStarted.value()
                }

                MediaPlayer.Event.Paused -> { retryBudget.paused(android.os.SystemClock.elapsedRealtime()) }

                MediaPlayer.Event.EndReached -> {
                    if (reconnectPending.value) return@EventListener
                    val shouldReconnect =
                        shouldRecoverEndedPlayback(playbackUrlInfo) &&
                            currentStreamUrl.value.isNotBlank() &&
                            !reconnectPending.value

                    if (shouldReconnect) {
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (!retryBudget.acquire(now, maxReconnectAttemptsForStream(currentStreamUrl.value, currentIsLiveStream.value))) {
                            reportSessionError("Se ha agotado el presupuesto de reconexión")
                            return@EventListener
                        }
                        // Drop the slot, then reopen. Debounced so stalls don't look like buffering loops.
                        releaseVlcPlaybackSession(mediaPlayer, trace)
                        reconnectPending.value = true
                        reconnectAttempt.intValue = retryBudget.attempts
                        reconnectToken.intValue += 1
                        currentOnReconnectScheduled.value()
                    } else {
                        releaseVlcPlaybackSession(mediaPlayer, trace)
                        currentOnPlaybackEnded.value()
                    }
                }

                MediaPlayer.Event.EncounteredError -> {
                    trace.event(PlaybackEvent.ENGINE_ERROR)
                    if (reconnectPending.value) return@EventListener
                    val maxAttempts = maxReconnectAttemptsForStream(
                        url = currentStreamUrl.value,
                        isLive = currentIsLiveStream.value
                    )
                    if (
                        currentShouldAutoReconnectOnFailure.value &&
                        currentStreamUrl.value.isNotBlank() &&
                        !reconnectPending.value &&
                        retryBudget.acquire(android.os.SystemClock.elapsedRealtime(), maxAttempts)
                    ) {
                        reconnectPending.value = true
                        reconnectAttempt.intValue = retryBudget.attempts
                        reconnectToken.intValue += 1
                        currentOnReconnectScheduled.value()
                    } else {
                        reportSessionError("Error al reproducir con VLC")
                    }
                }

                else -> Unit
            }
        }
        installSessionListener.value = { mediaPlayer.setEventListener(listener) }
        onDispose {
            val owned = sessionGate.retire(session) {
                mediaPlayer.setEventListener(null)
                trace.stop { releaseVlcPlaybackSession(mediaPlayer, trace) }
            }
            trace.event(PlaybackEvent.SESSION_RETIRED)
            if (owned) {
                currentOnPlaybackControllerActionsChanged.value(PlaybackControllerActions())
                currentOnPlaybackControllerStateChanged.value(PlaybackControllerState())
            }
        }
    }

    DisposableEffect(mediaPlayer, libVlc) {
        onDispose {
            mediaPlayer.setEventListener(null)
            releaseVlcPlaybackSession(mediaPlayer, trace)
            runCatching { mediaPlayer.detachViews() }
            runCatching { mediaPlayer.release() }
            runCatching { libVlc.release() }
        }
    }

    LaunchedEffect(mediaPlayer, session) {
        while (true) {
            val playbackState = if (sessionPrepared.value) PlaybackControllerState(
                isPlaying = mediaPlayer.isPlaying,
                canPause = mediaPlayer.isPlaying || currentStreamUrl.value.isNotBlank(),
                canSeek = mediaPlayer.isSeekable() && mediaPlayer.length > 0L,
                isLive = currentIsLiveStream.value,
                positionMs = mediaPlayer.time.coerceAtLeast(0L),
                durationMs = mediaPlayer.length.coerceAtLeast(0L)
            ) else PlaybackControllerState()
            playbackStateSnapshot.value = playbackState
            currentOnPlaybackControllerStateChanged.value(playbackState)
            kotlinx.coroutines.delay(PlaybackStatePollingMillis)
        }
    }

    DisposableEffect(lifecycleOwner, mediaPlayer, applicationContext, session) {
        var shouldResumeOnStart = mediaPlayer.isPlaying
        var playbackStoppedForSleep = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    retryBudget.paused(android.os.SystemClock.elapsedRealtime())
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
                        releaseVlcPlaybackSession(mediaPlayer, trace)
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (!terminalReported.value && !playbackStoppedForSleep && shouldResumeOnStart) {
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

    LaunchedEffect(mediaPlayer, session, reconnectToken.intValue) {
        try {
            val sanitizedUrl = streamUrl.trim()
            if (sanitizedUrl.isBlank()) {
                releaseVlcPlaybackSession(mediaPlayer, trace)
                return@LaunchedEffect
            }

            if (!sessionGate.isCurrent(session) || terminalReported.value) return@LaunchedEffect
            mediaPlayer.setEventListener(null)
            sessionPrepared.value = false
            if (!trace.stop { check(releaseVlcPlaybackSession(mediaPlayer, trace)) }) {
                reportSessionError("No se pudo detener la reproducción anterior", allowBackendFallback = false)
                return@LaunchedEffect
            }
            // Wait for the video surface without putting surfaceReady in the effect keys
            // (that was restarting playback and looked like buffering every few seconds).
            snapshotFlow { surfaceReady.value }.first { it }

            val shouldDelayReconnect = reconnectPending.value && reconnectAttempt.intValue > 0
            if (shouldDelayReconnect) {
                val delayMs = reconnectDelayMillis(reconnectAttempt.intValue, isXtreamStreamUrl(sanitizedUrl))
                trace.event(PlaybackEvent.RETRY_WAIT, reconnectAttempt.intValue, delayMs)
                kotlinx.coroutines.delay(delayMs)
            }

            if (shouldDelayReconnect && !reconnectPending.value) return@LaunchedEffect
            lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            snapshotFlow { !userPaused.value || terminalReported.value }.first { it }
            if (!sessionGate.isCurrent(session) || terminalReported.value) return@LaunchedEffect
            trace.event(PlaybackEvent.PREPARE, reconnectAttempt.intValue)
            runCatching {
                reconnectPending.value = false
                val media = Media(libVlc, normalizePlaybackUri(sanitizedUrl))
                try {
                    media.setHWDecoderEnabled(true, false)
                    media.addOption(":http-reconnect=true")
                    media.addOption(":network-caching=$VlcNetworkCachingMs")
                    media.addOption(":live-caching=$VlcLiveCachingMs")
                    media.addOption(":file-caching=$VlcFileCachingMs")
                    media.addVlcHeaderOptions(stableRequestHeaders)
                    media.addPlaybackSpecificOptions(sanitizedUrl)
                    mediaPlayer.media = media
                    installSessionListener.value()
                } finally {
                    media.release()
                }
                sessionPrepared.value = true
                trace.event(PlaybackEvent.PLAY_REQUEST)
                mediaPlayer.play()
            }.onFailure { error ->
                trace.event(PlaybackEvent.START_ERROR)
                val maxAttempts = maxReconnectAttemptsForStream(
                    url = currentStreamUrl.value,
                    isLive = currentIsLiveStream.value
                )
                if (
                    currentShouldAutoReconnectOnFailure.value &&
                    currentStreamUrl.value.isNotBlank() &&
                    !reconnectPending.value &&
                    retryBudget.acquire(android.os.SystemClock.elapsedRealtime(), maxAttempts)
                ) {
                    reconnectPending.value = true
                    reconnectAttempt.intValue = retryBudget.attempts
                    reconnectToken.intValue += 1
                    currentOnReconnectScheduled.value()
                } else {
                    reportSessionError(error.localizedMessage ?: error.message ?: "Error al iniciar la reproducción")
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            trace.event(PlaybackEvent.PREPARATION_TASK_CANCELLED)
            throw cancelled
        } finally {
            trace.event(PlaybackEvent.PREPARATION_TASK_FINISHED)
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
                    runCatching {
                        mediaPlayer.attachViews(this, null, false, false)
                        attachedLayout[0] = this
                        surfaceReady.value = true
                    }
                    setOnTouchListener { view, event ->
                        handleMouseMotionEvent(view, event, pointerTracker, currentOnInputActivity.value)
                    }
                    setOnGenericMotionListener { _, event ->
                        handleGenericMouseMotionEvent(event, pointerTracker, currentOnInputActivity.value)
                    }
                }
            },
            onReset = null,
            onRelease = { videoLayout ->
                videoLayout.setOnTouchListener(null)
                videoLayout.setOnGenericMotionListener(null)
                if (attachedLayout[0] === videoLayout) {
                    runCatching { mediaPlayer.detachViews() }
                    attachedLayout[0] = null
                    surfaceReady.value = false
                }
            },
            update = { videoLayout ->
                videoLayout.keepScreenOn = true
                if (!surfaceReady.value) {
                    runCatching {
                        mediaPlayer.detachViews()
                        mediaPlayer.attachViews(videoLayout, null, false, false)
                        attachedLayout[0] = videoLayout
                        surfaceReady.value = true
                    }
                }
            }
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ExoPlayerSurface(
    streamUrl: String,
    playbackRequestId: Long = 0L,
    requestHeaders: Map<String, String>,
    videoCompatibilityMode: VideoCompatibilityMode,
    fullscreenTransitionToken: Int,
    controlsVisible: Boolean,
    onInputActivity: () -> Unit,
    onAutoHide: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onReconnectScheduled: () -> Unit,
    onPlaybackStarted: () -> Unit,
    onPlaybackEnded: () -> Unit,
    onPlaybackError: (PlaybackFailure) -> Unit,
    onPlaybackControllerStateChanged: (PlaybackControllerState) -> Unit,
    onPlaybackControllerActionsChanged: (PlaybackControllerActions) -> Unit
) {
    val applicationContext = LocalContext.current.applicationContext
    PlaybackTrace.enabled = applicationContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    val lifecycleOwner = LocalLifecycleOwner.current
    val playbackUrlInfo = remember(streamUrl) { analyzePlaybackUrl(streamUrl) }
    val currentOnInputActivity = rememberUpdatedState(onInputActivity)
    val currentOnToggleFullscreen = rememberUpdatedState(onToggleFullscreen)
    val currentOnReconnectScheduled = rememberUpdatedState(onReconnectScheduled)
    val currentOnPlaybackStarted = rememberUpdatedState(onPlaybackStarted)
    val currentOnPlaybackEnded = rememberUpdatedState(onPlaybackEnded)
    val currentOnPlaybackError = rememberUpdatedState(onPlaybackError)
    val currentOnPlaybackControllerStateChanged = rememberUpdatedState(onPlaybackControllerStateChanged)
    val currentOnPlaybackControllerActionsChanged = rememberUpdatedState(onPlaybackControllerActionsChanged)
    val currentStreamUrl = rememberUpdatedState(playbackUrlInfo.trimmed)
    val currentIsLiveStream = rememberUpdatedState(shouldApplyLivePlaybackHandling(playbackUrlInfo))
    val currentShouldReconnectAfterEnd = rememberUpdatedState(shouldReconnectAfterEnd(playbackUrlInfo))
    val currentShouldAutoReconnectOnFailure = rememberUpdatedState(shouldAutoReconnectOnFailure(playbackUrlInfo))
    val sessionGate = remember { PlaybackSessionGate() }
    val session = remember(streamUrl, requestHeaders, playbackRequestId) { PlaybackSessionGate.Session() }
    val trace = remember(session) { PlaybackTrace(playbackRequestId, "EXOPLAYER") }
    val initialTransitionToken = remember(session) { fullscreenTransitionToken }
    val retryBudget = remember(session) { PlaybackRetryBudget(StablePlaybackBeforeRetryResetMillis) }
    val sessionPrepared = remember(session) { mutableStateOf(false) }
    val terminalReported = remember(session) { mutableStateOf(false) }
    val stopOnTerminal = remember(session) { mutableStateOf<() -> Unit>({}) }
    val userPaused = remember(session) { mutableStateOf(false) }
    fun reportSessionError(detail: String?, allowBackendFallback: Boolean = true) {
        if (!sessionGate.isCurrent(session) || terminalReported.value) return
        trace.event(PlaybackEvent.TERMINAL_ERROR)
        terminalReported.value = true
        sessionPrepared.value = false
        runCatching { stopOnTerminal.value() }
        currentOnPlaybackError.value(PlaybackFailure(detail, retryBudget.attempts + 1, allowBackendFallback))
    }
    val reconnectAttempt = remember(streamUrl, requestHeaders, playbackRequestId) { mutableIntStateOf(0) }
    val reconnectToken = remember(streamUrl, requestHeaders, playbackRequestId) { mutableIntStateOf(0) }
    val reconnectPending = remember(streamUrl, requestHeaders, playbackRequestId) { mutableStateOf(false) }
    val fullscreenTransitionActive = remember(streamUrl, requestHeaders, playbackRequestId) { mutableStateOf(false) }
    val pointerTracker = remember(streamUrl, requestHeaders, playbackRequestId) { PointerActivityTracker() }
    val playbackStateSnapshot = remember(streamUrl, requestHeaders, playbackRequestId) { mutableStateOf(PlaybackControllerState()) }
    val playbackActionSnapshot = remember(streamUrl, requestHeaders, playbackRequestId) { mutableStateOf(PlaybackControllerActions()) }
    val playerAudioAttributes = remember {
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }
    val usePlaybackCache = remember(streamUrl) { shouldUsePlaybackCache(streamUrl) }
    val effectiveRequestHeaders = remember(requestHeaders) { ensureIptvHeaders(requestHeaders) }
    val stableRequestHeaders = remember(streamUrl, effectiveRequestHeaders) { effectiveRequestHeaders }
    // Keep one ExoPlayer instance across zapping. Recreating per URL briefly overlaps
    // two network sessions and trips single-connection IPTV panels.
    val exoPlayer = remember(applicationContext) {
        ExoPlayer.Builder(applicationContext)
            .setRenderersFactory(
                DefaultRenderersFactory(applicationContext)
                    .setEnableDecoderFallback(true)
            )
            .setLoadControl(createExoLoadControl())
            .build().apply {
                setAudioAttributes(playerAudioAttributes, true)
                setWakeMode(C.WAKE_MODE_NETWORK)
                volume = 1f
                playWhenReady = false
            }
    }

    fun requestSoftReconnect() {
        if (!sessionGate.isCurrent(session) || terminalReported.value || currentStreamUrl.value.isBlank() || reconnectPending.value) return
        if (!retryBudget.acquire(android.os.SystemClock.elapsedRealtime(), maxReconnectAttemptsForStream(currentStreamUrl.value, currentIsLiveStream.value))) {
            reportSessionError("Se ha agotado el presupuesto de reconexión")
            return
        }
        reconnectPending.value = true
        reconnectAttempt.intValue = retryBudget.attempts
        reconnectToken.intValue += 1
    }

    LaunchedEffect(fullscreenTransitionToken, session) {
        if (fullscreenTransitionToken == initialTransitionToken || fullscreenTransitionToken <= 0 || currentStreamUrl.value.isBlank()) return@LaunchedEffect

        fullscreenTransitionActive.value = true
        kotlinx.coroutines.delay(FullscreenTransitionRecoveryCheckMillis)
        val needsRecovery =
            fullscreenTransitionActive.value &&
                !exoPlayer.isPlaying &&
                currentShouldAutoReconnectOnFailure.value && !userPaused.value &&
                exoPlayer.playbackState != Player.STATE_READY
        if (needsRecovery) {
            requestSoftReconnect()
        }
        kotlinx.coroutines.delay((FullscreenTransitionGuardMillis - FullscreenTransitionRecoveryCheckMillis).coerceAtLeast(0L))
        fullscreenTransitionActive.value = false
    }

    DisposableEffect(exoPlayer, session) {
        sessionGate.activate(session)
        trace.event(PlaybackEvent.SESSION_ACTIVE)
        stopOnTerminal.value = {
            trace.stop { exoPlayer.playWhenReady = false; exoPlayer.stop(); exoPlayer.clearMediaItems() }
            Unit
        }
        val playbackActions = PlaybackControllerActions(
            togglePlayPause = playbackAction@{
                if (!sessionGate.isCurrent(session) || terminalReported.value) return@playbackAction
                if (!sessionPrepared.value) { userPaused.value = !userPaused.value; return@playbackAction }
                if (exoPlayer.isPlaying) {
                    userPaused.value = true
                    retryBudget.paused(android.os.SystemClock.elapsedRealtime())
                    exoPlayer.pause()
                } else {
                    userPaused.value = false
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
            },
            play = playbackAction@{
                if (!sessionGate.isCurrent(session) || terminalReported.value) return@playbackAction
                userPaused.value = false
                if (!sessionPrepared.value) return@playbackAction
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            },
            pause = playbackAction@{
                if (!sessionGate.isCurrent(session) || terminalReported.value) return@playbackAction
                userPaused.value = true
                retryBudget.paused(android.os.SystemClock.elapsedRealtime())
                exoPlayer.pause()
            },
            seekBack = playbackAction@{
                if (!sessionGate.isCurrent(session) || terminalReported.value || !sessionPrepared.value) return@playbackAction
                if (exoPlayer.isCurrentMediaItemSeekable) {
                    exoPlayer.seekTo((exoPlayer.currentPosition - PlaybackSeekStepMillis).coerceAtLeast(0L))
                }
            },
            seekForward = playbackAction@{
                if (!sessionGate.isCurrent(session) || terminalReported.value || !sessionPrepared.value) return@playbackAction
                if (exoPlayer.isCurrentMediaItemSeekable) {
                    val duration = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                    exoPlayer.seekTo((exoPlayer.currentPosition + PlaybackSeekStepMillis).coerceAtMost(duration))
                }
            }
        )
        playbackActionSnapshot.value = playbackActions
        currentOnPlaybackControllerActionsChanged.value(playbackActions)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!sessionGate.isCurrent(session) || terminalReported.value ||
                    exoPlayer.currentMediaItem?.mediaId != playbackRequestId.toString()) return
                if (isPlaying) trace.event(PlaybackEvent.PLAYING)
                if (isPlaying) retryBudget.playing(android.os.SystemClock.elapsedRealtime())
                else retryBudget.paused(android.os.SystemClock.elapsedRealtime())
            }
            override fun onRenderedFirstFrame() {
                if (sessionGate.isCurrent(session) && !terminalReported.value && sessionPrepared.value &&
                    exoPlayer.currentMediaItem?.mediaId == playbackRequestId.toString())
                    trace.event(PlaybackEvent.FIRST_FRAME)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!sessionGate.isCurrent(session) || terminalReported.value ||
                    exoPlayer.currentMediaItem?.mediaId != playbackRequestId.toString()) return
                if (playbackState == Player.STATE_READY) {
                    trace.event(PlaybackEvent.READY)
                    // READY is not proof of sustained playback; onIsPlayingChanged tracks it.
                    reconnectPending.value = false
                    fullscreenTransitionActive.value = false
                    currentOnPlaybackStarted.value()
                } else if (playbackState == Player.STATE_ENDED) {
                    if (reconnectPending.value) return
                    val shouldReconnect =
                        shouldRecoverEndedPlayback(playbackUrlInfo) &&
                            currentStreamUrl.value.isNotBlank() &&
                            !reconnectPending.value
                    if (shouldReconnect) {
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (!retryBudget.acquire(now, maxReconnectAttemptsForStream(currentStreamUrl.value, currentIsLiveStream.value))) {
                            reportSessionError("Se ha agotado el presupuesto de reconexión")
                            return
                        }
                        runCatching {
                            exoPlayer.playWhenReady = false
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                        }
                        reconnectPending.value = true
                        reconnectAttempt.intValue = retryBudget.attempts
                        reconnectToken.intValue += 1
                        currentOnReconnectScheduled.value()
                    } else {
                        runCatching {
                            exoPlayer.playWhenReady = false
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                        }
                        currentOnPlaybackEnded.value()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (sessionGate.isCurrent(session)) trace.event(PlaybackEvent.ENGINE_ERROR, error.errorCode)
                if (reconnectPending.value) return
                if (!sessionGate.isCurrent(session) || terminalReported.value ||
                    exoPlayer.currentMediaItem?.mediaId != playbackRequestId.toString()) return
                if (isPermanentPlaybackFailure(error)) {
                    reportSessionError("El servidor ha rechazado esta reproducción", allowBackendFallback = false)
                    return
                }
                val maxAttempts = maxReconnectAttemptsForStream(
                    url = currentStreamUrl.value,
                    isLive = currentIsLiveStream.value,
                    error = error
                )
                if (
                    currentShouldAutoReconnectOnFailure.value &&
                    currentStreamUrl.value.isNotBlank() &&
                    !reconnectPending.value &&
                    retryBudget.acquire(android.os.SystemClock.elapsedRealtime(), maxAttempts)
                ) {
                    reconnectPending.value = true
                    reconnectAttempt.intValue = retryBudget.attempts
                    reconnectToken.intValue += 1
                    currentOnReconnectScheduled.value()
                } else {
                    reportSessionError(formatPlaybackError(error))
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            val owned = sessionGate.retire(session) {
                trace.stop {
                    exoPlayer.playWhenReady = false
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                }
            }
            trace.event(PlaybackEvent.SESSION_RETIRED)
            if (owned) {
                currentOnPlaybackControllerActionsChanged.value(PlaybackControllerActions())
                currentOnPlaybackControllerStateChanged.value(PlaybackControllerState())
            }
        }
    }

    DisposableEffect(exoPlayer) {
        PlaybackCache.acquireReader()
        onDispose {
            try {
                runCatching {
                    exoPlayer.playWhenReady = false
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                }
            } finally {
                try { exoPlayer.release() }
                finally { PlaybackCache.releaseReader(applicationContext) }
            }
        }
    }

    LaunchedEffect(exoPlayer, session) {
        while (true) {
            val playbackState = if (sessionPrepared.value) PlaybackControllerState(
                isPlaying = exoPlayer.isPlaying,
                canPause = exoPlayer.mediaItemCount > 0,
                canSeek = exoPlayer.isCurrentMediaItemSeekable && exoPlayer.duration > 0L,
                isLive = currentIsLiveStream.value || exoPlayer.isCurrentMediaItemLive,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                durationMs = exoPlayer.duration.takeIf { it > 0L }?.coerceAtLeast(0L) ?: 0L
            ) else PlaybackControllerState()
            playbackStateSnapshot.value = playbackState
            currentOnPlaybackControllerStateChanged.value(playbackState)
            kotlinx.coroutines.delay(PlaybackStatePollingMillis)
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer, applicationContext, session) {
        var shouldResumeOnStart = exoPlayer.playWhenReady
        var playbackStoppedForSleep = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    retryBudget.paused(android.os.SystemClock.elapsedRealtime())
                    shouldResumeOnStart = exoPlayer.isPlaying || exoPlayer.playWhenReady
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_STOP -> {
                    if (!applicationContext.isDeviceInteractive()) {
                        playbackStoppedForSleep = true
                        shouldResumeOnStart = false
                        reconnectPending.value = false
                        reconnectAttempt.intValue = 0
                        exoPlayer.playWhenReady = false
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (!terminalReported.value && !playbackStoppedForSleep && shouldResumeOnStart && exoPlayer.mediaItemCount > 0) {
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

    LaunchedEffect(exoPlayer, session, reconnectToken.intValue) {
        try {
            val sanitizedUrl = streamUrl.trim()
            if (sanitizedUrl.isBlank()) {
                exoPlayer.playWhenReady = false
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                return@LaunchedEffect
            }

            if (!sessionGate.isCurrent(session) || terminalReported.value) return@LaunchedEffect
            sessionPrepared.value = false
            if (!trace.stop {
                exoPlayer.playWhenReady = false
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }) {
                reportSessionError("No se pudo detener la reproducción anterior", allowBackendFallback = false)
                return@LaunchedEffect
            }

            val shouldDelayReconnect = reconnectPending.value && reconnectAttempt.intValue > 0
            if (shouldDelayReconnect) {
                val delayMs = reconnectDelayMillis(reconnectAttempt.intValue, isXtreamStreamUrl(sanitizedUrl))
                trace.event(PlaybackEvent.RETRY_WAIT, reconnectAttempt.intValue, delayMs)
                kotlinx.coroutines.delay(delayMs)
            }

            if (shouldDelayReconnect && !reconnectPending.value) return@LaunchedEffect
            lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            snapshotFlow { !userPaused.value || terminalReported.value }.first { it }
            if (!sessionGate.isCurrent(session) || terminalReported.value) return@LaunchedEffect
            trace.event(PlaybackEvent.PREPARE, reconnectAttempt.intValue)
            runCatching {
                reconnectPending.value = false
                val dataSourceFactory = PlaybackCache.createDataSourceFactory(
                    context = applicationContext,
                    requestHeaders = stableRequestHeaders,
                    useCache = usePlaybackCache,
                    trace = trace
                )
                val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
                    .createMediaSource(buildMediaItem(sanitizedUrl, playbackUrlInfo).buildUpon()
                        .setMediaId(playbackRequestId.toString()).build())
                exoPlayer.setMediaSource(mediaSource, /* resetPosition = */ true)
                sessionPrepared.value = true
                trace.event(PlaybackEvent.PLAY_REQUEST)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            }.onFailure { error ->
                trace.event(PlaybackEvent.START_ERROR)
                val maxAttempts = maxReconnectAttemptsForStream(
                    url = currentStreamUrl.value,
                    isLive = currentIsLiveStream.value
                )
                if (
                    currentShouldAutoReconnectOnFailure.value &&
                    currentStreamUrl.value.isNotBlank() &&
                    !reconnectPending.value &&
                    retryBudget.acquire(android.os.SystemClock.elapsedRealtime(), maxAttempts)
                ) {
                    reconnectPending.value = true
                    reconnectAttempt.intValue = retryBudget.attempts
                    reconnectToken.intValue += 1
                    currentOnReconnectScheduled.value()
                } else {
                    reportSessionError(error.localizedMessage ?: error.message)
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            trace.event(PlaybackEvent.PREPARATION_TASK_CANCELLED)
            throw cancelled
        } finally {
            trace.event(PlaybackEvent.PREPARATION_TASK_FINISHED)
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
            onReset = null,
            onRelease = { playerView ->
                playerView.player = null
                playerView.setOnTouchListener(null)
                playerView.setOnGenericMotionListener(null)
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
        isXtreamStreamUrl(urlInfo.trimmed) -> false
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
        normalized.contains(".ism") || (normalized.contains("manifest") && normalized.contains("format=ism")) -> MimeTypes.APPLICATION_SS
        // Do not force MPEG-TS for generic Xtream URLs: many panels serve HLS or redirect.
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

    // A generic get.php URL can describe a catalogue or redirect to VOD. Keep
    // recovery policy separate from evidence that the stream is actually live.
    if (normalized.contains("get.php") &&
        !normalized.contains("type=m3u_plus") &&
        !normalized.contains("type=live") &&
        !normalized.contains("output=ts") &&
        !normalized.contains("output=m3u8") &&
        !normalized.contains("action=get_live")) return false

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
        normalized.contains("get.php") -> true
        isXtreamStreamUrl(urlInfo.trimmed) && !looksLikeVodStream(urlInfo) -> true
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
    if (isXtreamStreamUrl(urlInfo.trimmed)) return true
    if (shouldApplyLivePlaybackHandling(urlInfo)) return true
    return urlInfo.isRemoteHttp
}

internal fun shouldRecoverEndedPlayback(url: String): Boolean {
    return shouldRecoverEndedPlayback(analyzePlaybackUrl(url))
}

private fun shouldRecoverEndedPlayback(urlInfo: PlaybackUrlInfo): Boolean {
    if (urlInfo.normalized.isBlank() || urlInfo.isLocalResource) return false
    if (looksLikeVodStream(urlInfo) && !isXtreamLiveStreamUrl(urlInfo.trimmed)) return false
    if (isXtreamLiveStreamUrl(urlInfo.trimmed)) return true
    if (shouldApplyLivePlaybackHandling(urlInfo)) return true
    // Generic remote files should advance/end, not reconnect (reconnect looked like buffering).
    return false
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

private fun reconnectDelayMillis(attempt: Int, xtream: Boolean = false): Long {
    val safeAttempt = attempt.coerceAtLeast(1)
    val initial = if (xtream) 1_500L else LiveReconnectInitialDelayMillis
    val maxDelay = if (xtream) 20_000L else LiveReconnectMaxDelayMillis
    val baseDelay = initial * (1L shl (safeAttempt - 1).coerceAtMost(4))
    val jitter = (baseDelay * 0.2 * Math.random()).toLong()
    return (baseDelay + jitter).coerceAtMost(maxDelay)
}

private fun releaseVlcPlaybackSession(mediaPlayer: MediaPlayer, trace: PlaybackTrace): Boolean {
    val stopped = runCatching { mediaPlayer.stop() }.onFailure { trace.event(PlaybackEvent.STOP_FAILED) }
    val cleared = runCatching { mediaPlayer.media = null }.onFailure { trace.event(PlaybackEvent.STOP_FAILED) }
    return stopped.isSuccess && cleared.isSuccess
}

private fun maxReconnectAttemptsForStream(url: String, isLive: Boolean, error: PlaybackException? = null): Int {
    if (isXtreamStreamUrl(url)) return MaxXtreamReconnectAttempts
    if (!isLive) return MaxNonLiveReconnectAttempts
    return maxReconnectAttemptsForError(error)
}

private fun isDecoderError(error: PlaybackException): Boolean {
    val message = error.localizedMessage.orEmpty() + error.message.orEmpty()
    return message.contains("decoder", ignoreCase = true) ||
        message.contains("codec", ignoreCase = true) ||
        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
}

private fun maxReconnectAttemptsForError(error: PlaybackException?): Int {
    if (error == null) return MaxReconnectAttempts
    return if (isDecoderError(error)) MaxDecoderReconnectAttempts else MaxNetworkReconnectAttempts
}

private fun Media.addPlaybackSpecificOptions(url: String) {
    val normalized = url.lowercase(Locale.ROOT)
    val isXtream = isXtreamStreamUrl(url)
    val isLive = isLikelyLiveStream(url) || isXtreamLiveStreamUrl(url)
    val isVod = looksLikeVodStream(url)
    val networkCaching = if (isXtream) VlcXtreamNetworkCachingMs else VlcNetworkCachingMs
    val liveCaching = if (isXtream) VlcXtreamLiveCachingMs else VlcLiveCachingMs
    val fileCaching = if (isVod) VlcVodFileCachingMs else VlcFileCachingMs
    when {
        normalized.startsWith("rtsp://") -> {
            addOption(":rtsp-tcp")
            addOption(":network-caching=$networkCaching")
        }
        // Only force demux when the URL clearly indicates the format.
        normalized.contains(".m3u8") || normalized.contains("format=m3u8") || normalized.contains("output=m3u8") -> {
            addOption(":demux=hls")
            addOption(":network-caching=$networkCaching")
            if (isLive) {
                addOption(":live-caching=$liveCaching")
            } else {
                addOption(":file-caching=$fileCaching")
            }
        }
        normalized.contains("output=ts") || normalized.endsWith(".ts") || normalized.contains(".ts?") -> {
            addOption(":demux=ts")
            addOption(":network-caching=$networkCaching")
            addOption(":live-caching=$liveCaching")
        }
        isLive || isXtream -> {
            addOption(":network-caching=$networkCaching")
            addOption(":live-caching=$liveCaching")
            addOption(":http-reconnect")
        }
        else -> {
            addOption(":network-caching=$networkCaching")
            addOption(":file-caching=$fileCaching")
            addOption(":input-repeat=0")
        }
    }
}

private fun Media.addVlcHeaderOptions(headers: Map<String, String>) {
    val effectiveHeaders = ensureIptvHeaders(headers)
    effectiveHeaders.forEach { (name, value) ->
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

@androidx.annotation.OptIn(UnstableApi::class)
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

@androidx.annotation.OptIn(UnstableApi::class)
internal fun createExoLoadControlForStream(streamUrl: String): DefaultLoadControl {
    val urlInfo = analyzePlaybackUrl(streamUrl)
    return when {
        shouldApplyLivePlaybackHandling(urlInfo) -> DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                ExoLiveMinBufferMs,
                ExoLiveMaxBufferMs,
                ExoLiveBufferForPlaybackMs,
                ExoLiveBufferForPlaybackAfterRebufferMs
            )
            .build()

        looksLikeVodStream(urlInfo) -> DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                ExoVodMinBufferMs,
                ExoVodMaxBufferMs,
                ExoVodBufferForPlaybackMs,
                ExoVodBufferForPlaybackAfterRebufferMs
            )
            .build()

        else -> createExoLoadControl()
    }
}

private fun isPermanentPlaybackFailure(error: Throwable): Boolean {
    var cause: Throwable? = error
    repeat(8) {
        val current = cause ?: return false
        if (current is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
            return isPermanentPlaybackHttpStatus(current.responseCode)
        }
        cause = current.cause
    }
    return false
}
