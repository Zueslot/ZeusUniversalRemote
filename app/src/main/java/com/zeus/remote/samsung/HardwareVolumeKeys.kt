package com.zeus.remote.samsung

import android.view.KeyEvent

/** Own a complete key press once routed; never switch a held press between TV and phone. */
class HardwareVolumeKeys {
    private val captured = mutableSetOf<Int>()
    private val local = mutableSetOf<Int>()
    private val lastSent = mutableMapOf<Int, Long>()

    fun dispatch(key: Int, action: Int, repeat: Int, time: Long, send: (String) -> Boolean): Boolean {
        val command = when (key) {
            KeyEvent.KEYCODE_VOLUME_UP -> "KEY_VOLUP"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "KEY_VOLDOWN"
            else -> return false
        }
        if (action == KeyEvent.ACTION_UP) {
            local.remove(key)
            lastSent.remove(key)
            return captured.remove(key)
        }
        if (action != KeyEvent.ACTION_DOWN) return false
        if (repeat == 0) { captured.remove(key); local.remove(key); lastSent.remove(key) }
        if (key in local) return false
        if (key in captured && repeat > 0 && time - (lastSent[key] ?: 0L) < 120L) return true
        if (send(command)) {
            captured.add(key)
            lastSent[key] = time
            return true
        }
        if (key in captured) return true
        local.add(key)
        return false
    }

    fun reset() { captured.clear(); local.clear(); lastSent.clear() }
}
