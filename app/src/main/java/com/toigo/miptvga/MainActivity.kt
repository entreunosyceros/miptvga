
package com.toigo.miptvga

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels {
        MainViewModel.factory(applicationContext)
    }
    private var lastMouseBackHandledAt = 0L
    private var finishOnStopRequested = false
    private var backgroundExitHandled = false
    private var backgroundCloseJob: Job? = null
    private var shutdownReceiverRegistered = false
    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SHUTDOWN -> {
                    requestFinishOnNextStop()
                    scheduleBackgroundClose(immediate = intent.action == Intent.ACTION_SHUTDOWN)
                }
            }
        }
    }

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        registerShutdownReceiver()
        setContent {
            MiptvgaTheme {
                RootScreen(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        cancelPendingBackgroundClose(resetFinishRequest = true)
        backgroundExitHandled = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        cancelPendingBackgroundClose(resetFinishRequest = true)
        backgroundExitHandled = false
    }

    override fun onPause() {
        if (!isChangingConfigurations && !isFinishing && !isDeviceInteractive()) {
            requestFinishOnNextStop()
            scheduleBackgroundClose()
        }
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!isChangingConfigurations && !isFinishing && (finishOnStopRequested || !isDeviceInteractive())) {
            scheduleBackgroundClose()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.isMouseBackKey()) {
            return if (event.action == KeyEvent.ACTION_UP) {
                handleMouseBackGesture(event.eventTime)
            } else {
                true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.isSecondaryMouseAction()) {
            return if (event.action == MotionEvent.ACTION_UP) {
                handleMouseBackGesture(event.eventTime)
            } else {
                true
            }
        }

        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isSecondaryMouseAction()) {
            return if (event.action == MotionEvent.ACTION_BUTTON_RELEASE || event.action == MotionEvent.ACTION_UP) {
                handleMouseBackGesture(event.eventTime)
            } else {
                true
            }
        }

        return super.dispatchGenericMotionEvent(event)
    }

    override fun onDestroy() {
        cancelPendingBackgroundClose(resetFinishRequest = false)
        unregisterShutdownReceiver()
        PlaybackCache.releaseAndClear(applicationContext)
        super.onDestroy()
    }

    private fun handleMouseBackGesture(eventTime: Long): Boolean {
        if (eventTime - lastMouseBackHandledAt < 250L) {
            return true
        }

        lastMouseBackHandledAt = eventTime
        return vm.handleMouseBackAction()
    }

    private fun requestFinishOnNextStop() {
        if (finishOnStopRequested) return
        finishOnStopRequested = true
        vm.prepareForBackgroundExit()
    }

    fun requestAppExit() {
        if (isChangingConfigurations) return
        closeForBackgroundExit()
    }

    private fun closeForBackgroundExit() {
        if (backgroundExitHandled || isChangingConfigurations) return

        cancelPendingBackgroundClose(resetFinishRequest = false)
        backgroundExitHandled = true
        finishOnStopRequested = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        vm.prepareForBackgroundExit()
        PlaybackCache.releaseAndClear(applicationContext)

        if (!isFinishing && !isDestroyed) {
            finishAndRemoveTask()
        }
    }

    private fun scheduleBackgroundClose(immediate: Boolean = false) {
        if (backgroundExitHandled || isChangingConfigurations) return

        backgroundCloseJob?.cancel()
        backgroundCloseJob = lifecycleScope.launch {
            if (!immediate) {
                delay(750L)
            }

            if (isChangingConfigurations) return@launch
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
            if (!finishOnStopRequested && isDeviceInteractive()) return@launch

            closeForBackgroundExit()
        }
    }

    private fun cancelPendingBackgroundClose(resetFinishRequest: Boolean) {
        backgroundCloseJob?.cancel()
        backgroundCloseJob = null
        if (resetFinishRequest) {
            finishOnStopRequested = false
        }
    }

    private fun registerShutdownReceiver() {
        if (shutdownReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SHUTDOWN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(shutdownReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(shutdownReceiver, filter)
        }
        shutdownReceiverRegistered = true
    }

    private fun unregisterShutdownReceiver() {
        if (!shutdownReceiverRegistered) return
        runCatching { unregisterReceiver(shutdownReceiver) }
        shutdownReceiverRegistered = false
    }

    private fun isDeviceInteractive(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isInteractive
    }
}

private fun KeyEvent.isMouseBackKey(): Boolean {
    val isMouseSource = isFromSource(InputDevice.SOURCE_MOUSE) || isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)
    if (!isMouseSource) return false
    return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
}

private fun MotionEvent.isSecondaryMouseAction(): Boolean {
    val isMouseSource = isFromSource(InputDevice.SOURCE_MOUSE) || isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)
    if (!isMouseSource) return false

    val secondaryPressed = buttonState and MotionEvent.BUTTON_SECONDARY != 0
    return secondaryPressed && (
        action == MotionEvent.ACTION_BUTTON_PRESS ||
            action == MotionEvent.ACTION_BUTTON_RELEASE ||
            action == MotionEvent.ACTION_DOWN ||
            action == MotionEvent.ACTION_UP
        )
}

