package com.zeus.remote.shield

import com.zeus.remote.shield.proto.PoloProto.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.math.BigInteger
import java.security.interfaces.RSAPublicKey

class ShieldProtocolTest {
    private fun key(modulus: String) = object : RSAPublicKey {
        override fun getModulus() = BigInteger(modulus, 16)
        override fun getPublicExponent() = BigInteger("65537")
        override fun getAlgorithm() = "RSA"
        override fun getFormat() = "X.509"
        override fun getEncoded() = byteArrayOf()
    }

    @Test fun pinProofMatchesIndependentSha256VectorAndStripsSignPadding() {
        // SHA-256 over 80 01 | 01 00 01 | ff 02 | 01 00 01 | a1 b2.
        val result = ShieldPairingSecret.calculate(key("8001"), key("ff02"), "D4A1B2")
        assertEquals("d49ec8f65c55f3198566546b5733662e31530bd1b271517b11f66f28531ab1a2",
            result.joinToString("") { "%02x".format(it) })
    }

    @Test fun incorrectPinChecksumAndMalformedPinsAreRejected() {
        listOf("00A1B2", "D4A1B", "D4A1BZ", " D4A1B2").forEach { code ->
            assertThrows(IllegalArgumentException::class.java) {
                ShieldPairingSecret.calculate(key("8001"), key("ff02"), code)
            }
        }
    }

    @Test fun readsFragmentedFramesWithoutConsumingNextMessage() {
        val message = OuterMessage.newBuilder().setProtocolVersion(2).setStatus(OuterMessage.Status.STATUS_OK)
            .setPairingRequest(PairingRequest.newBuilder().setServiceName("atvremote").setClientName("Z".repeat(300))).build()
        val bytes = ByteArrayOutputStream().also { ShieldFrames.write(it, message); ShieldFrames.write(it, message) }.toByteArray()
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(target: ByteArray, off: Int, len: Int): Int = super.read(target, off, minOf(len, 1))
        }
        assertEquals(message, ShieldFrames.read(input, OuterMessage.parser()))
        assertEquals(message, ShieldFrames.read(input, OuterMessage.parser()))
        assertEquals(-1, input.read())
    }

    @Test fun oversizedAndMalformedLengthsAreRejectedBeforeAllocation() {
        listOf(byteArrayOf(0), byteArrayOf(0x81.toByte(), 0x80.toByte(), 0x40),
            ByteArray(5) { 0xff.toByte() }).forEach { prefix ->
            assertThrows(Exception::class.java) { ShieldFrames.read(ByteArrayInputStream(prefix), OuterMessage.parser()) }
        }
    }

    @Test fun truncatedFrameAndPrefixReportClosedConnection() {
        listOf(byteArrayOf(0x80.toByte()), byteArrayOf(5, 8, 2)).forEach { bytes ->
            assertThrows(EOFException::class.java) { ShieldFrames.read(ByteArrayInputStream(bytes), OuterMessage.parser()) }
        }
    }

    @Test fun privateAddressValidationChecksEveryOctet() {
        listOf("10.0.0.1", "192.168.1.2", "172.16.0.1", "172.31.255.254").forEach { assertTrue(privateIpv4(it)) }
        listOf("10.0.0.999", "172.32.0.1", "127.0.0.1", "8.8.8.8", "10.0.1", "10..0.1", "host.local").forEach { assertFalse(privateIpv4(it)) }
    }
}
