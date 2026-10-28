package com.zeus.remote.shield

import com.zeus.remote.shield.proto.RemoteProto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

internal class ShieldRemoteClient(private val credentials: ShieldCredentials) : AutoCloseable {
    private val transport = ShieldTransport()
    private var features = 35 // Ping, key injection and power-status/start notifications.
    @Volatile private var ready = false

    suspend fun run(host: String, onReady: () -> Unit) {
        val tls = runInterruptible(Dispatchers.IO) { credentials.context(pairing = false) }
        transport.open(host, 6466, tls)
        var configured = false
        var active = false
        while (true) {
            val message = transport.read(RemoteMessage.parser())
            when {
                message.hasRemoteConfigure() -> {
                    ready = false
                    active = false
                    features = 35 and message.remoteConfigure.code1
                    check(features and 2 != 0) { "Shield does not offer remote key control" }
                    transport.send(RemoteMessage.newBuilder().setRemoteConfigure(RemoteConfigure.newBuilder()
                        .setCode1(features).setDeviceInfo(RemoteDeviceInfo.newBuilder()
                            .setModel("Zeus Remote").setVendor("Zeus").setUnknown1(1).setUnknown2("1")
                            .setPackageName("com.zeus.remote").setAppVersion("0.2.0"))).build())
                    configured = true
                }
                message.hasRemoteSetActive() -> {
                    check(configured) { "Shield activation arrived before configuration" }
                    transport.send(RemoteMessage.newBuilder().setRemoteSetActive(
                        RemoteSetActive.newBuilder().setActive(features)).build())
                    active = true
                }
                message.hasRemoteStart() -> {
                    check(configured && active) { "Shield session negotiation is incomplete" }
                    ready = true
                    transport.controlTimeout()
                    onReady()
                }
                message.hasRemotePingRequest() -> transport.send(RemoteMessage.newBuilder()
                    .setRemotePingResponse(RemotePingResponse.newBuilder().setVal1(message.remotePingRequest.val1)).build())
                message.hasRemoteError() -> error("Shield reported a remote protocol error")
            }
        }
    }

    suspend fun sendKey(code: Int) {
        check(ready) { "Shield is not authenticated and ready" }
        transport.send(RemoteMessage.newBuilder().setRemoteKeyInject(RemoteKeyInject.newBuilder()
            .setKeyCodeValue(code).setDirection(RemoteDirection.SHORT)).build())
    }

    override fun close() { ready = false; transport.close() }
}
