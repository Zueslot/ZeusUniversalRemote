package com.zeus.remote.shield

import com.google.protobuf.ByteString
import com.zeus.remote.shield.proto.PoloProto.*
import java.security.interfaces.RSAPublicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

internal class ShieldPairingClient(private val credentials: ShieldCredentials) : AutoCloseable {
    private val transport = ShieldTransport()
    private fun envelope() = OuterMessage.newBuilder().setProtocolVersion(2).setStatus(OuterMessage.Status.STATUS_OK)
    private val encoding = Options.Encoding.newBuilder().setType(Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL).setSymbolLength(6).build()

    suspend fun start(host: String) {
        val tls = runInterruptible(Dispatchers.IO) { credentials.context(pairing = true) }
        transport.open(host, 6467, tls)
        transport.send(envelope().setPairingRequest(PairingRequest.newBuilder()
            .setServiceName("atvremote").setClientName("Zeus Universal Remote")).build())
        check(receive().hasPairingRequestAck()) { "Shield did not acknowledge pairing request" }
        transport.send(envelope().setOptions(Options.newBuilder().addInputEncodings(encoding)
            .setPreferredRole(Options.RoleType.ROLE_TYPE_INPUT)).build())
        check(receive().hasOptions()) { "Shield did not negotiate PIN encoding" }
        transport.send(envelope().setConfiguration(Configuration.newBuilder().setEncoding(encoding)
            .setClientRole(Options.RoleType.ROLE_TYPE_INPUT)).build())
        check(receive().hasConfigurationAck()) { "Shield did not display the pairing code" }
    }

    suspend fun finish(pin: String) {
        val peer = transport.peerCertificate()
        val secret = ShieldPairingSecret.calculate(
            transport.localCertificate().publicKey as RSAPublicKey, peer.publicKey as RSAPublicKey, pin)
        transport.send(envelope().setSecret(Secret.newBuilder().setSecret(ByteString.copyFrom(secret))).build())
        check(receive().hasSecretAck()) { "Shield did not confirm pairing" }
        runInterruptible(Dispatchers.IO) { credentials.savePeer(peer) }
    }

    private suspend fun receive(): OuterMessage {
        val message = transport.read(OuterMessage.parser())
        check(message.status == OuterMessage.Status.STATUS_OK) { "Shield rejected pairing (${message.status}). Restart pairing." }
        return message
    }

    override fun close() = transport.close()
}
