package com.zeus.remote.shield

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.*
import javax.security.auth.x500.X500Principal

/** Per-device identity: private key never leaves Android Keystore; peer pin is not backed up. */
internal class ShieldCredentials(context: Context, private val host: String) {
    private val deviceId = MessageDigest.getInstance("SHA-256").digest(host.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val alias = "zeus_shield_$deviceId"
    private val pinFile = AtomicFile(File(context.noBackupFilesDir, "$alias.peer"))
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun isPaired(): Boolean = store.containsAlias(alias) && pinFile.baseFile.exists()

    private fun identity(): KeyStore.PrivateKeyEntry {
        if (!store.containsAlias(alias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .setCertificateSubject(X500Principal("CN=Zeus Universal Remote"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(Date(System.currentTimeMillis() - 86400000L))
                    .setCertificateNotAfter(Date(System.currentTimeMillis() + 10L * 365 * 86400000))
                    .build())
                generateKeyPair()
            }
        }
        return store.getEntry(alias, null) as KeyStore.PrivateKeyEntry
    }

    fun context(pairing: Boolean): SSLContext {
        val entry = identity()
        val pinned = if (pairing) null else pinFile.readFully()
        val manager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = arrayOf(alias)
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String = alias
            override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String = alias
            override fun getCertificateChain(requested: String?): Array<X509Certificate> = entry.certificateChain.map { it as X509Certificate }.toTypedArray()
            override fun getPrivateKey(requested: String?): PrivateKey = entry.privateKey
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        }
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException("Client mode only")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val cert = chain?.firstOrNull() ?: throw CertificateException("Shield sent no certificate")
                if (pinned != null && !MessageDigest.isEqual(pinned, fingerprint(cert))) {
                    throw CertificateException("Shield certificate changed. Forget pairing and pair again.")
                }
                // Initial pairing authenticates this exact peer through the on-screen PIN hash.
                if (cert.publicKey.algorithm != "RSA") throw CertificateException("Shield requires an RSA certificate")
            }
        }
        return SSLContext.getInstance("TLSv1.2").apply { init(arrayOf(manager), arrayOf(trust), null) }
    }

    fun savePeer(cert: X509Certificate) {
        val output = pinFile.startWrite()
        try { output.write(fingerprint(cert)); pinFile.finishWrite(output) }
        catch (e: Exception) { pinFile.failWrite(output); throw e }
    }

    fun forget() {
        pinFile.delete()
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private fun fingerprint(cert: X509Certificate) = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
}
