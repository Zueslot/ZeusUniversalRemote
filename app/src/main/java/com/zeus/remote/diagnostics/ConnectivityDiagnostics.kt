package com.zeus.remote.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object ConnectivityDiagnostics {
    suspend fun test(samsungAddress: String, shieldAddress: String): String = withContext(Dispatchers.IO) {
        listOf(Triple("Samsung remote", samsungAddress, 8002), Triple("Shield pairing", shieldAddress, 6467),
            Triple("Shield control", shieldAddress, 6466)).joinToString("\n") { (label, ip, port) ->
            val result = if (ip.isBlank()) "IP missing" else try {
                Socket().use { it.connect(InetSocketAddress(ip, port), 2500) }
                "Reachable"
            } catch (e: Exception) { "Unavailable (${e.javaClass.simpleName})" }
            "$label ($port): $result"
        }
    }
}
