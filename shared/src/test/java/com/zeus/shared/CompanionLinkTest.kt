package com.zeus.shared

import org.junit.Assert.*
import org.junit.Test

class CompanionLinkTest {
    private val pin = "ab".repeat(32)
    private val token = "A".repeat(43)

    @Test fun qrRoundTripPreservesTrustMaterialWithoutLeakingItInToString() {
        val link = CompanionLink("10.0.0.2", pin, token)
        assertEquals(link, CompanionLink.parse(link.encode()))
        assertFalse(link.toString().contains(token))
        assertFalse(link.toString().contains(pin))
    }
    @Test fun rejectsExternalHostsAndInjectedEndpoints() {
        listOf("8.8.8.8", "127.0.0.1", "10.0.0.999", "10.0.0.2/path", "localhost", "10.0.0.2:443").forEach { host ->
            assertThrows(IllegalArgumentException::class.java) { CompanionLink(host, pin, token) }
        }
    }
    @Test fun rejectsBadPinsTokensVersionsAndOversizedCodes() {
        assertThrows(IllegalArgumentException::class.java) { CompanionLink("10.0.0.2", "ab", token) }
        assertThrows(IllegalArgumentException::class.java) { CompanionLink("10.0.0.2", pin, "short") }
        assertThrows(IllegalArgumentException::class.java) { CompanionLink.parse("x".repeat(3000)) }
        assertThrows(IllegalArgumentException::class.java) { CompanionLink.parse(CompanionLink("10.0.0.2", pin, token).encode().replace("\"v\":1", "\"v\":2")) }
    }
    @Test fun launchIdentifiersCannotContainComponentsOrShellCommands() {
        assertTrue(validPackageName("com.example.tv_app"))
        listOf("com.app/.Activity", "com.app;am start", "https://example.com", "", "com..app", "com.example app").forEach { assertFalse(validPackageName(it)) }
    }
}
