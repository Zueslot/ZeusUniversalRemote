package com.zeus.remote.samsung

import com.zeus.remote.data.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SamsungState(
    val connected: Boolean = false,
    val selectingInput: Boolean = false,
    val message: String = "Connect to the Samsung TV to authorize Zeus Remote."
)

/** Coordinates Samsung controls and the verified HDMI macro without UI or Activity dependencies. */
class SamsungController(devices: DeviceRepository, private val scope: CoroutineScope) : AutoCloseable {
    private val mutableState = MutableStateFlow(SamsungState())
    val state = mutableState.asStateFlow()
    private var inputJob: Job? = null
    private val client = SamsungRemoteClient(devices) { connected, message ->
        if (!connected) inputJob?.cancel()
        mutableState.value = state.value.copy(connected = connected, message = message)
    }

    fun showStatus(message: String) { mutableState.value = state.value.copy(message = message) }

    fun connect(address: String, trustSelfSigned: Boolean) {
        inputJob?.cancel()
        client.connect(address, trustSelfSigned)
    }

    fun forget() { inputJob?.cancel(); client.forget() }

    fun sendKey(key: String) {
        if (state.value.selectingInput) return
        showStatus(when {
            !client.connected -> "Connect and authorize Samsung first."
            client.sendKey(key) -> "Queued $key; confirm the result on the TV."
            else -> "Samsung connection is unavailable; reconnect."
        })
    }

    fun selectHdmi1(fast: Boolean) {
        if (state.value.selectingInput) return
        if (!client.connected) { showStatus("Connect and authorize Samsung first."); return }
        mutableState.value = state.value.copy(selectingInput = true)
        inputJob = scope.launch {
            try {
                showStatus("Preparing HDMI 1 selection...")
                delay(2000)
                val keys = HdmiInputSequence.keys
                for ((index, key) in keys.withIndex()) {
                    if (!client.connected) {
                        showStatus("HDMI 1 selection stopped: Samsung connection changed.")
                        return@launch
                    }
                    if (!client.sendKey(key)) {
                        showStatus("HDMI 1 selection stopped: reconnect Samsung.")
                        return@launch
                    }
                    showStatus("Selecting HDMI 1: step ${index + 1}/${keys.size}.")
                    delay(HdmiInputSequence.pauseAfter(index, fast))
                }
                showStatus("HDMI 1 sequence sent. Confirm the Shield picture on the TV.")
            } finally {
                mutableState.value = state.value.copy(selectingInput = false)
            }
        }
    }

    fun cancelInputSelection() {
        inputJob?.cancel()
        showStatus("HDMI 1 selection cancelled. The TV menu may remain open.")
    }

    override fun close() { inputJob?.cancel(); client.close() }
}
