package com.toigo.miptvga

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Uses the actual group list/back handler and channel list; no claim about decoder continuity. */
@OptIn(ExperimentalTestApi::class)
class GroupBrowserNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chooseGroupBackRestoresFocusAndOnlyExplicitChannelClickChangesPlayback() {
        val groups = listOf(ChannelGroup("z", "Z group", 1), ChannelGroup("a", "A group", 1))
        val state = GroupBrowserState()
        var playingIndex = 0
        var ownerCreated = 0
        var ownerReleased = 0
        compose.setContent { MaterialTheme {
            val scroll = rememberLazyListState()
            var token by remember { mutableIntStateOf(0) }
            SideEffect { state.bindSource("source-A", groups) }
            DisposableEffect(Unit) { ownerCreated++; onDispose { ownerReleased++ } }
            GroupBrowserBackHandler(true, state) { token++ }
            Column {
                Button(onClick = { state.openGroups(); token++ }) { Text("Open groups") }
                if (state.active && state.showingGroups) {
                    TvGroupList(groups, state.selectedGroupId, state.focusedGroupIndex, scroll, token,
                        state::focusGroup, { state.chooseGroup(it); token++ }, {}, {}, Modifier.height(300.dp))
                } else if (state.active) {
                    Text("Channels: ${state.selectedGroupId}")
                    val index = if (state.selectedGroupId == "a") 1 else 0
                    ChannelList(filteredChannels = listOf(ChannelListEntry(index,
                        Channel("Channel $index", "https://example.invalid/$index"))),
                        favoriteIds = emptySet(), favoriteGroupIds = emptySet(), watchedIds = emptySet(),
                        currentPrograms = emptyMap(), showChannelLogos = false, selectedIndex = playingIndex,
                        selectedVisibleIndex = -1, focusRequestToken = token,
                        onToggleFavorite = {}, onToggleWatched = {}, onPlayContinuousFrom = {},
                        onStopContinuousPlay = {}, onSelectChannel = { playingIndex = it },
                        modifier = Modifier.height(300.dp))
                }
            }
        } }
        compose.onNodeWithText("Open groups").performClick()
        compose.onNodeWithText("Z group").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("A group").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Channels: a").assertExists()
        compose.onNodeWithText("Z group").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, playingIndex); assertEquals(1, ownerCreated); assertEquals(0, ownerReleased) }
        pressBack()
        compose.onNodeWithText("A group").assertIsFocused()
        compose.onNodeWithText("Channel 1").assertDoesNotExist()
        compose.onNodeWithText("A group").performClick()
        compose.runOnIdle { assertEquals(0, playingIndex) }
        compose.onNodeWithText("Channel 1").performClick()
        compose.runOnIdle { assertEquals(1, playingIndex); assertEquals(1, ownerCreated); assertEquals(0, ownerReleased) }
    }
}
