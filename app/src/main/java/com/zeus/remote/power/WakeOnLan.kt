package com.zeus.remote.power

import com.zeus.shared.isPrivateIpv4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object WakeOnLan {
    fun normalizeMac(value: String): String? {
        val hex = value.trim().replace(":", "").replace("-", "")
        if (!Regex("[0-9A-Fa-f]{12}").matches(hex) || hex.all { it == '0' } || hex.all { it.lowercaseChar() == 'f' }) return null
        return hex.chunked(2).joinToString(":").uppercase()
    }

    fun packet(value: String): ByteArray {
        val mac = requireNotNull(normalizeMac(value)) { "Enter the TV's Wi-Fi MAC address in Power settings." }
            .split(':').map { it.toInt(16).toByte() }.toByteArray()
        return ByteArray(102) { index -> if (index < 6) 0xff.toByte() else mac[(index - 6) % 6] }
    }

    suspend fun send(mac: String, tvAddress: String) = withContext(Dispatchers.IO) {
        require(isPrivateIpv4(tvAddress)) { "Enter the TV's private IPv4 address first." }
        val bytes = packet(mac)
        val target = InetAddress.getByName(tvAddress).address
        val broadcasts = mutableSetOf(InetAddress.getByName("255.255.255.255"))
        broadcasts.add(InetAddress.getByName(tvAddress))
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.forEach { network ->
            network.interfaceAddresses.forEach { address ->
                if (address.address is Inet4Address && address.broadcast != null) {
                    val own = address.address.address
                    val prefix = address.networkPrefixLength.toInt().coerceIn(0, 32)
                    val sameSubnet = (0 until prefix).all { bit ->
                        val mask = 1 shl (7 - bit % 8)
                        (own[bit / 8].toInt() and mask) == (target[bit / 8].toInt() and mask)
                    }
                    if (sameSubnet) broadcasts.add(address.broadcast)
                }
            }
        }
        DatagramSocket().use { socket ->
            socket.broadcast = true
            repeat(3) {
                broadcasts.forEach { host ->
                    for (port in listOf(7, 9)) socket.send(DatagramPacket(bytes, bytes.size, host, port))
                }
                delay(350)
            }
        }
    }
}
