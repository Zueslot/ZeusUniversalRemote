package com.zeus.remote.power

import com.zeus.remote.data.DeviceRepository
import com.zeus.remote.samsung.SamsungController
import com.zeus.remote.shield.ShieldController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class PowerState(val busy: Boolean = false, val message: String = "Press Power to turn both devices on or put them in standby.")

/** Never infer TV power from a disconnected socket; Samsung checks its state before standby. */
class PowerController(
    private val devices: DeviceRepository, private val samsung: SamsungController,
    private val shield: ShieldController, private val scope: CoroutineScope
) : AutoCloseable {
    private val mutable = MutableStateFlow(PowerState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun toggle() {
        if (state.value.busy) return
        mutable.value = PowerState(true, "Checking TV power...")
        job = scope.launch {
            try {
                samsung.stopInputSelection()
                // A missing response is not proof of standby. Choose idempotent Wake in
                // that case, rather than risk turning an already-on TV off blindly.
                val wake = samsung.readPowerState() != "on"
                mutable.value = PowerState(true, if (wake) "Waking both devices..." else "Putting both devices in standby...")
                if (wake) wakeBoth() else sleepBoth()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = PowerState(false, "Power request failed: ${e.message}") }
            finally { mutable.value = state.value.copy(busy = false) }
        }
    }

    private suspend fun wakeBoth() {
        val mac = WakeOnLan.normalizeMac(devices.samsungMac)
        var wolSent = false
        var wolError: String? = null
        if (mac != null) {
            try { WakeOnLan.send(mac, devices.samsungAddress); wolSent = true }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { wolError = e.message }
        }
        mutable.value = PowerState(true, "Connecting to Shield to send Wake. TV wake uses CEC${if (wolSent) " and Wake-on-LAN" else ""}...")
        val shieldSent = shield.setPower(devices.shieldAddress, wake = true)
        // Force a fresh authorization check: the old socket may still look open during standby.
        samsung.connect(devices.samsungAddress, devices.trustSamsungCertificate)
        mutable.value = PowerState(true, "Wake requests sent. Waiting up to 45 seconds for Samsung authorization...")
        val tvReady = samsung.state.value.connected || withTimeoutOrNull(45000) { samsung.state.first { it.connected }; true } == true
        if (tvReady && devices.powerHdmiFallback) {
            samsung.selectHdmi1(devices.fastHdmiInput)
            mutable.value = PowerState(true, "Samsung connected. Selecting HDMI 1 with the menu fallback...")
            withTimeoutOrNull(40000) { samsung.state.first { !it.selectingInput } }
        }
        mutable.value = PowerState(false, buildString {
            append(if (shieldSent) "Shield confirmed awake. " else "Shield wake was not confirmed; check pairing and standby network access. ")
            append(if (tvReady) "Samsung authorized. Confirm both devices and HDMI input on the TV."
                else "Samsung has not reconnected. Enable Power On with Mobile/Anynet+ and Shield CEC TV-on, and check the TV MAC address.")
            if (mac == null) append(" TV MAC is missing; direct Wake-on-LAN was not sent.")
            if (wolError != null) append(" Wake-on-LAN failed: $wolError")
        })
    }

    private suspend fun sleepBoth() {
        val tvSent = samsung.requestStandby()
        // Give Samsung's working power key time to take effect before Shield CEC sleep.
        if (tvSent) delay(1000)
        val shieldSent = shield.setPower(devices.shieldAddress, wake = false)
        mutable.value = PowerState(false, buildString {
            append(if (shieldSent) "Shield confirmed standby; HDMI-CEC can put Samsung in standby. " else "Shield standby was not confirmed. ")
            append(if (tvSent) "Samsung reported on; its power key was sent. " else "Samsung could not confirm it was on; TV-off depends on Shield CEC. ")
            append("Confirm both devices are in standby.")
        })
    }

    fun cancel() { job?.cancel(); mutable.value = PowerState(false, "Stopped waiting. Power commands already sent cannot be undone.") }
    override fun close() { job?.cancel() }
}
