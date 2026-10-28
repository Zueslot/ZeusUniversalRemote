package com.zeus.remote.samsung

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.zeus.remote.data.DeviceRepository
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
    private val onState: (connected: Boolean, message: String) -> Unit
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val safeClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    var connected = false
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
        if (!Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.)\\d{1,3}(\\.\\d{1,3}){2}$").matches(ip)) {
            onState(connected, "Enter a private-network IPv4 address for the TV.")
            return
        }
        close()
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
                            onState(true, "Samsung authorized. TV controls are ready.")
                        }
                        "ms.channel.unauthorized", "ms.channel.timeOut" -> {
                            connected = false
                            onState(false, "Samsung denied or timed out. Check TV device authorization.")
                        }
                    }
                } catch (_: Exception) { }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = update(webSocket) {
                connected = false
                onState(false, "Samsung connection failed: ${t.message ?: t.javaClass.simpleName}")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = update(webSocket) {
                connected = false
                onState(false, "Samsung disconnected: $reason")
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

    fun forget() {
        close()
        devices.forgetSamsungToken()
        onState(false, "Samsung token cleared. Reconnect to request TV authorization.")
    }

    override fun close() {
        val previous = socket
        socket = null
        connected = false
        previous?.close(1000, "Connection closed")
    }
}
