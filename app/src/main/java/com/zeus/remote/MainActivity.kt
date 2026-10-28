package com.zeus.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zeus.remote.data.DeviceRepository
import com.zeus.remote.ui.RemoteScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val devices = DeviceRepository(applicationContext)
        setContent { RemoteScreen(devices) }
    }
}
