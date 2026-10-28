package com.zeus.remote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zeus.remote.data.DeviceRepository
import com.zeus.remote.ui.RemoteScreen
import com.zeus.remote.samsung.HardwareVolumeKeys
import androidx.lifecycle.Lifecycle

class MainActivity : ComponentActivity() {
    private val volumeKeys = HardwareVolumeKeys()
    private var tvVolumeHandler: ((String) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val devices = DeviceRepository(applicationContext)
        setContent { RemoteScreen(devices, onVolumeHandlerChanged = { tvVolumeHandler = it }) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        routeVolume(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        routeVolume(event) || super.onKeyUp(keyCode, event)

    private fun routeVolume(event: KeyEvent): Boolean =
        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && hasWindowFocus() &&
            volumeKeys.dispatch(event.keyCode, event.action, event.repeatCount, event.eventTime) { command ->
                tvVolumeHandler?.invoke(command) == true
            }

    override fun onPause() {
        volumeKeys.reset()
        super.onPause()
    }
}
