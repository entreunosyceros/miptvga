package com.toigo.miptvga

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class TvInputTest {
    @Test fun playPausePairDispatchesOnlyOnce() {
        var toggles = 0
        var activity = 0
        val actions = PlaybackControllerActions(togglePlayPause = { toggles++ })
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            assertTrue(dispatchTvMediaCommand(tvMediaCommand(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, action, false),
                PlaybackControllerState(), actions) { activity++ })
        }
        assertEquals(1, toggles)
        assertEquals(1, activity)
    }

    @Test fun directionalAndChannelKeysRemainAvailableToNavigationAndZapping() {
        for (code in listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PROG_RED)) {
            assertEquals(TvMediaCommand.UNHANDLED, tvMediaCommand(code, KeyEvent.ACTION_DOWN, true))
        }
    }

    @Test fun liveStreamCannotSeekAndSeekReleaseDoesNotRepeat() {
        assertEquals(TvMediaCommand.UNHANDLED, tvMediaCommand(KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.ACTION_DOWN, false))
        var seeks = 0
        val state = PlaybackControllerState(canSeek = true)
        val actions = PlaybackControllerActions(seekForward = { seeks++ })
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            dispatchTvMediaCommand(tvMediaCommand(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, action, true), state, actions) {}
        }
        assertEquals(1, seeks)
    }

    @Test fun playDoesNotToggleAnAlreadyPlayingStream() {
        var calls = 0
        val actions = PlaybackControllerActions(play = { calls++ })
        dispatchTvMediaCommand(TvMediaCommand.PLAY, PlaybackControllerState(isPlaying = true), actions) {}
        assertEquals(0, calls)
        dispatchTvMediaCommand(TvMediaCommand.PLAY, PlaybackControllerState(), actions) {}
        assertEquals(1, calls)
    }

    @Test fun anchorUsesReferenceIdentityWithoutComparingCatalogueContents() {
        val source = object { override fun equals(other: Any?): Boolean = error("Catalogue comparison") }
        val anchor = ChannelFocusAnchor()
        assertTrue(anchor.bindSource(source))
        anchor.originalIndex = 999_999
        assertFalse(anchor.bindSource(source))
        assertEquals(999_999, anchor.originalIndex)
        assertTrue(anchor.bindSource(Any()))
        assertEquals(-1, anchor.originalIndex)
    }
}
