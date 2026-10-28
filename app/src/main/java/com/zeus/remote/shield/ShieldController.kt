package com.zeus.remote.shield

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.view.KeyEvent

data class ShieldState(
    val message: String = "Pair Shield to enable navigation.",
    val busy: Boolean = false,
    val needsPin: Boolean = false,
    val connected: Boolean = false
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

    private fun stop() {
        generation++
        pairing?.close(); pairing = null
        remote?.close(); remote = null
        task?.cancel(); task = null
    }

    fun disconnect() {
        stop()
        mutableState.value = ShieldState("Shield disconnected.")
    }

    /** Restore an existing identity without initiating a fresh PIN pairing prompt. */
    fun reconnectSaved(address: String) {
        if (!privateIpv4(address.trim())) return
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
                    client.run(host) {
                        attempts = 0
                        mutableState.value = ShieldState("Shield authenticated. Navigation is ready.", connected = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (generation != run) return@launch
                    if (generateSequence<Throwable>(e) { it.cause }.any { it is javax.net.ssl.SSLException }) {
                        mutableState.value = ShieldState("Shield TLS authentication failed. Pair again if credentials changed. ${detail(e)}")
                        return@launch
                    }
                    attempts++
                    if (attempts >= 5) {
                        mutableState.value = ShieldState("Shield unavailable after five attempts: ${detail(e)} Tap Connect to retry.")
                        return@launch
                    }
                    mutableState.value = ShieldState("Shield disconnected: ${detail(e)} Reconnecting ($attempts/5)...", busy = true)
                } finally { client.close() }
                delay((1000L shl (attempts - 1).coerceIn(0, 4)))
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
        if (!state.value.connected) return false
        return try {
            client.sendKey(if (wake) KeyEvent.KEYCODE_WAKEUP else KeyEvent.KEYCODE_SLEEP)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            client.close()
            mutableState.value = ShieldState("Shield power request failed: ${detail(e)}", busy = true)
            false
        }
    }

    fun forget(address: String) {
        if (!valid(address)) return
        stop()
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

    override fun close() = stop()
}
