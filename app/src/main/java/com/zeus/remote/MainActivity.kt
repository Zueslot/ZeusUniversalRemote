package com.zeus.remote

import android.os.Bundle
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
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("devices", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val scope = rememberCoroutineScope()
                var samsung by remember { mutableStateOf(prefs.getString("samsung", "") ?: "") }
                var shield by remember { mutableStateOf(prefs.getString("shield", "") ?: "") }
                var status by remember { mutableStateOf("Enter local IP addresses and tap Test connectivity.") }
                var samsungToken by remember { mutableStateOf(prefs.getString("samsung_token", "") ?: "") }
                val client = remember { OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build() }
                fun tvKey(key: String) {
                    if (samsung.isBlank()) { status = "Set Samsung IP first."; return }
                    val encodedName = Base64.getEncoder().encodeToString("Zeus Remote".toByteArray())
                    val tokenQuery = if (samsungToken.isNotBlank()) "&token=$samsungToken" else ""
                    val url = "wss://$samsung:8002/api/v2/channels/samsung.remote.control?name=$encodedName$tokenQuery"
                    // Samsung TV uses a self-signed TLS certificate. Do not disable TLS validation silently.
                    status = "Samsung remote command not sent: secure TV certificate trust and pairing must be configured first."
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ZEUS UNIVERSAL REMOTE", style = MaterialTheme.typography.headlineSmall)
                    Text("Samsung S95B • HDMI 1 • NVIDIA Shield Pro", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("Device setup", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value=samsung, onValueChange={samsung=it.trim(); prefs.edit().putString("samsung",samsung).apply()}, label={Text("Samsung S95B IP address")}, singleLine=true, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=shield, onValueChange={shield=it.trim(); prefs.edit().putString("shield",shield).apply()}, label={Text("NVIDIA Shield IP address")}, singleLine=true, modifier=Modifier.fillMaxWidth())
                    Button(onClick={
                        scope.launch {
                            status="Testing TCP connectivity…"
                            val results = withContext(Dispatchers.IO) {
                                listOf(Triple("Samsung remote",samsung,8002), Triple("Shield pairing",shield,6467), Triple("Shield control",shield,6466)).map { (name,ip,port) ->
                                    val result = if (ip.isBlank()) "IP missing" else try { Socket().use { it.connect(InetSocketAddress(ip,port),2500) }; "Reachable" } catch(e:Exception) { "Unavailable (${e.javaClass.simpleName})" }
                                    "$name ($port): $result"
                                }.joinToString("\n")
                            }
                            status=results
                        }
                    },modifier=Modifier.fillMaxWidth()) { Text("Test connectivity") }
                    Text(status)
                    HorizontalDivider()
                    Text("Remote layout preview", style=MaterialTheme.typography.titleMedium)
                    Text("Controls are placeholders until authenticated pairing is implemented.")
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Power", "HDMI 1", "Mute").forEach { label -> OutlinedButton(onClick={status="$label requires paired device control"}) { Text(label) } } }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("↑","←","OK","→","↓").forEach { label -> OutlinedButton(onClick={status="Shield pairing required for $label"},contentPadding=PaddingValues(10.dp)) { Text(label) } } }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Back","Home","Play").forEach { label -> OutlinedButton(onClick={status="Shield pairing required for $label"}) { Text(label) } } }
                    Text("Favorite apps (preview)")
                    listOf("SmartTube", "Nuvio", "Stremio", "VLC", "App 5").forEach { name -> OutlinedButton(onClick={status="App launch needs Shield pairing: $name"},modifier=Modifier.fillMaxWidth()) { Text(name) } }
                    HorizontalDivider()
                    Text("Security: IP addresses are stored locally. No pairing secrets are collected in this prototype.",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
