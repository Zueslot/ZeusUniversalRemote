package com.zeus.remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DeviceSettings(
    samsung: String,
    onSamsungChange: (String) -> Unit,
    shield: String,
    onShieldChange: (String) -> Unit,
    trustSelfSigned: Boolean,
    onTrustChange: (Boolean) -> Unit,
    samsungConnected: Boolean,
    onConnectSamsung: () -> Unit,
    onForgetSamsung: () -> Unit,
    onTestConnectivity: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Device setup", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(samsung, onSamsungChange, label = { Text("Samsung TV IP") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(shield, onShieldChange, label = { Text("Shield IP") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row {
            Checkbox(trustSelfSigned, onTrustChange)
            Text("Trust Samsung TV self-signed certificate on local network", modifier = Modifier.padding(top = 12.dp))
        }
        Button(onClick = onConnectSamsung, modifier = Modifier.fillMaxWidth()) {
            Text(if (samsungConnected) "Reconnect Samsung TV" else "Pair / Connect Samsung TV")
        }
        OutlinedButton(onClick = onForgetSamsung, modifier = Modifier.fillMaxWidth()) { Text("Forget Samsung authorization") }
        Button(onClick = onTestConnectivity, modifier = Modifier.fillMaxWidth()) { Text("Test connectivity") }
    }
}
