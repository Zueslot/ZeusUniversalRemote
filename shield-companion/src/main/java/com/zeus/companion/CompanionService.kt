package com.zeus.companion

import android.app.*
import android.content.Intent
import android.os.IBinder
import com.zeus.shared.CompanionLink
import com.zeus.shared.isPrivateIpv4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.NetworkInterface

internal data class CompanionUiState(val message: String = "Start the companion to share this Shield's apps.", val qr: String = "", val running: Boolean = false)
internal object CompanionStatus {
    val mutable = MutableStateFlow(CompanionUiState())
    val state = mutable.asStateFlow()
}

class CompanionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var server: CompanionServer? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("zeus_companion", "Zeus phone connection", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, CompanionActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "zeus_companion").setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .setContentTitle("Zeus companion is running").setContentText("Open to stop access or replace the pairing code.")
            .setContentIntent(open).build())
        scope.launch {
            try {
                val host = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }.mapNotNull { it.hostAddress }.firstOrNull(::isPrivateIpv4)
                    ?: error("Connect Shield to a private home network first.")
                val identity = CompanionIdentity(applicationContext)
                val link = CompanionLink(host, identity.fingerprint, identity.token())
                val active = CompanionServer(applicationContext, host, identity)
                server = active
                try {
                    ensureActive()
                    CompanionStatus.mutable.value = CompanionUiState("Ready at $host. Scan this code in Zeus on your phone.", link.encode(), true)
                    active.serve()
                } finally { active.close() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                CompanionStatus.mutable.value = CompanionUiState("Companion failed: ${e.message}")
                stopSelf()
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        server?.close()
        scope.cancel()
        if (CompanionStatus.state.value.running) {
            CompanionStatus.mutable.value = CompanionUiState("Companion stopped. Phone app access is unavailable until restarted.")
        }
        super.onDestroy()
    }
}
