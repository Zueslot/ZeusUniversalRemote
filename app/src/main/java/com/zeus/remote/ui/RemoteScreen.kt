package com.zeus.remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeus.remote.data.DeviceRepository
import com.zeus.remote.diagnostics.ConnectivityDiagnostics
import com.zeus.remote.samsung.SamsungController
import com.zeus.remote.samsung.ui.SamsungControls
import com.zeus.remote.shield.ui.ShieldControls
import kotlinx.coroutines.launch

/** Composes the feature sections; networking and persistence live in their own packages. */
@Composable
fun RemoteScreen(devices: DeviceRepository) {
    val scope = rememberCoroutineScope()
    val samsungController = remember(devices, scope) { SamsungController(devices, scope) }
    val samsungState by samsungController.state.collectAsState()
    var samsung by remember { mutableStateOf(devices.samsungAddress) }
    var shield by remember { mutableStateOf(devices.shieldAddress) }
    var allowSelfSigned by remember { mutableStateOf(devices.trustSamsungCertificate) }
    var fastInput by remember { mutableStateOf(devices.fastHdmiInput) }
    DisposableEffect(samsungController) {
        onDispose { samsungController.close() }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ZEUS UNIVERSAL REMOTE", style = MaterialTheme.typography.headlineSmall)
            Text("Samsung S95B • HDMI 1 • NVIDIA Shield Pro", color = MaterialTheme.colorScheme.onSurface)
            HorizontalDivider()
            DeviceSettings(
                samsung = samsung,
                onSamsungChange = { samsung = it.trim(); devices.samsungAddress = samsung },
                shield = shield,
                onShieldChange = { shield = it.trim(); devices.shieldAddress = shield },
                trustSelfSigned = allowSelfSigned,
                onTrustChange = { allowSelfSigned = it; devices.trustSamsungCertificate = it },
                samsungConnected = samsungState.connected,
                onConnectSamsung = { samsungController.connect(samsung, allowSelfSigned) },
                onForgetSamsung = { samsungController.forget() },
                onTestConnectivity = {
                    scope.launch {
                        samsungController.showStatus("Testing TCP connectivity…")
                        samsungController.showStatus(ConnectivityDiagnostics.test(samsung, shield))
                    }
                }
            )
            Text(samsungState.message, color = MaterialTheme.colorScheme.onSurface)
            HorizontalDivider()
            SamsungControls(samsungController, samsungState, fastInput) {
                fastInput = it
                devices.fastHdmiInput = it
            }
            ShieldControls(address = shield)
            Text("Favorite Shield apps (pending pairing)")
            listOf("SmartTube", "Nuvio", "Stremio", "VLC", "App 5").forEach { name ->
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(name) }
            }
            Text("Security: Samsung authorization token is stored in app-private preferences. Self-signed TV TLS trust is opt-in and only used for the specified local TV connection.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
