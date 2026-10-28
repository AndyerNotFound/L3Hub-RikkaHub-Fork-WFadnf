package me.rerere.rikkahub.data.remote

import me.rerere.rikkahub.data.datastore.SettingsStore
import java.io.IOException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import okhttp3.Authenticator as OkHttpAuthenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

fun String.toProxyOrNull(): Proxy? {
    val value = trim()
    if (value.isEmpty()) return null

    val normalizedValue = if ("://" in value) value else "http://$value"
    val uri = runCatching { URI(normalizedValue) }.getOrNull() ?: return null
    val type = when (uri.scheme?.lowercase()) {
        "http", "https" -> Proxy.Type.HTTP
        "socks5" -> Proxy.Type.SOCKS
        else -> return null
    }
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    val port = uri.port.takeIf { it in 1..65535 } ?: return null
    if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
    if (uri.path?.let { it.isNotEmpty() && it != "/" } == true) return null

    return Proxy(type, InetSocketAddress.createUnresolved(host, port))
}

class SettingsProxyAuthenticator(
    private val settingsStore: SettingsStore,
) : OkHttpAuthenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val settings = settingsStore.settingsFlow.value
        val host = route?.address?.url?.host
        val providerProxyUrl = host?.let { settings.providerProxies[it] }
        val proxyUrl = providerProxyUrl ?: settings.networkSetting.proxyUrl
        val configuredProxy = proxyUrl.toProxyOrNull() ?: return null
        if (configuredProxy.type() != Proxy.Type.HTTP || route?.proxy != configuredProxy) return null
        val username = if (providerProxyUrl != null) {
            host?.let { settings.providerProxyUsernames[it] }.orEmpty()
        } else {
            settings.networkSetting.proxyUsername
        }
        val password = if (providerProxyUrl != null) {
            host?.let { settings.providerProxyPasswords[it] }.orEmpty()
        } else {
            settings.networkSetting.proxyPassword
        }
        if (username.isEmpty()) return null
        if (response.request.header("Proxy-Authorization") != null) return null

        return response.request.newBuilder()
            .header(
                "Proxy-Authorization",
                Credentials.basic(username, password, Charsets.UTF_8),
            )
            .build()
    }
}

class SettingsSocks5Authenticator(
    private val settingsStore: SettingsStore,
) : java.net.Authenticator() {
    override fun getPasswordAuthentication(): PasswordAuthentication? {
        val settings = settingsStore.settingsFlow.value
        if (!requestingProtocol.equals("SOCKS5", ignoreCase = true)) return null
        val requestedHost = requestingHost?.removeSurrounding("[", "]")

        settings.providerProxies.forEach { (host, url) ->
            val proxy = url.toProxyOrNull() ?: return@forEach
            if (proxy.type() != Proxy.Type.SOCKS) return@forEach
            val address = proxy.address() as? InetSocketAddress ?: return@forEach
            if (requestingPort != address.port) return@forEach
            if (!requestedHost.equals(address.hostString.removeSurrounding("[", "]"), ignoreCase = true)) {
                return@forEach
            }
            val username = settings.providerProxyUsernames[host].orEmpty()
            if (username.isEmpty()) return@forEach
            return PasswordAuthentication(
                username,
                settings.providerProxyPasswords[host].orEmpty().toCharArray(),
            )
        }

        val setting = settings.networkSetting
        val configuredProxy = setting.proxyUrl.toProxyOrNull() ?: return null
        val proxyAddress = configuredProxy.address() as? InetSocketAddress ?: return null
        if (configuredProxy.type() != Proxy.Type.SOCKS) return null
        if (setting.proxyUsername.isEmpty()) return null
        if (requestingPort != proxyAddress.port) return null
        val configuredHost = proxyAddress.hostString.removeSurrounding("[", "]")
        if (!requestedHost.equals(configuredHost, ignoreCase = true)) return null

        return PasswordAuthentication(setting.proxyUsername, setting.proxyPassword.toCharArray())
    }
}

class SettingsProxySelector(
    private val settingsStore: SettingsStore,
    private val systemProxySelector: ProxySelector? = ProxySelector.getDefault(),
) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> {
        val settings = settingsStore.settingsFlow.value
        // 提供商单独配置的代理优先于全局代理
        settings.providerProxies[uri.host]?.toProxyOrNull()?.let { proxy ->
            return listOf(proxy)
        }
        settings.networkSetting.proxyUrl.toProxyOrNull()?.let { proxy ->
            return listOf(proxy)
        }
        return runCatching { systemProxySelector?.select(uri) }
            .getOrNull()
            .orEmpty()
            .ifEmpty { listOf(Proxy.NO_PROXY) }
    }

    override fun connectFailed(uri: URI, socketAddress: SocketAddress, exception: IOException) {
        if (settingsStore.settingsFlow.value.networkSetting.proxyUrl.toProxyOrNull() == null) {
            systemProxySelector?.connectFailed(uri, socketAddress, exception)
        }
    }
}
