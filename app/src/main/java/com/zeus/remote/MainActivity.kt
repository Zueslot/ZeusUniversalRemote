package com.zeus.remote

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MainActivity : ComponentActivity() {
    private var tvSocket: WebSocket? = null
    private var tvConnected = false
    private val safeClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()

    // Samsung's LAN remote endpoint commonly uses a self-signed certificate.
    // This bypass is restricted to the user-supplied private-LAN TV IP and is opt-in.
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

    override fun onDestroy() {
        tvSocket?.close(1000, "App closed")
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("devices", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val scope = rememberCoroutineScope()
                var samsung by remember { mutableStateOf(prefs.getString("samsung", "") ?: "") }
                var shield by remember { mutableStateOf(prefs.getString("shield", "") ?: "") }
                var token by remember { mutableStateOf(prefs.getString("samsung_token", "") ?: "") }
                var allowSelfSigned by remember { mutableStateOf(prefs.getBoolean("tv_self_signed", false)) }
                var status by remember { mutableStateOf("Connect to the Samsung TV to authorize Zeus Remote.") }
                var connected by remember { mutableStateOf(false) }
                fun connectSamsung() {
                    val ip = samsung.trim()
                    if (!Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.)\\d{1,3}(\\.\\d{1,3}){2}$").matches(ip)) {
                        status = "Enter a private-network IPv4 address for the TV."
                        return
                    }
                    tvSocket?.close(1000, "Reconnect")
                    tvConnected = false
                    connected = false
                    val name = Base64.encodeToString("Zeus Universal Remote".toByteArray(), Base64.NO_WRAP)
                    val url = "wss://$ip:8002/api/v2/channels/samsung.remote.control?name=$name" +
                        if (token.isNotBlank()) "&token=$token" else ""
                    status = "Connecting to Samsung TV. Approve Zeus on the TV if prompted…"
                    val client = if (allowSelfSigned) lanTvClient() else safeClient
                    tvSocket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            runOnUiThread { status = "Samsung socket open; waiting for TV authorization…" }
                        }
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            try {
                                val message = JSONObject(text)
                                when (message.optString("event")) {
                                    "ms.channel.connect" -> {
                                        val newToken = message.optJSONObject("data")?.optString("token").orEmpty()
                                        if (newToken.isNotBlank()) {
                                            prefs.edit().putString("samsung_token", newToken).apply()
                                            runOnUiThread { token = newToken }
                                        }
                                        tvConnected = true
                                        runOnUiThread { connected = true; status = "Samsung authorized. TV controls are ready." }
                                    }
                                    "ms.channel.unauthorized", "ms.channel.timeOut" -> runOnUiThread {
                                        connected = false; tvConnected = false
                                        status = "Samsung denied or timed out. Check TV device authorization."
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            tvConnected = false
                            runOnUiThread { connected = false; status = "Samsung connection failed: ${t.message ?: t.javaClass.simpleName}" }
                        }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            tvConnected = false
                            runOnUiThread { connected = false; status = "Samsung disconnected: $reason" }
                        }
                    })
                }
                fun sendTvKey(key: String) {
                    if (!connected || !tvConnected) { status = "Connect and authorize Samsung first."; return }
                    val payload = JSONObject().put("method", "ms.remote.control")
                        .put("params", JSONObject().put("Cmd", "Click")
                            .put("DataOfCmd", key).put("Option", "false").put("TypeOfRemote", "SendRemoteKey"))
                        .toString()
                    if (tvSocket?.send(payload) == true) status = "Sent $key to Samsung TV"
                    else status = "Samsung connection is unavailable; reconnect."
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ZEUS UNIVERSAL REMOTE", style = MaterialTheme.typography.headlineSmall)
                    Text("Samsung S95B • HDMI 1 • NVIDIA Shield Pro", color = MaterialTheme.colorScheme.onSurface)
                    HorizontalDivider()
                    Text("Device setup", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(samsung, { samsung = it.trim(); prefs.edit().putString("samsung", samsung).apply() }, label = { Text("Samsung TV IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(shield, { shield = it.trim(); prefs.edit().putString("shield", shield).apply() }, label = { Text("Shield IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row { Checkbox(allowSelfSigned, { allowSelfSigned = it; prefs.edit().putBoolean("tv_self_signed", it).apply() }); Text("Trust Samsung TV self-signed certificate on local network", modifier = Modifier.padding(top = 12.dp)) }
                    Button(onClick = { connectSamsung() }, modifier = Modifier.fillMaxWidth()) { Text(if (connected) "Reconnect Samsung TV" else "Pair / Connect Samsung TV") }
                    OutlinedButton(onClick = {
                        tvSocket?.close(1000, "Forget authorization")
                        prefs.edit().remove("samsung_token").apply(); token = ""; connected = false; tvConnected = false
                        status = "Samsung token cleared. Reconnect to request TV authorization."
                    }, modifier = Modifier.fillMaxWidth()) { Text("Forget Samsung authorization") }
                    Button(onClick = {
                        scope.launch {
                            status = "Testing TCP connectivity…"
                            status = withContext(Dispatchers.IO) {
                                listOf(Triple("Samsung remote", samsung, 8002), Triple("Shield pairing", shield, 6467), Triple("Shield control", shield, 6466)).joinToString("\n") { (label, ip, port) ->
                                    val result = if (ip.isBlank()) "IP missing" else try {
                                        Socket().use { it.connect(InetSocketAddress(ip, port), 2500) }; "Reachable"
                                    } catch (e: Exception) { "Unavailable (${e.javaClass.simpleName})" }
                                    "$label ($port): $result"
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Test connectivity") }
                    Text(status, color = MaterialTheme.colorScheme.onSurface)
                    HorizontalDivider()
                    Text("Samsung controls", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { sendTvKey("KEY_POWER") }) { Text("TV Power") }
                        OutlinedButton(onClick = { sendTvKey("KEY_HDMI1") }) { Text("HDMI 1") }
                        OutlinedButton(onClick = { sendTvKey("KEY_MUTE") }) { Text("Mute") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { sendTvKey("KEY_VOLDOWN") }) { Text("Volume −") }
                        OutlinedButton(onClick = { sendTvKey("KEY_VOLUP") }) { Text("Volume +") }
                    }
                    Text("Shield controls — awaiting secure pairing implementation", style = MaterialTheme.typography.titleMedium)
                    Text("The Shield ports are reachable, but navigation and app launching require Android TV Remote v2 TLS pairing. These buttons are intentionally disabled.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("↑", "←", "OK", "→", "↓").forEach { label -> OutlinedButton(onClick = {}, enabled = false, contentPadding = PaddingValues(10.dp)) { Text(label) } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Back", "Home", "Play").forEach { label -> OutlinedButton(onClick = {}, enabled = false) { Text(label) } }
                    }
                    Text("Favorite Shield apps (pending pairing)")
                    listOf("SmartTube", "Nuvio", "Stremio", "VLC", "App 5").forEach { name ->
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(name) }
                    }
                    Text("Security: Samsung authorization token is stored in app-private preferences. Self-signed TV TLS trust is opt-in and only used for the specified local TV connection.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
