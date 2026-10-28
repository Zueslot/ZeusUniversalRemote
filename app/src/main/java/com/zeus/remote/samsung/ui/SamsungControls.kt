package com.zeus.remote.samsung.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeus.remote.samsung.SamsungController
import com.zeus.remote.samsung.SamsungState

@Composable
fun SamsungControls(controller: SamsungController, state: SamsungState, fastInput: Boolean, onFastInputChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Samsung controls", style = MaterialTheme.typography.titleMedium)
        Row {
            Checkbox(checked = fastInput, onCheckedChange = onFastInputChange, enabled = !state.selectingInput)
            Text("Faster HDMI selection (about 7 seconds). Turn off for the tested 32-second timing.")
        }
        Button(onClick = { controller.selectHdmi1(fastInput) }, enabled = state.connected && !state.selectingInput,
            modifier = Modifier.fillMaxWidth()) {
            Text(if (state.selectingInput) "Selecting HDMI 1..." else "HDMI 1 / Shield")
        }
        if (state.selectingInput) {
            Text("Selecting HDMI 1 takes about ${if (fastInput) 7 else 32} seconds. Leave the TV menu and physical remote untouched.")
            OutlinedButton(onClick = { controller.cancelInputSelection() }) { Text("Cancel input selection") }
        }
        Text("Select HDMI 1: open Source and use the TV navigation below to select HDMI 1 or your Shield. If Source does nothing, open TV Home and navigate to Connected Devices.")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                listOf("TV Home" to "KEY_HOME", "Up" to "KEY_UP"),
                listOf("Left" to "KEY_LEFT", "OK" to "KEY_ENTER", "Right" to "KEY_RIGHT"),
                listOf("TV Back" to "KEY_RETURN", "Down" to "KEY_DOWN"),
                listOf("TV Exit" to "KEY_EXIT")
            ).forEach { buttons ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEach { (label, key) ->
                        OutlinedButton(onClick = { controller.sendKey(key) }, enabled = state.connected) { Text(label) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { controller.sendKey("KEY_POWER") }) { Text("TV Power") }
            OutlinedButton(onClick = { controller.sendKey("KEY_SOURCE") }) { Text("Source") }
            OutlinedButton(onClick = { controller.sendKey("KEY_MUTE") }) { Text("Mute") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { controller.sendKey("KEY_VOLDOWN") }) { Text("Volume −") }
            OutlinedButton(onClick = { controller.sendKey("KEY_VOLUP") }) { Text("Volume +") }
        }
    }
}
