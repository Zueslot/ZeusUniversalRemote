package com.zeus.companion

import android.content.Context
import com.zeus.shared.COMPANION_PORT
import com.zeus.shared.isPrivateIpv4
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

internal class CompanionServer(context: Context, host: String, identity: CompanionIdentity) : AutoCloseable {
    private val token = identity.token()
    private val catalog = AppCatalog(context)
    private val server = (identity.tls().serverSocketFactory.createServerSocket(COMPANION_PORT, 8, InetAddress.getByName(host)) as SSLServerSocket)
        .apply { enabledProtocols = arrayOf("TLSv1.2"); soTimeout = 1000 }
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue(4))
    private val sockets = java.util.concurrent.ConcurrentHashMap.newKeySet<SSLSocket>()

    suspend fun serve() {
        while (currentCoroutineContext().isActive && !server.isClosed) {
            val socket = try { server.accept() as SSLSocket } catch (_: SocketTimeoutException) { continue }
            if (!isPrivateIpv4(socket.inetAddress.hostAddress.orEmpty())) { socket.close(); continue }
            sockets.add(socket)
            try {
                workers.execute {
                    socket.use {
                        try {
                            socket.soTimeout = 5000
                            socket.startHandshake()
                            val request = CompanionHttp.read(socket.inputStream)
                            if (!MessageDigest.isEqual(request.authorization.toByteArray(), "Bearer $token".toByteArray())) {
                                reply(socket, 401, JSONObject().put("error", "Scan the companion QR code again."))
                            } else when {
                                request.method == "GET" && request.path == "/apps" -> reply(socket, 200, catalog.json())
                                request.method == "POST" && request.path == "/launch" -> {
                                    catalog.launch(JSONObject(request.body.toString(Charsets.UTF_8)).getString("package"))
                                    reply(socket, 200, JSONObject().put("accepted", true))
                                }
                                else -> reply(socket, 404, JSONObject().put("error", "Unknown request."))
                            }
                        } catch (e: Exception) {
                            runCatching { reply(socket, 400, JSONObject().put("error", e.message?.take(180) ?: "Request failed.")) }
                        } finally { sockets.remove(socket) }
                    }
                }
            } catch (_: RejectedExecutionException) { sockets.remove(socket); socket.close() }
        }
    }

    private fun reply(socket: SSLSocket, status: Int, body: JSONObject) {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.outputStream.apply { write(header.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
    }

    override fun close() {
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }
}
