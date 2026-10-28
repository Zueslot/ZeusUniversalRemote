package com.zeus.remote.shield

import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.net.InetSocketAddress
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

internal class ShieldTransport : AutoCloseable {
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var closed = false
    private val writeLock = Any()

    suspend fun open(host: String, port: Int, context: SSLContext) = runInterruptible(Dispatchers.IO) {
        check(!closed)
        val candidate = context.socketFactory.createSocket() as SSLSocket
        socket = candidate
        try {
            check(!closed)
            candidate.enabledProtocols = arrayOf("TLSv1.2")
            candidate.useClientMode = true
            candidate.tcpNoDelay = true
            candidate.soTimeout = 15000
            candidate.connect(InetSocketAddress(host, port), 5000)
            candidate.startHandshake()
        } catch (e: Exception) { close(); throw e }
    }

    fun localCertificate() = socket!!.session.localCertificates.first() as java.security.cert.X509Certificate
    fun peerCertificate() = socket!!.session.peerCertificates.first() as java.security.cert.X509Certificate
    fun controlTimeout() { socket!!.soTimeout = 45000 }

    suspend fun <T : MessageLite> read(parser: Parser<T>): T = runInterruptible(Dispatchers.IO) {
        ShieldFrames.read(checkNotNull(socket).inputStream, parser)
    }

    suspend fun send(message: MessageLite) = runInterruptible(Dispatchers.IO) {
        synchronized(writeLock) { ShieldFrames.write(checkNotNull(socket).outputStream, message) }
    }

    override fun close() {
        closed = true
        runCatching { socket?.close() }
    }
}
