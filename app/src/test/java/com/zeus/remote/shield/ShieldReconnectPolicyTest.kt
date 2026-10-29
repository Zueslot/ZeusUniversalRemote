package com.zeus.remote.shield

import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.*
import org.junit.Test

class ShieldReconnectPolicyTest {
    @Test fun longOutagesKeepRetryDelaysBoundedWithoutAnAttemptLimit() {
        assertEquals(1000L, ShieldReconnectPolicy.delayMillis(1))
        assertEquals(2000L, ShieldReconnectPolicy.delayMillis(2))
        for (attempt in listOf(5, 10, 100, Int.MAX_VALUE)) {
            assertTrue(ShieldReconnectPolicy.delayMillis(attempt) in 1000L..30000L)
        }
    }
    @Test fun transientTlsAndNetworkFailuresDoNotRequireNewPairing() {
        assertFalse(ShieldReconnectPolicy.requiresPairing(SocketTimeoutException()))
        assertFalse(ShieldReconnectPolicy.requiresPairing(SSLException("Connection reset")))
        val changedCertificate = SSLHandshakeException("Handshake failed").apply { initCause(CertificateException("Pin changed")) }
        assertTrue(ShieldReconnectPolicy.requiresPairing(changedCertificate))
    }
}
