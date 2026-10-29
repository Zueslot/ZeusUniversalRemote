package com.zeus.remote.shield

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import android.view.KeyEvent

data class ShieldState(
    val message: String = "Pair Shield to enable navigation.",
    val busy: Boolean = false,
    val needsPin: Boolean = false,
    val connected: Boolean = false,
    val isOn: Boolean? = null,
    val pairingRequired: Boolean = false
)

/** Owned by the screen's coroutine scope. Public actions and state updates run on Main. */
class ShieldController(context: Context, private val scope: CoroutineScope) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(ShieldState())
    val state = mutableState.asStateFlow()
    private var generation = 0
    private var task: Job? = null
    private var pairing: ShieldPairingClient? = null
    private var remote: ShieldRemoteClient? = null
    private var host = ""
    private var reconnectEnabled = false

    private fun stop() {
        generation++
        pairing?.close(); pairing = null
        remote?.close(); remote = null
        task?.cancel(); task = null
    }

    fun disconnect() {
        reconnectEnabled = false
        stop()
        mutableState.value = ShieldState("Shield disconnected.")
    }

    /** Restore an existing identity without initiating a fresh PIN pairing prompt. */
    fun reconnectSaved(address: String) {
        if (!privateIpv4(address.trim())) return
        reconnectEnabled = true
        stop()
        val run = generation
        val target = address.trim()
        mutableState.value = ShieldState("Restoring Shield connection...", busy = true)
        task = scope.launch {
            try {
                val paired = runInterruptible(Dispatchers.IO) { ShieldCredentials(appContext, target).isPaired() }
                if (generation == run) {
                    if (paired) connect(target) else mutableState.value = ShieldState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == run) mutableState.value = ShieldState("Could not restore Shield: ${detail(e)}")
            }
        }
    }

    fun pair(address: String) {
        if (!valid(address)) return
        stop()
        reconnectEnabled = false
        host = address.trim()
        val run = generation
        mutableState.value = ShieldState("Connecting to Shield pairing...", busy = true)
        task = scope.launch {
            try {
                val identity = runInterruptible(Dispatchers.IO) { ShieldCredentials(appContext, host) }
                val client = ShieldPairingClient(identity)
                pairing = client
                client.start(host)
                if (generation != run) return@launch
                mutableState.value = ShieldState("Enter the six-character code displayed by Shield.", needsPin = true)
                // Keep the pairing transport alive while waiting for user input, but bound its lifetime.
                delay(120000)
                if (generation == run) {
                    client.close(); pairing = null
                    mutableState.value = ShieldState("Pairing code expired. Start pairing again.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == run) {
                    pairing?.close(); pairing = null
                    mutableState.value = ShieldState("Pairing failed: ${detail(e)}")
                }
            }
        }
    }

    fun submitPin(pin: String) {
        if (!state.value.needsPin || state.value.busy) return
        if (!Regex("[0-9A-Fa-f]{6}").matches(pin.trim())) {
            mutableState.value = state.value.copy(message = "Use all six hexadecimal characters (0-9, A-F).")
            return
        }
        val client = pairing ?: return
        val run = generation
        task?.cancel()
        mutableState.value = state.value.copy(message = "Verifying Shield code...", busy = true)
        task = scope.launch {
            try {
                client.finish(pin.trim())
                client.close(); pairing = null
                if (generation == run) connect(host)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == run) {
                    client.close(); pairing = null
                    mutableState.value = ShieldState("Pairing failed: ${detail(e)} Start pairing again.")
                }
            }
        }
    }

    fun connect(address: String) {
        if (!valid(address)) return
        stop()
        reconnectEnabled = true
        host = address.trim()
        val run = generation
        mutableState.value = ShieldState("Connecting to authenticated Shield control...", busy = true)
        task = scope.launch {
            val identity = try {
                runInterruptible(Dispatchers.IO) {
                    ShieldCredentials(appContext, host).also { check(it.isPaired()) { "Pair this Shield first." } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == run) mutableState.value = ShieldState(detail(e))
                return@launch
            }
            var attempts = 0
            while (isActive && generation == run) {
                val client = ShieldRemoteClient(identity)
                remote = client
                try {
                    client.run(host) { isOn ->
                        if (generation == run) {
                            attempts = 0
                            mutableState.value = ShieldState("Shield authenticated. Navigation is ready.", connected = true, isOn = isOn)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (generation != run) return@launch
                    if (ShieldReconnectPolicy.requiresPairing(e)) {
                        mutableState.value = ShieldState("Shield certificate verification failed. Pair again if credentials changed. ${detail(e)}", pairingRequired = true)
                        return@launch
                    }
                    attempts = (attempts + 1).coerceAtMost(30)
                    mutableState.value = ShieldState("Shield disconnected: ${detail(e)} Retrying automatically...", busy = true)
                } finally {
                    client.close()
                    if (remote === client) remote = null
                }
                delay(ShieldReconnectPolicy.delayMillis(attempts))
            }
        }
    }

    fun sendKey(code: Int) {
        if (!state.value.connected) return
        val client = remote ?: return
        val run = generation
        scope.launch {
            try {
                client.sendKey(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (run == generation) {
                    mutableState.value = ShieldState("Shield command failed: ${detail(e)} Reconnecting...", busy = true)
                    client.close() // Wake the reader and let its reconnect loop recover.
                }
            }
        }
    }

    suspend fun sendPowerCommand(wake: Boolean): Boolean {
        val client = remote ?: return false
        val run = generation
        if (!state.value.connected) return false
        return try {
            client.sendKey(if (wake) KeyEvent.KEYCODE_WAKEUP else KeyEvent.KEYCODE_SLEEP)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            client.close()
            if (run == generation && remote === client) {
                mutableState.value = ShieldState("Shield power request failed: ${detail(e)}", busy = true)
            }
            false
        }
    }

    /** Refresh stale foreground sessions without re-pairing or undoing an explicit Disconnect. */
    fun resumeConnection(address: String) {
        if (!reconnectEnabled || state.value.needsPin || state.value.pairingRequired) return
        val last = remote?.lastReceivedNanos ?: 0L
        if (!state.value.connected || System.nanoTime() - last > 15_000_000_000L) reconnectSaved(address)
    }

    /** One tap owns reconnect + bounded retries; only explicit Wake/Sleep commands are repeated. */
    suspend fun setPower(address: String, wake: Boolean): Boolean {
        if (!privateIpv4(address.trim()) || state.value.needsPin || state.value.pairingRequired) return false
        // Never trust a socket carried across phone/Shield sleep for a power request.
        reconnectSaved(address)
        repeat(3) { attempt ->
            if (!reconnectEnabled || state.value.needsPin || state.value.pairingRequired) return false
            val ready = withTimeoutOrNull(12000) {
                state.first { it.connected || it.pairingRequired || (!it.busy && !it.needsPin) }
            }
            if (ready?.pairingRequired == true || (ready != null && !ready.connected)) return false
            if (ready?.connected == true && sendPowerCommand(wake)) {
                val confirmed = withTimeoutOrNull(4000) { state.first { it.connected && it.isOn == wake } }
                if (confirmed != null) return true
            }
            if (!reconnectEnabled || state.value.needsPin || state.value.pairingRequired) return false
            if (attempt < 2) reconnectSaved(address)
        }
        mutableState.value = state.value.copy(message = "Shield did not confirm power. Reconnection will continue; check Shield power and network.")
        return false
    }

    fun forget(address: String) {
        if (!valid(address)) return
        stop()
        reconnectEnabled = false
        val run = generation
        mutableState.value = ShieldState("Removing this Shield's local credentials...", busy = true)
        task = scope.launch {
            try {
                runInterruptible(Dispatchers.IO) { ShieldCredentials(appContext, address.trim()).forget() }
                if (generation == run) mutableState.value = ShieldState("Shield credentials removed. Pair again to connect.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { if (generation == run) mutableState.value = ShieldState("Could not remove credentials: ${detail(e)}") }
        }
    }

    private fun valid(address: String): Boolean {
        if (privateIpv4(address.trim())) return true
        mutableState.value = state.value.copy(message = "Enter a private-network IPv4 address for Shield.")
        return false
    }

    private fun detail(error: Exception): String = when (error) {
        is java.net.SocketTimeoutException -> "Shield did not respond in time. Check its power and network."
        else -> error.message ?: error.javaClass.simpleName
    }

    override fun close() { reconnectEnabled = false; stop() }
}
