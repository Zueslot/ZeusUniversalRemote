package com.zeus.remote.shield

import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

internal object ShieldReconnectPolicy {
    fun delayMillis(attempt: Int): Long = (1000L shl (attempt - 1).coerceIn(0, 5)).coerceAtMost(30000L)

    fun requiresPairing(error: Throwable): Boolean = generateSequence(error) { it.cause }.any {
        it is CertificateException || it is SSLPeerUnverifiedException
    }
}
