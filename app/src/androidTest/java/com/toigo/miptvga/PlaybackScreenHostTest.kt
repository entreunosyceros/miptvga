package com.toigo.miptvga

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Verifies composition ownership, not decoder/audio/Surface continuity. */
class PlaybackScreenHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun auxiliaryScreensKeepMainOwnerAndDisposeItOnlyWhenRootCloses() {
        val auxiliary = mutableStateOf(false)
        val present = mutableStateOf(true)
        var created = 0
        var released = 0
        compose.setContent {
            if (present.value) {
                PlaybackScreenHost(auxiliary.value, { auxiliary.value = false }, mainContent = {
                    DisposableEffect(Unit) { created++; onDispose { released++ } }
                    Button(onClick = { auxiliary.value = true }) { Text("Open synthetic auxiliary") }
                }, auxiliaryContent = {
                    Box(Modifier.fillMaxSize()) {
                        Button(onClick = { auxiliary.value = false }) { Text("Close synthetic auxiliary") }
                    }
                })
            }
        }
        repeat(5) {
            compose.onNodeWithText("Open synthetic auxiliary").performClick()
            compose.onNodeWithText("Close synthetic auxiliary").performClick()
        }
        compose.runOnIdle { assertEquals(1, created); assertEquals(0, released); present.value = false }
        compose.runOnIdle { assertEquals(1, released) }
    }
}
