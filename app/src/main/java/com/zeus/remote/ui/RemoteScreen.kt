package com.zeus.remote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeus.remote.power.PowerController
import com.zeus.remote.power.WakeOnLan
import com.zeus.remote.data.DeviceRepository
import com.zeus.remote.data.singleLineDeviceAddress
import com.zeus.remote.diagnostics.ConnectivityDiagnostics
import com.zeus.remote.samsung.SamsungController
import com.zeus.remote.samsung.ui.SamsungControls
import com.zeus.remote.shield.apps.ShieldAppsController
import com.zeus.remote.shield.ui.ShieldAppsScreen
import com.zeus.remote.shield.ShieldController
import com.zeus.remote.shield.ui.ShieldControls
import kotlinx.coroutines.launch

@Composable
fun RemoteScreen(devices: DeviceRepository, onVolumeHandlerChanged: (((String) -> Boolean)?) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val samsungController = remember(devices, scope) { SamsungController(devices, scope) }
    val shieldController = remember(scope) { ShieldController(context, scope) }
    val appsController = remember(scope) { ShieldAppsController(context, scope) }
    val powerController = remember(scope, samsungController, shieldController) { PowerController(devices, samsungController, shieldController, scope) }
    val powerState by powerController.state.collectAsState()
    var wakeMac by remember { mutableStateOf(devices.samsungMac) }
    var hdmiOnWake by remember { mutableStateOf(devices.powerHdmiFallback) }
    val appsState by appsController.state.collectAsState()
    val appMessages = remember { SnackbarHostState() }
    val tv by samsungController.state.collectAsState()
    val player by shieldController.state.collectAsState()
    var samsung by remember { mutableStateOf(devices.samsungAddress) }
    var shield by remember { mutableStateOf(devices.shieldAddress) }
    val latestShield by rememberUpdatedState(shield)
    DisposableEffect(shieldController, context) {
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) shieldController.resumeConnection(latestShield)
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    var allowSelfSigned by remember { mutableStateOf(devices.trustSamsungCertificate) }
    var fastInput by remember { mutableStateOf(devices.fastHdmiInput) }
    var haptics by remember { mutableStateOf(devices.hapticFeedback) }
    var dark by remember { mutableStateOf(devices.darkTheme) }
    var page by rememberSaveable { mutableStateOf("Remote") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var editingLayout by remember { mutableStateOf(false) }
    fun reconnectDevices() {
        if (samsung.isNotBlank()) samsungController.connect(samsung, allowSelfSigned)
        shieldController.reconnectSaved(shield)
        appsController.refresh(shield)
    }
    LaunchedEffect(samsungController, shieldController) {
        if (samsung.isNotBlank()) {
            samsungController.connect(samsung, allowSelfSigned)
        }
        shieldController.reconnectSaved(shield)
    }
    SideEffect {
        onVolumeHandlerChanged { command ->
            page == "Remote" && !editingLayout && samsungController.sendHardwareVolume(command)
        }
    }
    DisposableEffect(Unit) { onDispose { onVolumeHandlerChanged(null) } }
    DisposableEffect(samsungController, shieldController) {
        onDispose { powerController.close(); samsungController.close(); shieldController.close(); appsController.close() }
    }
    DisposableEffect(shield) {
        onDispose { shieldController.disconnect() }
    }
    LaunchedEffect(shield) { appsController.refresh(shield) }
    LaunchedEffect(page) { if (page == "Apps") appsController.refresh(shield) }
    LaunchedEffect(appsState.message) {
        if (page == "Remote" && (appsState.message.startsWith("Launch") || appsState.message.startsWith("Open Apps") ||
                appsState.message.startsWith("On Shield"))) {
            appMessages.showSnackbar(appsState.message)
        }
    }
    LaunchedEffect(powerState.busy, powerState.message) {
        if (!powerState.busy && (powerState.message.contains("not confirmed") || powerState.message.startsWith("Power request failed"))) {
            appMessages.showSnackbar("Power wasn't confirmed. Check Settings → Power & Wake for details.")
        }
    }
    BackHandler(page != "Remote") { page = "Remote" }

    LaunchedEffect(page) { if (page == "Settings") wakeMac = devices.samsungMac }
    RemoteTheme(dark, haptics) {
        Scaffold(modifier = Modifier.glassBackdrop(), containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(appMessages) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (page != "Remote") {
                    Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                        RemoteBottomBar(page, enabled = !editingLayout) { page = it }
                    }
                }
            }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                if (page == "Remote") {
                    RemotePanel(samsungController, tv, shieldController, player, devices, { editingLayout = it },
                        onReconnectDevices = ::reconnectDevices,
                        onShieldUnavailable = {
                            if (shield.isBlank() || player.pairingRequired || (!player.busy && !player.connected)) page = "Settings"
                            else if (!player.busy) shieldController.reconnectSaved(shield)
                        },
                        onPower = powerController::toggle, powerBusy = powerState.busy,
                        onOpenApps = { page = "Apps" }, onOpenSettings = { page = "Settings" },
                        onToggleTheme = { dark = !dark; devices.darkTheme = dark },
                        onFavorite = { name -> appsController.launchFavorite(name, shield) },
                        favoriteName = appsState::favoriteName,
                        favoriteAvailable = { !appsState.busy },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
                } else {
                    Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("ZEUS", fontSize = 20.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Bold)
                                Text(page.uppercase(), fontSize = 9.sp, letterSpacing = 1.7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RoundControl("Switch light or dark theme", RemoteIcon.Theme,
                                { dark = !dark; devices.darkTheme = dark }, diameter = 48.dp)
                        }
                        when (page) {
                            "Apps" -> ShieldAppsScreen(shield, appsController)
                            "Settings" -> {
                                SettingsCard("DEVICES") {
                                    DeviceSettings(
                                        samsung = samsung, onSamsungChange = { samsung = singleLineDeviceAddress(it); devices.samsungAddress = samsung },
                                        shield = shield, onShieldChange = { shield = singleLineDeviceAddress(it); devices.shieldAddress = shield },
                                        trustSelfSigned = allowSelfSigned,
                                        onTrustChange = { allowSelfSigned = it; devices.trustSamsungCertificate = it },
                                        samsungConnected = tv.connected,
                                        onConnectSamsung = { samsungController.connect(samsung, allowSelfSigned) },
                                        onForgetSamsung = { samsungController.forget() },
                                        onTestConnectivity = {
                                            scope.launch {
                                                samsungController.showStatus("Testing TCP connectivity...")
                                                samsungController.showStatus(ConnectivityDiagnostics.test(samsung, shield))
                                            }
                                        })
                                    Text(tv.message, style = MaterialTheme.typography.bodySmall)
                                }
                                SettingsCard("SHIELD PAIRING") { ShieldControls(shield, shieldController) }
                                SettingsCard("POWER & WAKE") {
                                    Text(powerState.message, style = MaterialTheme.typography.bodySmall)
                                    OutlinedTextField(wakeMac, { wakeMac = it.trim(); devices.samsungMac = wakeMac },
                                        label = { Text("Samsung Wi-Fi MAC address") }, singleLine = true,
                                        isError = wakeMac.isNotBlank() && WakeOnLan.normalizeMac(wakeMac) == null,
                                        supportingText = { Text("Used to wake the TV from standby. Format: AA:BB:CC:DD:EE:FF") },
                                        modifier = Modifier.fillMaxWidth())
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            val found = samsungController.learnWakeAddress()
                                            wakeMac = devices.samsungMac
                                            if (found == null) samsungController.showStatus("TV did not report a wake MAC. Enter its Wi-Fi MAC from TV network settings.")
                                        }
                                    }, enabled = tv.connected) { Text("Read wake address from TV") }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("HDMI 1 menu fallback after wake")
                                            Text("Use if CEC does not select Shield. Opens the TV Source menu.", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Switch(hdmiOnWake, { hdmiOnWake = it; devices.powerHdmiFallback = it })
                                    }
                                    Text("Enable Samsung Anynet+ / Power On with Mobile, and Shield Settings > Display & Sound > Power Control > CEC TV on/off. Wake and standby behavior needs device testing.",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                SettingsCard("PREFERENCES") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Haptic feedback", Modifier.weight(1f))
                                        Switch(haptics, { haptics = it; devices.hapticFeedback = it })
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Dark appearance", Modifier.weight(1f))
                                        Switch(dark, { dark = it; devices.darkTheme = it })
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Faster HDMI selection")
                                            Text("About 7 seconds. Off uses 32 seconds.", style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(fastInput, { fastInput = it; devices.fastHdmiInput = it }, enabled = !tv.selectingInput)
                                    }
                                    Text("Press Power once to turn both devices on or put them in standby. Enable CEC and TV wake settings above. Keyboard and double-tap Home are still pending.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide TV fallback controls" else "Show TV fallback controls") }
                                if (advanced) SettingsCard("SAMSUNG FALLBACK") {
                                    SamsungControls(samsungController, tv, fastInput) { fastInput = it; devices.fastHdmiInput = it }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = glassBorder()) {
        Column(Modifier.fillMaxWidth().glassSheen().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
