package com.toigo.miptvga

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.yield

/** Only the focused visible row installs an animation node. basicMarquee itself checks overflow. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.focusedChannelName(focused: Boolean): Modifier = if (!focused ||
    !LocalWindowInfo.current.isWindowFocused || LocalInputModeManager.current.inputMode != InputMode.Keyboard) this else
    clipToBounds().basicMarquee(
        iterations = Int.MAX_VALUE,
        initialDelayMillis = 1_000,
        repeatDelayMillis = 1_000,
        velocity = 30.dp
    )

@Composable
internal fun FullChannelNameDialog(name: String, onDismiss: () -> Unit) {
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { yield(); close.requestFocus() }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Nombre completo") },
        text = {
            SelectionContainer {
                Text(name, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(close)) { Text("Cerrar") } },
        containerColor = TvSurface, tonalElevation = 0.dp)
}
