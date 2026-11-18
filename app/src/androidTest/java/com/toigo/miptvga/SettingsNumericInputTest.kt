package com.toigo.miptvga

import android.view.KeyEvent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNumericInputTest {
    @Test
    fun digitsReachSettingsInsteadOfSelectingChannels() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(
                    activity,
                    MainViewModel.factory(activity.applicationContext)
                )[MainViewModel::class.java]
                val digits = (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) +
                    (KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9)

                vm.openSettings()
                for (keyCode in digits) {
                    assertFalse(activity.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode)))
                    assertFalse(activity.handleKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode)))
                }

                // The remote's channel-number shortcut remains available on television.
                vm.openMain()
                assertTrue(activity.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1)))
            }
        }
    }
}
