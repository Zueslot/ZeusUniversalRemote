package com.zeus.remote.samsung

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.zeus.remote.data.DeviceRepository
import com.zeus.remote.power.WakeOnLan
import com.zeus.shared.isPrivateIpv4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Owns Samsung's socket and authorization. Call public methods on the main thread. */
class SamsungRemoteClient(
    private val devices: DeviceRepository,
    private val onState: (connected: Boolean, message: String) -> Unit,
    private val onDisconnected: () -> Unit = {}
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val safeClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    var connected = false
        private set
    var connecting = false
        private set
    var authorizationDenied = false
        private set

    // Preserve the existing explicit opt-in, limited to the validated private TV endpoint.
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun lanTvClient(): OkHttpClient {
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(trust), SecureRandom())
        return safeClient.newBuilder().sslSocketFactory(context.socketFactory, trust)
            .hostnameVerifier { _, _ -> true }.readTimeout(0, TimeUnit.MILLISECONDS).build()
    }

    fun connect(address: String, trustSelfSigned: Boolean) {
        val ip = address.trim()
        if (!isPrivateIpv4(ip)) {
            onState(connected, "Enter a private-network IPv4 address for the TV.")
            return
        }
        close()
        connecting = true
        authorizationDenied = false
        onState(false, "Connecting to Samsung TV. Approve Zeus on the TV if prompted…")
        val name = Base64.encodeToString("Zeus Universal Remote".toByteArray(), Base64.NO_WRAP)
        val token = devices.samsungToken
        val url = "wss://$ip:8002/api/v2/channels/samsung.remote.control?name=$name" +
            if (token.isNotBlank()) "&token=$token" else ""
        val client = if (trustSelfSigned) lanTvClient() else safeClient
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            // Ignore late callbacks from a socket closed during reconnect/forget/disposal.
            private fun update(webSocket: WebSocket, action: () -> Unit) {
                main.post { if (socket === webSocket) action() }
            }
            override fun onOpen(webSocket: WebSocket, response: Response) = update(webSocket) {
                onState(false, "Samsung socket open; waiting for TV authorization…")
            }
            override fun onMessage(webSocket: WebSocket, text: String) = update(webSocket) {
                try {
                    val message = JSONObject(text)
                    when (message.optString("event")) {
                        "ms.channel.connect" -> {
                            val tokenValue = message.optJSONObject("data")?.optString("token").orEmpty()
                            if (tokenValue.isNotBlank()) devices.samsungToken = tokenValue
                            connected = true
                            connecting = false
                            onState(true, "Samsung authorized. TV controls are ready.")
                        }
                        "ms.channel.unauthorized", "ms.channel.timeOut" -> {
                            connected = false
                            connecting = false
                            authorizationDenied = true
                            onState(false, "Samsung denied or timed out. Check TV device authorization.")
                        }
                    }
                } catch (_: Exception) { }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = update(webSocket) {
                connected = false
                connecting = false
                onState(false, "Samsung connection failed: ${t.message ?: t.javaClass.simpleName}")
                onDisconnected()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = update(webSocket) {
                connected = false
                connecting = false
                onState(false, "Samsung disconnected: $reason")
                onDisconnected()
            }
        })
    }

    fun sendKey(key: String): Boolean {
        if (!connected) return false
        val payload = JSONObject().put("method", "ms.remote.control")
            .put("params", JSONObject().put("Cmd", "Click").put("DataOfCmd", key)
                .put("Option", "false").put("TypeOfRemote", "SendRemoteKey")).toString()
        return socket?.send(payload) == true
    }

    suspend fun discoverWakeMac(address: String, trustSelfSigned: Boolean): String? =
        readDevice(address, trustSelfSigned)?.let { WakeOnLan.normalizeMac(it.optString("wifiMac")) }

    suspend fun readPowerState(address: String, trustSelfSigned: Boolean): String? =
        readDevice(address, trustSelfSigned, timeoutSeconds = 2)?.optString("PowerState")?.lowercase()

    private suspend fun readDevice(address: String, trustSelfSigned: Boolean, timeoutSeconds: Long = 7): JSONObject? = withContext(Dispatchers.IO) {
        if (!isPrivateIpv4(address)) return@withContext null
        try {
            val client = (if (trustSelfSigned) lanTvClient() else safeClient).newBuilder()
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS).callTimeout(timeoutSeconds, TimeUnit.SECONDS).followRedirects(false).build()
            client.newCall(Request.Builder().url("https://$address:8002/api/v2/").build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > 1024 * 1024) return@use null
                JSONObject(body.string()).optJSONObject("device")
            }
        } catch (_: Exception) { null }
    }

    fun forget() {
        close()
        devices.forgetSamsungToken()
        onState(false, "Samsung token cleared. Reconnect to request TV authorization.")
    }

    override fun close() {
        val previous = socket
        socket = null
        connected = false
        connecting = false
        previous?.close(1000, "Connection closed")
    }
}
