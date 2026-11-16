package me.rerere.rikkahub.data.ai.account

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.provider.AccountBinding
import me.rerere.ai.provider.AccountCredentialProvider
import me.rerere.ai.provider.OAuthCredential
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.providers.deepseek.DeepSeekAccountPlatform
import me.rerere.ai.provider.providers.deepseek.DeepSeekPlatformUser
import me.rerere.common.http.await
import me.rerere.oauth.CustomTabsOAuthAuthorizationLauncher
import me.rerere.oauth.OAuthHttpClient
import me.rerere.oauth.OAuthLoopbackCallbackServer
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private const val TAG = "ProviderAccountManager"

// 与 Codex CLI 一致：access token 过期前 5 分钟内就刷新
private const val TOKEN_REFRESH_LEEWAY_MS = 5 * 60_000L
private val AUTHORIZATION_TIMEOUT = 5.minutes
private const val DEEPSEEK_DEVICE_ID_KEY = "deepseek_account_device_id"

// 被平台拒绝的账号先冷却这么久，期间路由到下一个账号
private const val ACCOUNT_COOLDOWN_MS = 5 * 60_000L

sealed interface AccountLoginStatus {
    data object Authorizing : AccountLoginStatus

    /** 设备码登录：用户在浏览器里打开 [verificationUrl] 确认 [userCode] */
    data class DeviceCode(val userCode: String, val verificationUrl: String) : AccountLoginStatus

    data class Failed(val message: String) : AccountLoginStatus
}

/**
 * 管理账号型提供商（[ProviderSetting.Account]）的登录、token 刷新和凭据持久化。
 *
 * 凭据只由这里写回设置，界面编辑提供商时应保留设置里的最新凭据，见 [preserveCredential]。
 */
