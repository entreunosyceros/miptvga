package com.toigo.miptvga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager

/** Keep the native surface at one composition position; the modal owns auxiliary input. */
@Composable
internal fun PlaybackScreenHost(
    showAuxiliary: Boolean,
    onDismiss: () -> Unit,
    mainContent: @Composable () -> Unit,
    auxiliaryContent: @Composable () -> Unit
) {
    mainContent()
    if (showAuxiliary) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            val view = LocalView.current
            DisposableEffect(view) {
                val window = (view.parent as? DialogWindowProvider)?.window
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            auxiliaryContent()
        }
    }
}
