package com.toigo.miptvga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Pixel checks require API 26; they are not a TV frame-rate benchmark. */
@SdkSuppress(minSdkVersion = 26)
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class ChannelNameMarqueeAndroidTest {
    @get:Rule val compose = createComposeRule()
    private fun tick(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun pixels(tag: String): IntArray {
        val map = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return IntArray(map.width * map.height) { map[it % map.width, it / map.width].toArgb() }
    }
    @Composable private fun Keyboard(content: @Composable () -> Unit) {
        val manager = remember { object : InputModeManager {
            override val inputMode: InputMode get() = InputMode.Keyboard
            override fun requestInputMode(inputMode: InputMode): Boolean = inputMode == InputMode.Keyboard
        } }
        CompositionLocalProvider(LocalInputModeManager provides manager) { content() }
    }
    @Composable private fun Name(tag: String, value: String, focused: Boolean) {
        Box(Modifier.width(140.dp).height(30.dp).background(Color.Black).testTag(tag)) {
            Text(value, color = Color.White, maxLines = 1, modifier = Modifier.focusedChannelName(focused))
        }
    }

    @Test fun delayOnlyFocusedRowMovesAndLossOfFocusResetsToStart() {
        var focused by mutableIntStateOf(0)
        val name = "Película — Serie — Texto largo para leer completamente sin cambiar de canal. ".repeat(5)
        compose.setContent { MaterialTheme { Keyboard { Column {
            Name("first", name, focused == 0)
            Name("second", name, focused == 1)
        } } } }
        compose.mainClock.autoAdvance = false
        val firstStart = pixels("first")
        val secondStart = pixels("second")
        tick(700)
        assertTrue(firstStart.contentEquals(pixels("first")))
        tick(900)
        assertFalse(firstStart.contentEquals(pixels("first")))
        assertTrue(secondStart.contentEquals(pixels("second")))
        compose.runOnIdle { focused = 1 }
        tick(32)
        assertTrue(firstStart.contentEquals(pixels("first")))
        tick(700)
        assertTrue(secondStart.contentEquals(pixels("second")))
        tick(900)
        assertTrue(firstStart.contentEquals(pixels("first")))
        assertFalse(secondStart.contentEquals(pixels("second")))
        compose.runOnIdle { focused = 0 }
        tick(32)
        tick(700)
        assertTrue(firstStart.contentEquals(pixels("first")))
    }

    @Test fun fittingUnicodeNameRemainsStaticAndExtremelyLongUnicodeCanMove() {
        var focused by mutableIntStateOf(0)
        compose.setContent { MaterialTheme { Keyboard { Column {
            Name("short", "TV ñ 漢", focused == 0)
            Name("long", "映画 — Ελληνικά — العربية — Película 🎬 — ".repeat(200), focused == 1)
        } } } }
        compose.mainClock.autoAdvance = false
        val shortStart = pixels("short")
        val longStart = pixels("long")
        tick(2_000)
        assertTrue(shortStart.contentEquals(pixels("short")))
        assertTrue(longStart.contentEquals(pixels("long")))
        compose.runOnIdle { focused = 1 }
        tick(32)
        tick(1_600)
        assertFalse(longStart.contentEquals(pixels("long")))
        assertTrue(shortStart.contentEquals(pixels("short")))
    }

    @Test fun touchModeDoesNotAnimateEvenIfRowRetainsLogicalFocus() {
        val mode = mutableStateOf(InputMode.Keyboard)
        val manager = object : InputModeManager {
            override val inputMode get() = mode.value
            override fun requestInputMode(inputMode: InputMode): Boolean { mode.value = inputMode; return true }
        }
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalInputModeManager provides manager) {
            Name("name", "Texto Unicode muy largo — película y serie — ".repeat(10), true)
        } } }
        compose.mainClock.autoAdvance = false
        val initial = pixels("name")
        tick(1_600)
        assertFalse(initial.contentEquals(pixels("name")))
        compose.runOnIdle { mode.value = InputMode.Touch }
        tick(32)
        assertTrue(initial.contentEquals(pixels("name")))
        tick(2_000)
        assertTrue(initial.contentEquals(pixels("name")))
    }

    @Test fun longPressReadsOriginalFullNameWithoutPlayingOrChangingFavorites() {
        val name = "Película Unicode ñ 漢字 🎬 — nombre extremadamente largo. ".repeat(30)
        var plays = 0
        var favorites = 0
        compose.setContent { MaterialTheme {
            ChannelList(modifier = Modifier.width(400.dp).height(300.dp),
                filteredChannels = listOf(ChannelListEntry(0, Channel(name, "https://example.invalid/stream"))),
                favoriteIds = emptySet(), favoriteGroupIds = emptySet(), watchedIds = emptySet(),
                currentPrograms = emptyMap(), showChannelLogos = false, selectedIndex = -1, selectedVisibleIndex = -1,
                onToggleFavorite = { favorites++ }, onToggleWatched = {}, onPlayContinuousFrom = {},
                onStopContinuousPlay = {}, onSelectChannel = { plays++ })
        } }
        compose.onNodeWithText(name).performTouchInput { longClick() }
        compose.onNodeWithText("Ver nombre completo").performClick()
        compose.onNodeWithText("Nombre completo").assertExists()
        compose.onAllNodesWithText(name).assertCountEquals(2)
        compose.runOnIdle { assertEquals(0, plays); assertEquals(0, favorites) }
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Nombre completo").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, plays) }
    }
}
