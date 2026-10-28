package com.zeus.companion

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CompanionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by CompanionStatus.state.collectAsState()
            val scope = rememberCoroutineScope()
            var note by remember { mutableStateOf("") }
            var rotating by remember { mutableStateOf(false) }
            var bitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(state.qr) {
                bitmap = if (state.qr.isBlank()) null else withContext(Dispatchers.Default) {
                    val matrix = QRCodeWriter().encode(state.qr, BarcodeFormat.QR_CODE, 600, 600)
                    Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).apply {
                        setPixels(IntArray(600 * 600) { index -> if (matrix[index % 600, index / 600]) android.graphics.Color.BLACK else android.graphics.Color.WHITE },
                            0, 600, 0, 0, 600, 600)
                    }
                }
            }
            MaterialTheme(colorScheme = darkColorScheme()) {
                Row(Modifier.fillMaxSize().background(Color(0xFF101820)).padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("ZEUS SHIELD COMPANION", style = MaterialTheme.typography.headlineSmall)
                        Text(state.message)
                        Text("1. Start here.  2. Open Apps on your phone.  3. Scan this QR code.")
                        Text("To launch apps while this screen is closed, enable Display over other apps for Zeus. No overlay is drawn.")
                        Button(onClick = { startForegroundService(Intent(this@CompanionActivity, CompanionService::class.java)) },
                            enabled = !state.running && !rotating) { Text("Start companion") }
                        Button(onClick = {
                            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                            catch (_: Exception) { note = "Open Shield Settings > Apps > Special app access > Display over other apps > Zeus Shield Companion." }
                        }) { Text("Enable app launching") }
                        OutlinedButton(onClick = { stopService(Intent(this@CompanionActivity, CompanionService::class.java)) }, enabled = state.running) { Text("Stop companion") }
                        OutlinedButton(onClick = {
                            rotating = true
                            stopService(Intent(this@CompanionActivity, CompanionService::class.java))
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { CompanionIdentity(applicationContext).rotateToken() }
                                    note = "Old phone access revoked. Start again and scan the new QR code."
                                } catch (e: Exception) { note = "Could not reset access: ${e.message}" }
                                finally { rotating = false }
                            }
                        }, enabled = !rotating) { Text("Reset phone access") }
                        if (note.isNotBlank()) Text(note)
                    }
                    bitmap?.let { Image(it.asImageBitmap(), "Scan to grant this phone app-list and launch access", Modifier.size(270.dp)) }
                }
            }
        }
    }
}
