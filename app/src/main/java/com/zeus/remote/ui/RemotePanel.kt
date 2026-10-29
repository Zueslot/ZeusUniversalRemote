package com.zeus.remote.ui

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeus.remote.data.DeviceRepository
import com.zeus.remote.data.RemoteControl
import com.zeus.remote.samsung.SamsungController
import com.zeus.remote.samsung.SamsungState
import com.zeus.remote.shield.ShieldController
import com.zeus.remote.shield.ShieldState
import com.zeus.remote.ui.layout.RemoteLayoutEditor

@Composable
fun RemotePanel(
    samsung: SamsungController, tv: SamsungState, shield: ShieldController, player: ShieldState,
    devices: DeviceRepository, onEditingChanged: (Boolean) -> Unit,
    onReconnectDevices: () -> Unit,
    onShieldUnavailable: () -> Unit,
    onPower: () -> Unit, powerBusy: Boolean,
    onOpenApps: () -> Unit, onOpenSettings: () -> Unit, onToggleTheme: () -> Unit,
    onFavorite: (String) -> Unit, favoriteAvailable: (String) -> Boolean, favoriteName: (String) -> String,
    modifier: Modifier = Modifier
) {
    var layoutJson by rememberSaveable { mutableStateOf(devices.remoteLayout) }
    LaunchedEffect(Unit) {
        val aligned = com.zeus.remote.data.RemoteLayout.decode(layoutJson).encode()
        devices.remoteLayout = aligned
        layoutJson = aligned
    }
    val tvReady = tv.connected && !tv.selectingInput
    Box(modifier.fillMaxSize()) {
        RemoteLayoutEditor(layoutJson, onSave = { devices.remoteLayout = it; layoutJson = it }, onEditingChanged = onEditingChanged,
            connectionStatus = "Samsung: ${if (tv.connected) "connected" else "disconnected"}\nShield: ${if (player.connected) "connected" else if (player.busy) "connecting" else "disconnected"}",
            onReconnectDevices = onReconnectDevices,
            displayLabel = { control -> favoriteName(control.label) },
            onOpenApps = onOpenApps, onOpenSettings = onOpenSettings, onToggleTheme = onToggleTheme) { control, editing ->
            // The editor intercepts pointer input; these guards also block accessibility/keyboard actions.
            when (control) {
                RemoteControl.BRANDING -> Column(Modifier.fillMaxSize().padding(start = 12.dp, top = 6.dp)) {
                    Text("ZEUS", fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text("ONE ROOM. ONE REMOTE.", fontSize = 9.sp, lineHeight = 14.sp, letterSpacing = 1.7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RemoteControl.POWER -> RoundControl("Power actions for Samsung and Shield", RemoteIcon.Power,
                    { if (!editing) onPower() }, enabled = editing || !powerBusy,
                    caption = "POWER", tint = MaterialTheme.colorScheme.error)
                RemoteControl.SOURCE -> RoundControl("Open Samsung Source menu", RemoteIcon.Source,
                    { if (!editing) samsung.sendKey("KEY_SOURCE") }, enabled = editing || tvReady,
                    diameter = 52.dp, caption = "SOURCE")
                RemoteControl.DPAD -> DirectionPad(true, { icon ->
                    if (!editing && !player.connected) onShieldUnavailable()
                    else if (!editing) shield.sendKey(when (icon) {
                        RemoteIcon.Up -> KeyEvent.KEYCODE_DPAD_UP; RemoteIcon.Down -> KeyEvent.KEYCODE_DPAD_DOWN
                        RemoteIcon.Left -> KeyEvent.KEYCODE_DPAD_LEFT; RemoteIcon.Right -> KeyEvent.KEYCODE_DPAD_RIGHT
                        else -> KeyEvent.KEYCODE_DPAD_CENTER
                    })
                })
                RemoteControl.VOLUME -> ControlRail("TV VOL", RemoteIcon.Plus, RemoteIcon.Minus, editing || tvReady,
                    { if (!editing) samsung.sendKey("KEY_VOLUP") }, { if (!editing) samsung.sendKey("KEY_VOLDOWN") })
                RemoteControl.HOME -> GlassPill("Shield Home", {
                    if (!editing) { if (player.connected) shield.sendKey(KeyEvent.KEYCODE_HOME) else onShieldUnavailable() }
                }, modifier = Modifier.fillMaxSize(), icon = RemoteIcon.Home)
                RemoteControl.PLAY_PAUSE -> RoundControl("Shield play or pause", RemoteIcon.PlayPause,
                    { if (!editing) shield.sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, enabled = editing || player.connected)
                RemoteControl.BACK -> GlassPill("Back", {
                    if (!editing) { if (player.connected) shield.sendKey(KeyEvent.KEYCODE_BACK) else onShieldUnavailable() }
                }, modifier = Modifier.fillMaxSize(), icon = RemoteIcon.Back)
                RemoteControl.MEDIA -> ControlRail("SHIELD", RemoteIcon.Rewind, RemoteIcon.Forward, editing || player.connected,
                    { if (!editing) shield.sendKey(KeyEvent.KEYCODE_MEDIA_REWIND) },
                    { if (!editing) shield.sendKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) })
                else -> {
                    val appName = favoriteName(control.label)
                    val accent = when (control) {
                        RemoteControl.SMARTTUBE -> Color(0xFFED565F)
                        RemoteControl.NUVIO -> Color(0xFF9B8AFB)
                        RemoteControl.PLAYFY -> Color(0xFFE3AB51)
                        else -> Color(0xFF56BFAE)
                    }
                    Surface(onClick = hapticClick { if (!editing) onFavorite(control.label) },
                        enabled = editing || favoriteAvailable(control.label),
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Launch $appName" },
                        shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, border = glassBorder()) {
                        Column(Modifier.fillMaxSize().glassSheen().padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(if (appName.startsWith("SmartTube", ignoreCase = true)) "▶" else appName.take(1),
                                fontSize = 17.sp, lineHeight = 22.sp, color = accent, fontWeight = FontWeight.Bold)
                            Text(appName, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (!player.connected) {
            TextButton(onClick = onShieldUnavailable,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) {
                Text(if (player.busy) "Connecting to Shield…" else "Shield disconnected · Tap to connect")
            }
        }
        if (tv.selectingInput) {
            Surface(Modifier.align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.surface) {
                Text(tv.message, Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
