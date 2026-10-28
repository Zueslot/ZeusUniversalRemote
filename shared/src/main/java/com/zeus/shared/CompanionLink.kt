package com.zeus.shared

import org.json.JSONObject

const val COMPANION_PORT = 8765
private const val QR_PREFIX = "zeus-companion:"

data class CompanionLink(val host: String, val fingerprint: String, val token: String) {
    init {
        require(isPrivateIpv4(host)) { "Companion must use a private IPv4 address." }
        require(Regex("[0-9a-f]{64}").matches(fingerprint)) { "Invalid companion certificate fingerprint." }
        require(Regex("[A-Za-z0-9_-]{43}").matches(token)) { "Invalid companion access token." }
    }
    fun encode(): String = QR_PREFIX + JSONObject().put("v", 1).put("host", host)
        .put("fingerprint", fingerprint).put("token", token).toString()
    override fun toString() = "CompanionLink(host=$host, credentials=redacted)"

    companion object {
        fun parse(code: String): CompanionLink {
            require(code.length <= 2048 && code.startsWith(QR_PREFIX)) { "Scan the QR code in Zeus Shield Companion." }
            val json = JSONObject(code.removePrefix(QR_PREFIX))
            require(json.getInt("v") == 1) { "Unsupported companion version." }
            return CompanionLink(json.getString("host"), json.getString("fingerprint"), json.getString("token"))
        }
    }
}

fun isPrivateIpv4(host: String): Boolean {
    val fields = host.split('.')
    if (fields.size != 4 || fields.any { it.isEmpty() || it.length > 3 || it.any { c -> c !in '0'..'9' } }) return false
    val parts = fields.map { it.toIntOrNull() ?: return false }
    if (parts.any { it !in 0..255 }) return false
    return parts[0] == 10 || (parts[0] == 192 && parts[1] == 168) || (parts[0] == 172 && parts[1] in 16..31)
}

data class ShieldApp(val name: String, val packageName: String)

fun validPackageName(value: String) = value.length in 3..255 &&
    Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(value)
