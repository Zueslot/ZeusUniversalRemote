package com.zeus.remote.samsung

import com.zeus.remote.data.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancelAndJoin
import com.zeus.shared.isPrivateIpv4
import com.zeus.remote.power.WakeOnLan

data class SamsungState(
    val connected: Boolean = false,
    val selectingInput: Boolean = false,
    val message: String = "Connect to the Samsung TV to authorize Zeus Remote."
)

/** Coordinates Samsung controls and the verified HDMI macro without UI or Activity dependencies. */
class SamsungController(private val devices: DeviceRepository, private val scope: CoroutineScope) : AutoCloseable {
    private val mutableState = MutableStateFlow(SamsungState())
    val state = mutableState.asStateFlow()
    private var inputJob: Job? = null
    private var reconnectJob: Job? = null
    private var configuredAddress = ""
    private var trustCertificate = false
    private val client = SamsungRemoteClient(devices, onState = { connected, message ->
        if (!connected) inputJob?.cancel()
        mutableState.value = state.value.copy(connected = connected, message = message)
        if (connected) {
            reconnectJob?.cancel()
            scope.launch { learnWakeAddress() }
        }
    }, onDisconnected = { scheduleReconnect() })

    fun showStatus(message: String) { mutableState.value = state.value.copy(message = message) }

    fun connect(address: String, trustSelfSigned: Boolean) {
        inputJob?.cancel()
        reconnectJob?.cancel()
        configuredAddress = address.trim()
        trustCertificate = trustSelfSigned
        client.connect(address, trustSelfSigned)
    }

    private fun scheduleReconnect() {
        if (!isPrivateIpv4(configuredAddress) || client.authorizationDenied || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var seconds = 1L
            while (isActive && !client.connected && !client.authorizationDenied) {
                delay(seconds * 1000)
                if (!client.connected && !client.connecting && !client.authorizationDenied) {
                    client.connect(configuredAddress, trustCertificate)
                }
                seconds = (seconds * 2).coerceAtMost(30)
            }
        }
    }

    suspend fun learnWakeAddress(): String? {
        val target = configuredAddress
        val mac = client.discoverWakeMac(target, trustCertificate)
        if (mac != null && target == configuredAddress && WakeOnLan.normalizeMac(devices.samsungMac) == null) {
            devices.samsungMac = mac
        }
        return mac
    }

    suspend fun readPowerState(): String? = client.readPowerState(devices.samsungAddress, devices.trustSamsungCertificate)

    suspend fun requestStandby(): Boolean {
        if (state.value.selectingInput || !client.connected) return false
        // This TV ignores KEY_POWEROFF. Use its working toggle only with fresh affirmative
        // power-state evidence, before Shield sleep can trigger a competing CEC shutdown.
        val target = configuredAddress
        val power = client.readPowerState(target, trustCertificate)
        if (target != configuredAddress || !client.connected || power != "on") {
            showStatus("TV standby skipped: Samsung did not confirm it is on.")
            return false
        }
        return client.sendKey("KEY_POWER")
    }
    suspend fun stopInputSelection() { inputJob?.cancelAndJoin() }

    fun forget() {
        inputJob?.cancel(); reconnectJob?.cancel(); configuredAddress = ""
        client.forget()
    }

    fun sendKey(key: String) {
        if (state.value.selectingInput) return
        showStatus(when {
            !client.connected -> "Connect and authorize Samsung first."
            client.sendKey(key) -> "Queued $key; confirm the result on the TV."
            else -> "Samsung connection is unavailable; reconnect."
        })
    }

    fun sendHardwareVolume(key: String): Boolean {
        if (key !in listOf("KEY_VOLUP", "KEY_VOLDOWN") || state.value.selectingInput || !client.connected) return false
        val sent = client.sendKey(key)
        showStatus(if (sent) "Queued $key; confirm the result on the TV." else "Samsung connection is unavailable; reconnect.")
        return sent
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

    override fun close() { inputJob?.cancel(); reconnectJob?.cancel(); configuredAddress = ""; client.close() }
}