class ProviderAccountManager(
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
    private val client: OkHttpClient,
) : AccountCredentialProvider {
    // 走应用的 OkHttpClient，授权和刷新请求也能用上设置里的代理
    private val oauthClient = OAuthHttpClient(client)
    private val chatGPTCallbackServer = OAuthLoopbackCallbackServer(
        port = ChatGPTAuth.CALLBACK_PORT,
        callbackPath = ChatGPTAuth.CALLBACK_PATH,
        redirectHost = ChatGPTAuth.CALLBACK_HOST,
    )

    // DeepSeek 接受任意端口，用临时端口即可
    private val deepSeekCallbackServer = OAuthLoopbackCallbackServer(port = 0)
    // 401 之后临时不用的账号：bindingId -> 恢复时间（只在内存里，重启后重新尝试）
    private val accountCooldowns = ConcurrentHashMap<Uuid, Long>()
    private val loginJobs = ConcurrentHashMap<Uuid, Job>()
    private val refreshLocks = ConcurrentHashMap<Uuid, Mutex>()

    private val _loginStatus = MutableStateFlow<Map<Uuid, AccountLoginStatus>>(emptyMap())
    val loginStatus: StateFlow<Map<Uuid, AccountLoginStatus>> = _loginStatus.asStateFlow()

    fun startLogin(providerId: Uuid, context: Context) {
        val provider = findAccount(providerId) ?: return
        loginJobs.remove(providerId)?.cancel()
        setStatus(providerId, AccountLoginStatus.Authorizing)
        val job = appScope.launch {
            try {
                val credential = when (provider) {
                    is ProviderSetting.ChatGPT -> loginChatGPT(context.applicationContext)
                    is ProviderSetting.Grok -> loginGrok(providerId, context.applicationContext)
                    is ProviderSetting.DeepSeekAccount -> loginDeepSeek(context.applicationContext)
                }
                persistLogin(providerId, credential)
                setStatus(providerId, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Login failed for ${provider.name}", e)
                setStatus(
                    providerId,
                    AccountLoginStatus.Failed(e.message ?: e.javaClass.simpleName)
                )
            }
        }
        loginJobs[providerId] = job
        job.invokeOnCompletion { loginJobs.remove(providerId, job) }
    }

    fun cancelLogin(providerId: Uuid) {
        loginJobs.remove(providerId)?.cancel()
        setStatus(providerId, null)
    }

    /**
     * 退出登录。[bindingId] 为空时退掉该提供商的全部账号。
     */
    suspend fun logout(providerId: Uuid, bindingId: Uuid? = null) {
        cancelLogin(providerId)
        val account = findAccount(providerId) ?: return
        if (account !is ProviderSetting.DeepSeekAccount) {
            persistCredential(providerId, null)
            return
        }
        val removed = account.accounts.filter { bindingId == null || it.id == bindingId }
        removed.forEach { accountCooldowns.remove(it.id) }
        // 本地先退，再用捕获的 token 结束平台会话：远端失败不回滚本地状态
        updateAccounts(providerId) { accounts ->
            accounts.filterNot { bindingId == null || it.id == bindingId }
        }
        removed.forEach { binding ->
            val token = binding.credential.accessToken
            withContext(Dispatchers.IO) { runCatching { DeepSeekAccountPlatform.logout(client, token) } }
        }
    }

    /**
     * 需要刷新时在 [appScope] 里执行，调用方被取消（停止生成、关掉额度界面）时打断不了已经发出的刷新：
     * 服务端一收到请求就会轮换 refresh token，新的没存下来就只能重新登录。
     */
    override suspend fun getCredential(
        setting: ProviderSetting.Account,
        rejectedAccessToken: String?,
    ): OAuthCredential {
        // DeepSeek 账号的 token 没有过期时间和刷新流程，被拒就清凭据
        if (setting is ProviderSetting.DeepSeekAccount) {
            return resolveDeepSeekCredential(setting, rejectedAccessToken)
        }
        // 绝大多数请求的 token 都还有效，直接返回，不用切线程排队拿锁
        findAccount(setting.id)?.credential
            ?.takeUnless { it.needsRefresh(rejectedAccessToken) }
            ?.let { return it }
        return appScope.async(Dispatchers.IO) {
            refreshIfNeeded(setting, rejectedAccessToken)
        }.await()
    }

    /**
     * 按提供商串行刷新。拿到锁后重新读设置：并发请求可能已经刷新过，refresh token 只能用一次。
     */
    private suspend fun refreshIfNeeded(
        setting: ProviderSetting.Account,
        rejectedAccessToken: String?,
    ): OAuthCredential {
        val lock = refreshLocks.computeIfAbsent(setting.id) { Mutex() }
        return lock.withLock {
            val latest = findAccount(setting.id) ?: setting
            val credential = latest.credential ?: error("${latest.name}: not signed in")
            if (!credential.needsRefresh(rejectedAccessToken)) return@withLock credential

            val refreshToken = credential.refreshToken
                ?: error("${latest.name}: session expired, please sign in again")
            val refreshed = try {
                when (latest) {
                    is ProviderSetting.ChatGPT -> ChatGPTAuth.toCredential(
                        token = oauthClient.refreshToken(ChatGPTAuth.refreshRequest(refreshToken)),
                        previous = credential,
                    )

                    is ProviderSetting.Grok -> GrokAuth.toCredential(
                        token = oauthClient.refreshToken(GrokAuth.refreshRequest(refreshToken)),
                        previous = credential,
                    )

                    is ProviderSetting.DeepSeekAccount ->
                        error("${latest.name}: DeepSeek account tokens cannot be refreshed")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IllegalStateException(
                    "${latest.name}: failed to refresh sign-in, please sign in again",
                    e
                )
            }
            // 刷新期间用户可能退出或换了账号，这时结果作废，不能把旧会话写回去
            if (!replaceCredential(latest.id, expected = credential, credential = refreshed)) {
                error("${latest.name}: sign-in changed during refresh, please try again")
            }
            refreshed
        }
    }

    /**
     * DeepSeek 平台账号：PKCE 授权码 + 本机回环回调。
     *
     * 平台要求「换完凭证再给浏览器结果」：回调请求先挂起，兑换成功后把浏览器跳到平台完成页，
     * 兑换失败则回一张说明失败原因的页面。授权页与完成页都留在平台的浏览器标签里。
     */
    private suspend fun loginDeepSeek(context: Context): OAuthCredential =
        withContext(Dispatchers.IO) {
            val pkce = oauthClient.generatePkce()
            val state = oauthClient.generateState()
            val session = deepSeekCallbackServer.openDeferredSession(context, state)
            var authorizeId: String? = null
            try {
                val start = DeepSeekAccountPlatform.startSignIn(
                    client = client,
                    codeChallenge = pkce.challenge,
                    state = state,
                    redirectUri = session.redirectUri,
                )
                authorizeId = start.authorizeId
                Log.i(TAG, "DeepSeek sign-in: authorize page requested, expires in ${start.expiresInSeconds}s")
                withContext(Dispatchers.Main) {
                    CustomTabsOAuthAuthorizationLauncher.launch(context, start.authorizeUrl)
                }
                val callback = session.awaitCallback(AUTHORIZATION_TIMEOUT)
                if (callback == null) {
                    session.respondPage(deepSeekPage(title = "授权超时", message = "请回到 L3Hub 重新登录。"))
                    error("Authorization timed out")
                }
                Log.i(TAG, "DeepSeek sign-in: callback received, error=${callback.error}")
                callback.error?.let { code ->
                    val failure = listOfNotNull(code, callback.errorDescription).joinToString(": ")
                    session.respondPage(deepSeekPage(title = "授权失败", message = failure))
                    error(failure)
                }
                val code = callback.code
                if (code == null) {
                    session.respondPage(deepSeekPage(title = "授权失败", message = "没有返回授权码。"))
                    error("No authorization code returned")
                }
                val result = DeepSeekAccountPlatform.exchange(
                    client = client,
                    code = code,
                    codeVerifier = pkce.verifier,
                    redirectUri = session.redirectUri,
                    deviceId = deepSeekDeviceId(context),
                )
                Log.i(TAG, "DeepSeek sign-in: token exchanged")
                // 浏览器落到平台完成页；平台没给完成页时退回自己的成功页
                val completion = result.authorizedUrl
                if (completion != null) {
                    session.respondRedirect(completion)
                } else {
                    session.respondPage(deepSeekPage(title = "授权完成", message = "可以关闭此页面回到 L3Hub。"))
                }
                // 兑换时平台会带上用户资料，没带就单独查一次
                val user = result.user ?: runCatching {
                    DeepSeekAccountPlatform.profile(client, result.token)
                }.getOrNull()
                deepSeekCredential(result.token, user)
            } catch (e: Exception) {
                authorizeId?.let { id ->
                    withContext(NonCancellable) {
                        runCatching { DeepSeekAccountPlatform.cancel(client, id, pkce.verifier) }
                    }
                }
                throw e
            } finally {
                withContext(NonCancellable) { session.close() }
            }
        }

    private fun deepSeekPage(title: String, message: String): String = """
        <!doctype html><html lang="zh-CN"><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>$title</title>
        <body style="font-family:sans-serif;padding:32px;line-height:1.6">
        <h1 style="font-size:20px">$title</h1><p>$message</p>
        </body></html>
    """.trimIndent()

    /**
     * 按列表顺序挑一个可用账号：被拒或冷却中的账号先跳过。
     *
     * 只有一个账号又被拒时清掉它，界面回到未登录；否则换下一个账号，
     * OkHttp 的 401 重试会带着新凭据再发一次。
     */
    private suspend fun resolveDeepSeekCredential(
        setting: ProviderSetting.DeepSeekAccount,
        rejectedAccessToken: String?,
    ): OAuthCredential {
        val latest = (findAccount(setting.id) as? ProviderSetting.DeepSeekAccount) ?: setting
        val enabled = latest.accounts.filter { !it.disabled }
        if (enabled.isEmpty()) error("${latest.name}: not signed in")

        val now = System.currentTimeMillis()
        if (rejectedAccessToken != null) {
            enabled.firstOrNull { it.credential.accessToken == rejectedAccessToken }?.let { rejected ->
                accountCooldowns[rejected.id] = now + ACCOUNT_COOLDOWN_MS
                if (enabled.size == 1) {
                    updateAccounts(latest.id) { accounts -> accounts.filterNot { it.id == rejected.id } }
                    accountCooldowns.remove(rejected.id)
                    error("${latest.name}: sign-in expired, please sign in again")
                }
            }
        }
        return (enabled.firstOrNull { (accountCooldowns[it.id] ?: 0L) <= now } ?: enabled.first()).credential
    }

    private fun deepSeekCredential(token: String, user: DeepSeekPlatformUser?): OAuthCredential =
        OAuthCredential(
            accessToken = token,
            // 没有刷新流程，也不带过期时间
            expiresAt = 0L,
            accountId = user?.id,
            email = user?.contact,
            displayName = user?.name,
        )

    /** 设备标识只用于兑换请求；首次登录生成后留在本地 */
    private fun deepSeekDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("rikkahub.preferences", Context.MODE_PRIVATE)
        prefs.getString(DEEPSEEK_DEVICE_ID_KEY, null)?.let { return it }
        val id = Uuid.random().toString()
        prefs.edit().putString(DEEPSEEK_DEVICE_ID_KEY, id).apply()
        return id
    }

    private suspend fun loginChatGPT(context: Context): OAuthCredential =
        withContext(Dispatchers.IO) {
            val pkce = oauthClient.generatePkce()
            val state = oauthClient.generateState()
            val session = chatGPTCallbackServer.openSession(context, state)
            try {
                val authorizationUrl = oauthClient.buildAuthorizationUrl(
                    ChatGPTAuth.authorizationRequest(
                        pkce = pkce,
                        state = state,
                        redirectUri = session.redirectUri
                    )
                )
                withContext(Dispatchers.Main) {
                    CustomTabsOAuthAuthorizationLauncher.launch(context, authorizationUrl)
                }
                val callback =
                    session.awaitCallback(AUTHORIZATION_TIMEOUT) ?: error("Authorization timed out")
                callback.error?.let { code ->
                    error(listOfNotNull(code, callback.errorDescription).joinToString(": "))
                }
                val code = callback.code ?: error("No authorization code returned")
                val token = oauthClient.exchangeAuthorizationCode(
                    OAuthHttpClient.AuthorizationCodeTokenRequest(
                        tokenEndpoint = ChatGPTAuth.TOKEN_ENDPOINT,
                        clientId = ChatGPTAuth.CLIENT_ID,
                        code = code,
                        codeVerifier = pkce.verifier,
                        redirectUri = session.redirectUri,
                    )
                )
                ChatGPTAuth.toCredential(token, previous = null)
            } finally {
                withContext(NonCancellable) { session.close() }
            }
        }

    // 登录由 appScope（主线程）发起，网络请求和读响应体都放到 IO
    private suspend fun loginGrok(providerId: Uuid, context: Context): OAuthCredential =
        withContext(Dispatchers.IO) {
            val authorization =
                oauthClient.requestDeviceAuthorization(GrokAuth.deviceAuthorizationRequest())
            // 带 user_code 的地址打开后不用手动输入
            val url = authorization.verificationUriComplete ?: authorization.verificationUri
            require(url.startsWith("https://")) { "Invalid verification URL: $url" }
            setStatus(providerId, AccountLoginStatus.DeviceCode(authorization.userCode, url))
            withContext(Dispatchers.Main) {
                CustomTabsOAuthAuthorizationLauncher.launch(context, url)
            }
            val token = oauthClient.awaitDeviceToken(
                GrokAuth.deviceTokenRequest(authorization.deviceCode),
                authorization
            )
            val credential = GrokAuth.toCredential(token, previous = null)
            if (credential.email != null) credential else credential.copy(email = fetchGrokEmail(credential))
        }

    // token 里没有邮箱时查 userinfo，只用于界面展示，查不到不影响登录
    private suspend fun fetchGrokEmail(credential: OAuthCredential): String? = runCatching {
        val request = Request.Builder()
            .url(GrokAuth.USERINFO_ENDPOINT)
            .header("Authorization", "Bearer ${credential.accessToken}")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) return@use null
            JsonInstant.parseToJsonElement(response.body.string()).jsonObject.claimString("email")
        }
    }.onFailure { Log.w(TAG, "Failed to fetch Grok userinfo", it) }.getOrNull()

    /** 登录成功后写回：多账号的提供商追加一份，单账号的提供商替换 */
    private suspend fun persistLogin(providerId: Uuid, credential: OAuthCredential) {
        settingsStore.update { old ->
            old.copy(
                providers = old.providers.map { provider ->
                    when {
                        provider.id != providerId -> provider
                        provider is ProviderSetting.DeepSeekAccount -> provider.copy(
                            // 同一账号重复登录就替换原来那份，避免攒重复条目
                            accounts = provider.accounts.filterNot { existing ->
                                credential.accountId != null && existing.credential.accountId == credential.accountId
                            } + AccountBinding(credential = credential)
                        )

                        provider is ProviderSetting.Account -> provider.withCredential(credential)
                        else -> provider
                    }
                }
            )
        }
    }

    /** 停用/启用某一份登录：停用后不参与路由，凭据保留 */
    suspend fun setAccountDisabled(providerId: Uuid, bindingId: Uuid, disabled: Boolean) {
        updateAccounts(providerId) { accounts ->
            accounts.map { binding -> if (binding.id == bindingId) binding.copy(disabled = disabled) else binding }
        }
        if (!disabled) accountCooldowns.remove(bindingId)
    }

    private suspend fun updateAccounts(
        providerId: Uuid,
        transform: (List<AccountBinding>) -> List<AccountBinding>,
    ) {
        settingsStore.update { old ->
            old.copy(
                providers = old.providers.map { provider ->
                    if (provider.id == providerId && provider is ProviderSetting.DeepSeekAccount) {
                        provider.copy(accounts = transform(provider.accounts))
                    } else {
                        provider
                    }
                }
            )
        }
    }

    private suspend fun persistCredential(providerId: Uuid, credential: OAuthCredential?) {
        settingsStore.update { old ->
            old.copy(
                providers = old.providers.map { provider ->
                    if (provider.id == providerId && provider is ProviderSetting.Account) {
                        provider.withCredential(credential)
                    } else {
                        provider
                    }
                }
            )
        }
    }

    /** 设置里的凭据仍是 [expected] 时才替换，返回是否替换了 */
    private suspend fun replaceCredential(
        providerId: Uuid,
        expected: OAuthCredential,
        credential: OAuthCredential,
    ): Boolean {
        var replaced = false
        settingsStore.update { old ->
            old.copy(
                providers = old.providers.map { provider ->
                    if (provider.id == providerId && provider is ProviderSetting.Account &&
                        provider.credential == expected) {
                        replaced = true
                        provider.withCredential(credential)
                    } else {
                        provider
                    }
                }
            )
        }
        return replaced
    }

    // 刚被服务端拒绝，或者快过期了
    private fun OAuthCredential.needsRefresh(rejectedAccessToken: String?): Boolean =
        accessToken == rejectedAccessToken ||
            (expiresAt > 0 && System.currentTimeMillis() >= expiresAt - TOKEN_REFRESH_LEEWAY_MS)

    private fun findAccount(providerId: Uuid): ProviderSetting.Account? =
        settingsStore.settingsFlow.value.providers
            .find { it.id == providerId } as? ProviderSetting.Account

    private fun setStatus(providerId: Uuid, status: AccountLoginStatus?) {
        _loginStatus.update { if (status == null) it - providerId else it + (providerId to status) }
    }
}

/**
 * 界面整份写回提供商时用：凭据只能由 [ProviderAccountManager] 修改，
 * 编辑期间后台可能已经刷新过 token，旧凭据写回会把轮换后的 refresh token 覆盖掉。
 */
fun ProviderSetting.preserveCredential(latest: ProviderSetting): ProviderSetting =
    if (this is ProviderSetting.Account && latest is ProviderSetting.Account) {
        withAccounts(latest.accounts)
    } else {
        this
    }
