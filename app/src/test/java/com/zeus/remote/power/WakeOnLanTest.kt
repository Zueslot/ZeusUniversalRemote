package com.zeus.remote.power

import org.junit.Assert.*
import org.junit.Test

class WakeOnLanTest {
    @Test fun magicPacketContainsSixSyncBytesAndSixteenExactMacCopies() {
        val bytes = WakeOnLan.packet("AA:BB:CC:DD:EE:12")
        assertEquals(102, bytes.size)
        assertArrayEquals(ByteArray(6) { 0xff.toByte() }, bytes.copyOfRange(0, 6))
        val expected = byteArrayOf(0xaa.toByte(), 0xbb.toByte(), 0xcc.toByte(), 0xdd.toByte(), 0xee.toByte(), 0x12)
        repeat(16) { block -> assertArrayEquals(expected, bytes.copyOfRange(6 + block * 6, 12 + block * 6)) }
    }
    @Test fun acceptsCommonMacFormatsAndRejectsMissingInvalidAndBroadcastAddresses() {
        assertEquals("AA:BB:CC:DD:EE:12", WakeOnLan.normalizeMac(" aa-bb-cc-dd-ee-12 "))
        assertEquals("AA:BB:CC:DD:EE:12", WakeOnLan.normalizeMac("aabbccddee12"))
        listOf("", "00:00:00:00:00:00", "FF:FF:FF:FF:FF:FF", "not-a-mac", "AA:BB:CC:DD:EE", "10.0.0.130").forEach {
            assertNull(WakeOnLan.normalizeMac(it))
            assertThrows(IllegalArgumentException::class.java) { WakeOnLan.packet(it) }
        }
    }
}
