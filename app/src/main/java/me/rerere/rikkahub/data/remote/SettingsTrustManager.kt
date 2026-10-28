package me.rerere.rikkahub.data.remote

import me.rerere.rikkahub.data.datastore.SettingsStore
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * 在系统 CA 之外额外信任用户在设置里导入的 CA，用于自签 HTTPS 提供商。
 * 自定义证书按设置内容缓存，改证书后下一次 TLS 握手自动重建。
 */
class SettingsTrustManager(
    private val settingsStore: SettingsStore,
) : X509TrustManager {
    private val defaultManager: X509TrustManager = TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(null as KeyStore?) }
        .trustManagers
        .filterIsInstance<X509TrustManager>()
        .first()

    @Volatile
    private var cachedPem: List<String> = emptyList()

    @Volatile
    private var customManager: X509TrustManager? = null

    val sslContext: SSLContext
        get() = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(this@SettingsTrustManager), null)
        }

    private fun customManager(): X509TrustManager? {
        val pems = settingsStore.settingsFlow.value.customCaCertificates
        if (pems == cachedPem) return customManager
        val manager = runCatching { buildManager(pems) }.getOrNull()
        cachedPem = pems
        customManager = manager
        return manager
    }

    private fun buildManager(pems: List<String>): X509TrustManager? {
        if (pems.isEmpty()) return null
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
        }
        var index = 0
        val factory = CertificateFactory.getInstance("X.509")
        pems.forEach { pem ->
            val certificates = factory.generateCertificates(
                ByteArrayInputStream(pem.toByteArray())
            )
            certificates.filterIsInstance<X509Certificate>().forEach { certificate ->
                keyStore.setCertificateEntry("l3hub-user-ca-${index++}", certificate)
            }
        }
        if (index == 0) return null
        return TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        defaultManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        try {
            defaultManager.checkServerTrusted(chain, authType)
            return
        } catch (e: CertificateException) {
            val custom = customManager() ?: throw e
            custom.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = defaultManager.acceptedIssuers
}
