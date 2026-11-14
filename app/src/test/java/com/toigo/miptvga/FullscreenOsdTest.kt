package com.toigo.miptvga

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class FullscreenOsdTest {
    @Test fun hiddenOkOpensOnReleaseWithoutActivatingAnActionOnPress() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            assertEquals(OsdKeyCommand.CONSUME, fullscreenOsdKeyCommand(key, KeyEvent.ACTION_DOWN, false, true))
            assertEquals(OsdKeyCommand.SHOW, fullscreenOsdKeyCommand(key, KeyEvent.ACTION_UP, false, true))
        }
    }

    @Test fun visibleOkPassesToFocusedControl() {
        assertEquals(OsdKeyCommand.PASS, fullscreenOsdKeyCommand(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, true, false))
        assertEquals(OsdKeyCommand.PASS, fullscreenOsdKeyCommand(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP, true, false))
    }

    @Test fun okOnUncoveredVideoRestoresControlsFocusInsteadOfLeavingFullscreen() {
        assertEquals(OsdKeyCommand.SHOW, fullscreenOsdKeyCommand(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP, true, true))
    }

    @Test fun arrowsRevealHiddenOsdButDoNotConsumeVisibleNavigation() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            assertEquals(OsdKeyCommand.SHOW, fullscreenOsdKeyCommand(key, KeyEvent.ACTION_DOWN, false, true))
            assertEquals(OsdKeyCommand.CONSUME, fullscreenOsdKeyCommand(key, KeyEvent.ACTION_UP, false, true))
            assertEquals(OsdKeyCommand.PASS, fullscreenOsdKeyCommand(key, KeyEvent.ACTION_DOWN, true, false))
        }
    }

    @Test fun specialKeysAreNeverSwallowedByOsdOpeningPolicy() {
        val keys = listOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PROG_RED,
            KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_PROG_BLUE,
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE)
        for (visible in listOf(false, true)) for (key in keys) for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            assertEquals(OsdKeyCommand.PASS, fullscreenOsdKeyCommand(key, action, visible, true))
        }
    }

    @Test fun adjacentChannelUsesOneIndexedReadEvenWith150000Channels() {
        var reads = 0
        val entries = object : AbstractList<ChannelListEntry>(), java.util.RandomAccess {
            override val size = 150_000
            override fun get(index: Int): ChannelListEntry {
                reads++
                return ChannelListEntry(index * 2, Channel("Synthetic", "https://example.invalid/stream"))
            }
        }
        assertEquals(24692, adjacentOsdChannel(entries, 12345, true))
        assertEquals(1, reads)
        reads = 0
        assertEquals(299998, adjacentOsdChannel(entries, 0, false))
        assertEquals(1, reads)
        assertEquals(0, adjacentOsdChannel(entries, 149999, true))
    }

    @Test fun unavailableZapActionsAndAbsentSelectionAreHandledSafely() {
        assertNull(adjacentOsdChannel(emptyList(), -1, true))
        val one = listOf(ChannelListEntry(4, Channel("Synthetic", "https://example.invalid/stream")))
        assertNull(adjacentOsdChannel(one, 0, true))
        val two = one + ChannelListEntry(9, one.first().channel)
        assertEquals(4, adjacentOsdChannel(two, -1, true))
        assertEquals(9, adjacentOsdChannel(two, -1, false))
    }

    @Test fun noCapabilitiesOnlyOffersValidExitWithoutExecutingAnything() {
        var calls = 0
        val actions = fullscreenOsdActions(PlaybackControllerState(), PlaybackControllerActions(),
            null, null, null, null, null, null, { calls++ }, false, false)
        assertEquals(listOf(OsdActionId.EXIT), actions.map { it.id })
        assertEquals(0, calls)
        actions.single().invoke()
        assertEquals(1, calls)
    }

    @Test fun actionsReuseExistingCallbacksAndBuildingOrFocusingDoesNotPlay() {
        val calls = mutableListOf<String>()
        val actions = fullscreenOsdActions(PlaybackControllerState(isPlaying = true, canPause = true, canSeek = true),
            PlaybackControllerActions(togglePlayPause = { calls.add("pause") }, seekBack = { calls.add("seek") }),
            { calls.add("previous") }, { calls.add("next") }, { calls.add("favorite") }, null,
            { calls.add("info") }, null, { calls.add("exit") }, false, false)
        assertTrue(calls.isEmpty())
        for (id in listOf(OsdActionId.PLAY_PAUSE, OsdActionId.PREVIOUS, OsdActionId.NEXT,
            OsdActionId.FAVORITE, OsdActionId.INFO, OsdActionId.SEEK_BACK, OsdActionId.EXIT)) actions.single { it.id == id }.invoke()
        assertEquals(listOf("pause", "previous", "next", "favorite", "info", "seek", "exit"), calls)
    }

    @Test fun liveNonSeekableAndPausedPlaybackExposeCorrectControls() {
        val actions = fullscreenOsdActions(PlaybackControllerState(canPause = true, isLive = true), PlaybackControllerActions(),
            null, null, {}, {}, {}, {}, {}, true, true)
        assertFalse(actions.any { it.id == OsdActionId.SEEK_BACK || it.id == OsdActionId.SEEK_FORWARD })
        assertEquals("Reproducir", actions.single { it.id == OsdActionId.PLAY_PAUSE }.label)
        assertEquals("Quitar favorito", actions.single { it.id == OsdActionId.FAVORITE }.label)
        assertEquals("Quitar grupo favorito", actions.single { it.id == OsdActionId.GROUP_FAVORITE }.label)
    }
}
