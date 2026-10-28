package com.zeus.companion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.zeus.shared.EncryptedFile
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.security.auth.x500.X500Principal

internal class CompanionIdentity(context: Context) {
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "zeus_companion_tls_v1"
    private val tokenStore = EncryptedFile(context, "companion-token")

    init {
        if (!store.containsAlias(alias)) {
            KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .setCertificateSubject(X500Principal("CN=Zeus Shield Companion"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(Date(System.currentTimeMillis() - 86400000))
                    .setCertificateNotAfter(Date(System.currentTimeMillis() + 10L * 365 * 86400000)).build())
                generateKeyPair()
            }
        }
    }

    val fingerprint: String get() = MessageDigest.getInstance("SHA-256").digest(store.getCertificate(alias).encoded)
        .joinToString("") { "%02x".format(it) }
    fun token(): String = tokenStore.read() ?: rotateToken()
    fun rotateToken(): String = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) },
        Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE).also { tokenStore.write(it) }

    fun tls(): SSLContext {
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, null) }
        return SSLContext.getInstance("TLSv1.2").apply { init(managers.keyManagers, null, null) }
    }
}
