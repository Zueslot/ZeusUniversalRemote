package com.zeus.remote.shield

import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/** Varint-delimited protobuf framing, bounded before allocating network-controlled lengths. */
internal object ShieldFrames {
    const val MAX_FRAME = 1024 * 1024

    fun <T : MessageLite> read(input: InputStream, parser: Parser<T>): T {
        var size = 0L
        for (shift in 0..28 step 7) {
            val next = input.read()
            if (next < 0) throw EOFException("Shield closed the connection")
            size = size or ((next and 127).toLong() shl shift)
            if (next and 128 == 0) {
                require(size in 1..MAX_FRAME.toLong()) { "Invalid Shield message length" }
                val bytes = ByteArray(size.toInt())
                var offset = 0
                while (offset < bytes.size) {
                    val count = input.read(bytes, offset, bytes.size - offset)
                    if (count < 0) throw EOFException("Incomplete Shield message")
                    if (count == 0) continue
                    offset += count
                }
                return parser.parseFrom(bytes)
            }
        }
        error("Invalid Shield length prefix")
    }

    fun write(output: OutputStream, message: MessageLite) {
        val bytes = message.toByteArray()
        require(bytes.size in 1..MAX_FRAME)
        var size = bytes.size
        while (size > 127) {
            output.write((size and 127) or 128)
            size = size ushr 7
        }
        output.write(size)
        output.write(bytes)
        output.flush()
    }
}

internal object ShieldPairingSecret {
    private fun unsigned(value: BigInteger): ByteArray = value.toByteArray().let {
        if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
    }

    fun calculate(client: RSAPublicKey, server: RSAPublicKey, pin: String): ByteArray {
        require(Regex("[0-9A-Fa-f]{6}").matches(pin)) { "Enter the six hexadecimal characters shown by Shield." }
        val hash = MessageDigest.getInstance("SHA-256")
        hash.update(unsigned(client.modulus))
        hash.update(unsigned(client.publicExponent))
        hash.update(unsigned(server.modulus))
        hash.update(unsigned(server.publicExponent))
        hash.update(byteArrayOf(pin.substring(2, 4).toInt(16).toByte(), pin.substring(4, 6).toInt(16).toByte()))
        val secret = hash.digest()
        require((secret[0].toInt() and 255) == pin.substring(0, 2).toInt(16)) {
            "The code does not match this Shield connection. Check the code or restart pairing."
        }
        return secret
    }
}

internal fun privateIpv4(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4 || parts.any { it.isEmpty() || it.any { c -> c !in '0'..'9' } }) return false
    val numbers = parts.map { it.toIntOrNull() ?: return false }
    if (numbers.any { it !in 0..255 }) return false
    return numbers[0] == 10 || (numbers[0] == 192 && numbers[1] == 168) ||
        (numbers[0] == 172 && numbers[1] in 16..31)
}
