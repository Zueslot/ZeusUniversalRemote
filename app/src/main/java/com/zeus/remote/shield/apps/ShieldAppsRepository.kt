package com.zeus.remote.shield.apps

import android.content.Context
import com.zeus.shared.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Proxy
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

internal data class ShieldCatalog(val apps: List<ShieldApp>, val launchAllowed: Boolean)

internal class ShieldAppsRepository(context: Context) {
    private val credentials = EncryptedFile(context, "shield-apps-link")
    private val preferences = context.getSharedPreferences("shield_app_favorites", Context.MODE_PRIVATE)
    fun savedLink(): CompanionLink? = credentials.read()?.let(CompanionLink::parse)
    fun save(link: CompanionLink) = credentials.write(link.encode())
    fun forget() = credentials.delete()
    fun assigned(host: String, label: String): String? = preferences.getString("$host|$label", null)
    fun assign(host: String, label: String, packageName: String, name: String) {
        preferences.edit().putString("$host|$label", packageName).putString("$host|$label|name", name).apply()
    }
    fun favoriteNames(host: String): Map<String, String> = com.zeus.remote.data.FavoriteAppNames.mapNotNull { slot ->
        preferences.getString("$host|$slot|name", null)?.takeIf { it.isNotBlank() }?.let { slot to it }
    }.toMap()

    private fun client(link: CompanionLink): OkHttpClient {
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException("Client mode only")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val cert = chain?.firstOrNull() ?: throw CertificateException("Missing companion certificate")
                val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
                if (!MessageDigest.isEqual(actual.toByteArray(), link.fingerprint.toByteArray())) {
                    throw CertificateException("Companion certificate changed. Scan the TV's new QR code.")
                }
            }
        }
        val tls = SSLContext.getInstance("TLSv1.2").apply { init(null, arrayOf(trust), null) }
        return OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trust)
            .hostnameVerifier { hostname, _ -> hostname == link.host } // Exact QR fingerprint verified by trust manager.
            .proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build()
    }

    fun fetch(link: CompanionLink): ShieldCatalog {
        val data = request(link, "/apps")
        val list = data.getJSONArray("apps")
        require(list.length() <= 1000) { "Companion returned too many apps." }
        val apps = (0 until list.length()).map { index ->
            val app = list.getJSONObject(index)
            val pkg = app.getString("package")
            require(validPackageName(pkg)) { "Companion returned an invalid package." }
            ShieldApp(app.getString("name").take(120), pkg)
        }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
        return ShieldCatalog(apps, data.optBoolean("launchAllowed"))
    }

    fun launch(link: CompanionLink, packageName: String) {
        require(validPackageName(packageName))
        val body = JSONObject().put("package", packageName).toString().toRequestBody("application/json".toMediaType())
        check(request(link, "/launch", body).optBoolean("accepted")) { "Companion did not accept the launch request." }
    }

    private fun request(link: CompanionLink, path: String, body: RequestBody? = null): JSONObject {
        val http = client(link)
        try {
            val request = Request.Builder().url("https://${link.host}:$COMPANION_PORT$path")
                .header("Authorization", "Bearer ${link.token}").apply { if (body != null) post(body) }.build()
            return http.newCall(request).execute().use { response ->
                val output = ByteArrayOutputStream()
                response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 2 * 1024 * 1024) { "Companion response too large." }
                        output.write(buffer, 0, count)
                    }
                }
                val json = JSONObject(output.toString("UTF-8"))
                check(response.isSuccessful) { json.optString("error", "Companion request failed (${response.code}).") }
                json
            }
        } finally { http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
    }
}
