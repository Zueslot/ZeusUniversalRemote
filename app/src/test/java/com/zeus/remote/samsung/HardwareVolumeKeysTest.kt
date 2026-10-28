package com.zeus.remote.samsung

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class HardwareVolumeKeysTest {
    @Test fun routesUpAndDownAndConsumesTheirRelease() {
        val router = HardwareVolumeKeys()
        val commands = mutableListOf<String>()
        for ((key, command) in listOf(KeyEvent.KEYCODE_VOLUME_UP to "KEY_VOLUP", KeyEvent.KEYCODE_VOLUME_DOWN to "KEY_VOLDOWN")) {
            assertTrue(router.dispatch(key, KeyEvent.ACTION_DOWN, 0, 1000) { commands.add(it); true })
            assertEquals(command, commands.last())
            assertTrue(router.dispatch(key, KeyEvent.ACTION_UP, 0, 1100) { error("Release must not send a command") })
        }
        assertEquals(2, commands.size)
    }
    @Test fun unavailableTvLeavesEntireHeldPressToPhone() {
        val router = HardwareVolumeKeys()
        assertFalse(router.dispatch(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 1000) { false })
        assertFalse(router.dispatch(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 1, 1200) { error("Do not capture mid-press") })
        assertFalse(router.dispatch(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0, 1300) { true })
    }
    @Test fun repeatsAreThrottledAndDisconnectDoesNotAdjustPhoneMidPress() {
        val router = HardwareVolumeKeys()
        var sent = 0
        assertTrue(router.dispatch(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0, 1000) { sent++; true })
        assertTrue(router.dispatch(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 1, 1050) { error("Too soon") })
        assertTrue(router.dispatch(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 2, 1150) { sent++; true })
        assertEquals(2, sent)
        assertTrue(router.dispatch(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 3, 1300) { false })
        assertTrue(router.dispatch(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP, 0, 1350) { false })
        assertFalse(router.dispatch(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0, 1500) { false })
    }
    @Test fun unrelatedKeysPassThroughAndPauseClearsCapturedKeys() {
        val router = HardwareVolumeKeys()
        assertFalse(router.dispatch(KeyEvent.KEYCODE_HOME, KeyEvent.ACTION_DOWN, 0, 1000) { error("Wrong key") })
        router.dispatch(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 1000) { true }
        router.reset()
        assertFalse(router.dispatch(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0, 1100) { true })
    }
}
