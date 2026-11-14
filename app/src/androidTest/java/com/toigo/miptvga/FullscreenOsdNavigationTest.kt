package com.toigo.miptvga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Tests the production OSD input/effects/overlay. Native video continuity still needs a device test. */
@OptIn(ExperimentalTestApi::class)
class FullscreenOsdNavigationTest {
    @get:Rule val compose = createComposeRule()

    private class State(initiallyVisible: Boolean) {
        var visible by mutableStateOf(initiallyVisible)
        var active by mutableStateOf(true)
        var blocked by mutableStateOf(false)
        var activity by mutableIntStateOf(0)
        var focusRequest by mutableIntStateOf(0)
        var pauses = 0
        var zaps = 0
        var exits = 0
        var confirms = 0
        var created = 0
        var released = 0
        fun show() { visible = true; activity++ }
    }

    @Composable private fun Harness(state: State) {
        val videoFocus = remember { FocusRequester() }
        val controlsFocus = remember { FocusRequester() }
        var osdHasFocus by remember { mutableStateOf(false) }
        val actions = listOf(
            OsdAction(OsdActionId.PLAY_PAUSE, "Pausa") { state.pauses++ },
            OsdAction(OsdActionId.NEXT, "Canal siguiente") { state.zaps++ },
            OsdAction(OsdActionId.INFO, "Información") { state.blocked = true },
            OsdAction(OsdActionId.EXIT, "Volver") { state.active = false; state.exits++ }
        )
        FullscreenOsdEffects(state.active, state.visible, state.blocked, state.activity, 1L,
            state.focusRequest, controlsFocus, videoFocus, { state.visible = false })
        FullscreenOsdBackHandler(state.active && !state.blocked, state.visible,
            { state.visible = false }, { state.active = false; state.exits++ })
        Box(Modifier.fillMaxSize().fullscreenOsdInput(state.active && !state.blocked, state.visible,
            !osdHasFocus, state::show, { state.focusRequest++ })) {
            // Same composition owner before, during and after OSD visibility changes.
            Box(Modifier.fillMaxSize().testTag("video").focusRequester(videoFocus).clickable { state.show() }) {
                DisposableEffect(Unit) { state.created++; onDispose { state.released++ } }
            }
            if (state.active && state.visible) FullscreenOsdOverlay("Synthetic channel", "Programme", null,
                "Playing", actions, OsdActionId.PLAY_PAUSE, controlsFocus, state::show,
                Modifier.align(Alignment.BottomCenter).testTag("osd"), { osdHasFocus = it })
        }
        if (state.blocked) AlertDialog(onDismissRequest = { state.blocked = false },
            title = { Text("Confirmación de prueba") },
            confirmButton = { TextButton(onClick = { state.confirms++; state.blocked = false }) { Text("Confirmar") } })
    }

    private fun start(state: State) { compose.setContent { MaterialTheme { Harness(state) } } }
    private fun tick(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

    @Test fun okFocusActionsAndBackDoNotRemountVideoOwner() {
        val state = State(false)
        start(state)
        compose.onNodeWithTag("video").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Pausa").assertIsFocused()
        compose.runOnIdle { assertEquals(0, state.pauses); assertEquals(0, state.zaps) }
        compose.onNodeWithText("Pausa").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Canal siguiente").assertIsFocused()
        compose.runOnIdle { assertEquals(0, state.zaps) }
        compose.onNodeWithText("Canal siguiente").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, state.zaps); assertEquals(0, state.pauses) }
        pressBack()
        compose.onNodeWithTag("osd").assertDoesNotExist()
        compose.onNodeWithTag("video").assertIsFocused()
        compose.runOnIdle { assertEquals(true, state.active); assertEquals(0, state.exits) }
        pressBack()
        compose.runOnIdle { assertEquals(false, state.active); assertEquals(1, state.exits); assertEquals(1, state.created); assertEquals(0, state.released) }
    }

    @Test fun interactionRestartsFiveSecondTimerWithoutStealingControlFocus() {
        val state = State(true)
        start(state)
        compose.mainClock.autoAdvance = false
        tick(4_000)
        compose.onNodeWithTag("osd").assertExists()
        compose.onNodeWithText("Pausa").performKeyInput { pressKey(Key.DirectionRight) }
        tick(32)
        compose.onNodeWithText("Canal siguiente").assertIsFocused()
        tick(4_000)
        compose.onNodeWithText("Canal siguiente").assertIsFocused()
        tick(1_200)
        compose.onNodeWithTag("osd").assertDoesNotExist()
        compose.onNodeWithTag("video").assertIsFocused()
        compose.runOnIdle { assertEquals(0, state.zaps); assertEquals(1, state.created); assertEquals(0, state.released) }
    }

    @Test fun confirmationSuspendsTimeoutAndTouchActionWorksAfterDismissal() {
        val state = State(true)
        start(state)
        compose.mainClock.autoAdvance = false
        tick(4_000)
        compose.onNodeWithText("Información").performClick()
        tick(32)
        compose.onNodeWithText("Confirmación de prueba").assertExists()
        tick(6_000)
        compose.onNodeWithTag("osd").assertExists()
        compose.onNodeWithText("Confirmar").performClick()
        tick(32)
        compose.onNodeWithText("Pausa").assertIsFocused().performClick()
        tick(32)
        compose.runOnIdle { assertEquals(1, state.pauses); assertEquals(1, state.confirms); assertEquals(0, state.zaps) }
        tick(4_000)
        compose.onNodeWithTag("osd").assertExists()
        tick(1_200)
        compose.onNodeWithTag("osd").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, state.created); assertEquals(0, state.released) }
    }
}
