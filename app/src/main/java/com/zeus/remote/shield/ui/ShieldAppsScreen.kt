package com.zeus.remote.shield.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.zeus.remote.data.FavoriteAppNames
import com.zeus.remote.shield.apps.ShieldAppsController
import com.zeus.remote.ui.FavoriteAppTiles
import com.zeus.remote.ui.hapticClick
import com.zeus.remote.ui.glassBorder
import com.zeus.remote.ui.glassSheen
import com.zeus.shared.ShieldApp

@Composable
fun ShieldAppsScreen(address: String, controller: ShieldAppsController) {
    val state by controller.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var assignApp by remember { mutableStateOf<ShieldApp?>(null) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { controller.refresh(address, it) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Shield apps", style = MaterialTheme.typography.headlineMedium)
        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = hapticClick {
            scanner.launch(ScanOptions().setDesiredBarcodeFormats(listOf("QR_CODE"))
                .setPrompt("Scan the QR code on Zeus Shield Companion").setBeepEnabled(false).setOrientationLocked(false))
        }, enabled = !state.busy) { Text(if (state.connected) "Scan another companion code" else "Scan Shield companion QR") }
        if (!state.connected && state.apps.isEmpty()) {
            Text("Install Zeus Shield Companion on your Shield and open it on the TV. Select Start companion, then scan its QR code here. This lists your installed TV and sideloaded launcher apps, not hidden system services.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = hapticClick { controller.refresh(address) }, enabled = !state.busy) { Text("Refresh apps") }
            TextButton(onClick = hapticClick { controller.forget() }, enabled = !state.busy) { Text("Forget companion") }
        }
        Text("Your favorites", style = MaterialTheme.typography.titleMedium)
        FavoriteAppTiles(large = true, onAppClick = { controller.launchFavorite(it, address) }, available = controller::favoriteAvailable,
            displayName = state::favoriteName)
        if (state.connected) {
            HorizontalDivider()
            Text("Installed apps (${state.apps.size})", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(query, { query = it }, label = { Text("Search Shield apps") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val filtered = state.apps.filter { it.name.contains(query, ignoreCase = true) }
            if (filtered.isEmpty()) Text("No matching apps.")
            filtered.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { app ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
                            border = glassBorder()) {
                            Column(Modifier.glassSheen().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(app.name, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = hapticClick { controller.launchApp(app.packageName) },
                                    enabled = state.launchAllowed && !state.busy) { Text("Launch") }
                                TextButton(onClick = hapticClick { assignApp = app }, enabled = !state.busy) { Text("Set favorite") }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    assignApp?.let { app ->
        AlertDialog(onDismissRequest = { assignApp = null }, title = { Text("Favorite: ${app.name}") },
            text = {
                Column {
                    Text("Choose which shortcut opens this app:")
                    FavoriteAppNames.forEachIndexed { index, label ->
                        TextButton(onClick = hapticClick { controller.assign(label, app.packageName); assignApp = null }) {
                            Text("${index + 1}. ${state.favoriteName(label)}")
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { assignApp = null }) { Text("Cancel") } })
    }
}
