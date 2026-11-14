package com.toigo.miptvga

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Requires an Android device. These tests do not measure decoder or TV performance. */
@OptIn(ExperimentalTestApi::class)
class TvBrowserTest {
    @get:Rule val compose = createComposeRule()

    @Test fun railDpadMovesFocusWithoutActivatingAndOkSelects() {
        val selected = mutableListOf<TvDestination>()
        compose.setContent { MaterialTheme {
            TvNavigationRail(false, TvDestination.TELEVISION, { selected.add(it) }, Modifier.height(640.dp))
        } }
        compose.onNodeWithContentDescription("Televisión").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Favoritos").assertIsFocused()
        compose.runOnIdle { assertTrue(selected.isEmpty()) }
        compose.onNodeWithContentDescription("Favoritos").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(TvDestination.FAVORITES), selected) }
    }

    @Test fun exitStartsOnCancelAndConfirmationCallsOnlyExit() {
        var cancels = 0
        var exits = 0
        compose.setContent { MaterialTheme { ExitConfirmationDialog({ cancels++ }, { exits++ }) } }
        compose.onNodeWithText("Cancelar").assertIsFocused()
        compose.onNodeWithText("Salir").performClick()
        compose.runOnIdle { assertEquals(0, cancels); assertEquals(1, exits) }
    }

    @Test fun cancelNeverCallsExit() {
        var cancels = 0
        var exits = 0
        compose.setContent { MaterialTheme { ExitConfirmationDialog({ cancels++ }, { exits++ }) } }
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(1, cancels); assertEquals(0, exits) }
    }

    @Test fun channelFocusDoesNotPlayAndMillionEntryCatalogueStaysVirtualized() {
        var reads = 0
        val selected = mutableListOf<Int>()
        val entries = object : AbstractList<ChannelListEntry>(), java.util.RandomAccess {
            override val size = 1_000_000
            override fun get(index: Int): ChannelListEntry {
                reads++
                return ChannelListEntry(index, Channel("Synthetic $index", "https://example.invalid/$index"))
            }
        }
        compose.setContent { MaterialTheme {
            ChannelList(modifier = Modifier.height(400.dp), filteredChannels = entries,
                favoriteIds = emptySet(), favoriteGroupIds = emptySet(), watchedIds = emptySet(),
                currentPrograms = emptyMap(), showChannelLogos = false, selectedIndex = -1,
                selectedVisibleIndex = 0, focusRequestToken = 1,
                onToggleFavorite = {}, onToggleWatched = {}, onPlayContinuousFrom = {},
                onStopContinuousPlay = {}, onSelectChannel = { selected.add(it) })
        } }
        compose.onNodeWithText("Synthetic 0").performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { assertTrue(selected.isEmpty()); assertTrue("Reads: $reads", reads < 10_000) }
        compose.onNodeWithText("Synthetic 1").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(1), selected) }
    }
}
