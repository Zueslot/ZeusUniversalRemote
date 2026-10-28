package com.zeus.remote.shield.ui

import com.zeus.remote.shield.ShieldController

import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ShieldControls(address: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { ShieldController(context, scope) }
    val state by controller.state.collectAsState()
    var pin by remember(address) { mutableStateOf("") }
    DisposableEffect(address) {
        onDispose { controller.disconnect() }
    }
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }
    LaunchedEffect(state.needsPin) { if (!state.needsPin) pin = "" }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("NVIDIA Shield", style = MaterialTheme.typography.titleMedium)
        Text(if (state.connected) "Connected and authenticated" else if (state.needsPin) "Pairing code required" else "Not connected")
        Text(state.message)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Turn Shield on and select its HDMI input to see the pairing code. Pair here; do not use Android's Bluetooth pairing screen.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { controller.pair(address) }, enabled = !state.busy && !state.needsPin) { Text("Pair Shield") }
            OutlinedButton(onClick = { controller.connect(address) }, enabled = !state.busy && !state.needsPin && !state.connected) { Text("Connect Shield") }
        }
        if (state.needsPin) {
            OutlinedTextField(value = pin, onValueChange = { value ->
                pin = value.filter { it in "0123456789abcdefABCDEF" }.take(6).uppercase()
            }, label = { Text("Six-character Shield code") }, singleLine = true,
                enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii))
            Button(onClick = { controller.submitPin(pin) }, enabled = pin.length == 6 && !state.busy) { Text("Confirm pairing") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { controller.disconnect() }) { Text(if (state.busy || state.needsPin) "Cancel" else "Disconnect") }
            OutlinedButton(onClick = { controller.forget(address) }, enabled = !state.busy) { Text("Forget Shield") }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { controller.sendKey(KeyEvent.KEYCODE_DPAD_UP) }, enabled = state.connected) { Text("Up") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { controller.sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }, enabled = state.connected) { Text("Left") }
                OutlinedButton(onClick = { controller.sendKey(KeyEvent.KEYCODE_DPAD_CENTER) }, enabled = state.connected) { Text("OK") }
                OutlinedButton(onClick = { controller.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }, enabled = state.connected) { Text("Right") }
            }
            OutlinedButton(onClick = { controller.sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }, enabled = state.connected) { Text("Down") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { controller.sendKey(KeyEvent.KEYCODE_BACK) }, enabled = state.connected) { Text("Back") }
                OutlinedButton(onClick = { controller.sendKey(KeyEvent.KEYCODE_HOME) }, enabled = state.connected) { Text("Home") }
            }
            OutlinedButton(onClick = { controller.sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, enabled = state.connected) { Text("Play / Pause") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { controller.sendKey(KeyEvent.KEYCODE_MEDIA_REWIND) }, enabled = state.connected) { Text("Rewind") }
                OutlinedButton(onClick = { controller.sendKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) }, enabled = state.connected) { Text("Fast-forward") }
            }
        }
        Text("Keyboard, double-tap Home, favorites and coordinated power are not implemented yet.", style = MaterialTheme.typography.bodySmall)
    }
}
