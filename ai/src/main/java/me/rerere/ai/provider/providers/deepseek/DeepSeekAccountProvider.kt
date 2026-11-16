package me.rerere.ai.provider.providers.deepseek

import kotlinx.coroutines.flow.Flow
import me.rerere.ai.provider.AccountCredentialProvider
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderQuota
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.provider.providers.AccountAuthHeader
import me.rerere.ai.provider.providers.AccountRequestTag
import me.rerere.ai.provider.providers.claude.ClaudeProvider
import me.rerere.ai.provider.providers.claude.MessagesAuth
import me.rerere.ai.provider.providers.withAccountAuthenticator
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import okhttp3.OkHttpClient
import java.math.RoundingMode

/** 平台没有给出上下文窗口时的兜底值，与官方模型列表一致 */
private const val DEFAULT_CONTEXT_LENGTH = 1_048_576

/**
 * DeepSeek 平台账号（浏览器登录）。
 *
 * 对话走 Messages 协议，与 Claude 共用同一份实现（[ClaudeProvider]），区别只在地址固定为
 * `api.deepseek.com/anthropic/v1`、鉴权只写 `x-dsh-auth-token`；模型目录与钱包余额都由账号读，
 * 地址不可改，账号 token 不会被发往第三方。
 */
class DeepSeekAccountProvider(
    client: OkHttpClient,
    private val credentials: AccountCredentialProvider,
) : Provider<ProviderSetting.DeepSeekAccount> {
    private val client = client.withAccountAuthenticator(credentials)
    private val messages = ClaudeProvider(this.client)

    override suspend fun listModels(providerSetting: ProviderSetting.DeepSeekAccount): List<Model> =
        withAccount(providerSetting) { token ->
            DeepSeekAccountPlatform.modelListing(client, token).map { listed ->
                val vision = listed.inputModalities.any { it.equals("image", ignoreCase = true) }
                Model(
                    modelId = listed.id,
                    displayName = listed.name,
                    inputModalities = if (vision) {
                        listOf(Modality.TEXT, Modality.IMAGE)
                    } else {
                        listOf(Modality.TEXT)
                    },
                    outputModalities = listOf(Modality.TEXT),
                    // 账号目录里的模型都走 Messages 工具与推理档位
                    abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
                    contextLength = listed.contextWindow ?: DEFAULT_CONTEXT_LENGTH,
                )
            }
        }

    /** 充值余额与赠金余额分两条：主值是充值余额，赠金放在 [ProviderQuota.Balance.secondary] */
    override suspend fun getQuota(providerSetting: ProviderSetting.DeepSeekAccount): ProviderQuota? =
        withAccount(providerSetting) { token ->
            val wallets = DeepSeekAccountPlatform.wallets(client, token)
            val normal = wallets.normal.map { it.formatted() }
            // 赠金只显示正额，与平台网页一致
            val bonus = wallets.bonus
                .filter { it.balance.toBigDecimalOrNull()?.signum() == 1 }
                .map { it.formatted() }
            when {
                normal.isEmpty() && bonus.isEmpty() -> null
                normal.isEmpty() -> ProviderQuota.Balance(value = bonus.joinToString(" / "))
                else -> ProviderQuota.Balance(
                    value = normal.joinToString(" / "),
                    secondary = bonus.takeIf { it.isNotEmpty() }?.joinToString(" / "),
                )
            }
        }

    override suspend fun generateText(
        providerSetting: ProviderSetting.DeepSeekAccount,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): TextGenerationResult {
        val token = credentials.getCredential(providerSetting, rejectedAccessToken = null).accessToken
        return this.messages.generateText(
            providerSetting = providerSetting.asMessages(),
            messages = messages,
            params = params,
            auth = providerSetting.authOf(token),
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.DeepSeekAccount,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<StreamChunk> {
        val token = credentials.getCredential(providerSetting, rejectedAccessToken = null).accessToken
        return this.messages.streamText(
            providerSetting = providerSetting.asMessages(),
            messages = messages,
            params = params,
            auth = providerSetting.authOf(token),
        )
    }

    /** Messages 请求要的配置项由账号类型给出，地址与鉴权不从这里来 */
    private fun ProviderSetting.DeepSeekAccount.asMessages(): ProviderSetting.Claude =
        ProviderSetting.Claude(
            id = id,
            enabled = enabled,
            name = name,
            models = models,
            customHeaders = customHeaders,
            guard = guard,
            apiKey = "",
            baseUrl = DeepSeekAccountPlatform.MESSAGES_BASE_URL,
            // DeepSeek 的上下文缓存是自动的，不发 cache_control
            promptCaching = false,
        )

    private fun ProviderSetting.DeepSeekAccount.authOf(token: String): MessagesAuth = MessagesAuth(
        headerName = DeepSeekAccountPlatform.TOKEN_HEADER,
        token = token,
        tag = AccountRequestTag(
            setting = this,
            applyHeaders = { credential ->
                header(DeepSeekAccountPlatform.TOKEN_HEADER, credential.accessToken)
            },
            authHeader = AccountAuthHeader.DeepSeekToken,
        ),
    )

    /**
     * 取账号 token 执行一次账号查询；平台拒绝时按「这个 token 被拒」交给账号管理清凭据，
     * 再把登录失效报给界面。
     */
    private suspend fun <T> withAccount(
        setting: ProviderSetting.DeepSeekAccount,
        block: suspend (token: String) -> T,
    ): T {
        val token = credentials.getCredential(setting, rejectedAccessToken = null).accessToken
        return try {
            block(token)
        } catch (e: DeepSeekAccountUnauthorizedException) {
            credentials.getCredential(setting, rejectedAccessToken = token)
            throw e
        }
    }

    private fun DeepSeekWallet.formatted(): String {
        val symbol = if (currency.equals("USD", ignoreCase = true)) "$" else "¥"
        val amount = balance.toBigDecimalOrNull()
            ?.setScale(2, RoundingMode.DOWN)
            ?.toPlainString()
            ?: balance
        return "$symbol$amount"
    }
}
