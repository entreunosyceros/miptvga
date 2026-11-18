package com.toigo.miptvga

import android.view.KeyEvent

/** UI-only focus memory. Mutating it never publishes playlist/playback state. */
internal class ChannelFocusAnchor {
    private var source: Any? = null
    var originalIndex = -1
    fun bindSource(value: Any): Boolean {
        if (source === value) return false
        source = value
        originalIndex = -1
        return true
    }
}

internal enum class BrowserZone { RAIL, CHANNELS, PLAYER }
internal class BrowserFocusMemory {
    var zone = BrowserZone.RAIL
    var rail = TvDestination.TELEVISION
    var hasFocused = false
}

internal class BrowserFocusRequest { var consumed = false }

internal enum class TvMediaCommand { UNHANDLED, CONSUME, TOGGLE, PLAY, PAUSE, BACK, FORWARD }

/** Match the existing player actions once, even when a preview control owns focus. */
internal fun tvMediaCommand(code: Int, action: Int, canSeek: Boolean): TvMediaCommand {
    if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return TvMediaCommand.UNHANDLED
    return when (code) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if (action == KeyEvent.ACTION_UP) TvMediaCommand.TOGGLE else TvMediaCommand.CONSUME
        KeyEvent.KEYCODE_MEDIA_PLAY -> if (action == KeyEvent.ACTION_UP) TvMediaCommand.PLAY else TvMediaCommand.CONSUME
        KeyEvent.KEYCODE_MEDIA_PAUSE -> if (action == KeyEvent.ACTION_UP) TvMediaCommand.PAUSE else TvMediaCommand.CONSUME
        KeyEvent.KEYCODE_MEDIA_REWIND -> if (!canSeek) TvMediaCommand.UNHANDLED else if (action == KeyEvent.ACTION_DOWN) TvMediaCommand.BACK else TvMediaCommand.CONSUME
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> if (!canSeek) TvMediaCommand.UNHANDLED else if (action == KeyEvent.ACTION_DOWN) TvMediaCommand.FORWARD else TvMediaCommand.CONSUME
        else -> TvMediaCommand.UNHANDLED
    }
}

internal fun dispatchTvMediaCommand(
    command: TvMediaCommand,
    state: PlaybackControllerState,
    actions: PlaybackControllerActions,
    onInputActivity: () -> Unit
): Boolean {
    if (command == TvMediaCommand.UNHANDLED) return false
    if (command == TvMediaCommand.CONSUME) return true
    onInputActivity()
    when (command) {
        TvMediaCommand.TOGGLE -> actions.togglePlayPause()
        TvMediaCommand.PLAY -> if (!state.isPlaying) actions.play()
        TvMediaCommand.PAUSE -> if (state.isPlaying || state.canPause) actions.pause()
        TvMediaCommand.BACK -> if (state.canSeek) actions.seekBack()
        TvMediaCommand.FORWARD -> if (state.canSeek) actions.seekForward()
        else -> Unit
    }
    return true
}
