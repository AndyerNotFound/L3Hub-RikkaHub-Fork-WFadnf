package me.rerere.ai.provider.providers.deepseek

import kotlinx.coroutines.flow.Flow
import me.rerere.ai.provider.AccountBinding
import me.rerere.ai.provider.AccountCredentialProvider
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
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
import me.rerere.ai.provider.providers.openai.DeepSeekOcrAPI
import me.rerere.ai.provider.providers.withAccountAuthenticator
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.mergeCustomHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.uuid.Uuid

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
    private val ocrAPI = DeepSeekOcrAPI(this.client)

    override suspend fun listModels(providerSetting: ProviderSetting.DeepSeekAccount): List<Model> =
        withAccount(providerSetting) { token ->
            val chatModels = DeepSeekAccountPlatform.modelListing(client, token).map { listed ->
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
                    // 不声明推理档位：Claude 适配层发的是 Anthropic 的 adaptive + output_config，
                    // DeepSeek 的 Messages 只认 thinking.type 的 enabled/disabled，交给服务端默认值
                    abilities = listOf(ModelAbility.TOOL),
                    contextLength = listed.contextWindow ?: DEFAULT_CONTEXT_LENGTH,
                )
            }
            // 账号 token 同样能调 OCR 工具端点，单独列一个 OCR 模型给「OCR 辅助模型」选
            chatModels + Model(
                modelId = DeepSeekAccountPlatform.OCR_MODEL_ID,
                displayName = "DeepSeek OCR",
                type = ModelType.OCR,
                inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                outputModalities = listOf(Modality.TEXT),
            )
        }

    /**
     * 余额：主值与 [ProviderQuota.Balance.secondary] 是所有账号的合计（其他地方只显示这个），
     * [ProviderQuota.Balance.accounts] 带上每个账号自己的余额，设置里的用量卡分账号展开。
     */
    override suspend fun getQuota(providerSetting: ProviderSetting.DeepSeekAccount): ProviderQuota? {
        val enabled = providerSetting.accounts.filter { !it.disabled }
        if (enabled.isEmpty()) return null
        val rows = enabled.mapNotNull { binding ->
            // 单个账号读不到就跳过，不让一个失效账号把整张余额卡变成错误
            val wallets = readWallets(providerSetting, binding) ?: return@mapNotNull null
            binding to wallets
        }
        if (rows.isEmpty()) return null
        return ProviderQuota.Balance(
            value = formatWallets(rows.flatMap { it.second.normal }),
            secondary = rows.flatMap { it.second.bonus }
                .filter { it.isPositive() }
                .takeIf { it.isNotEmpty() }
                ?.let(::formatWallets),
            accounts = rows.map { (binding, wallets) ->
                ProviderQuota.AccountBalance(
                    label = binding.credential.displayName
                        ?: binding.credential.email
                        ?: providerSetting.name,
                    value = formatWallets(wallets.normal),
                    secondary = wallets.bonus
                        .filter { it.isPositive() }
                        .takeIf { it.isNotEmpty() }
                        ?.let(::formatWallets),
                )
            },
        )
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.DeepSeekAccount,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): TextGenerationResult {
        val preferred = params.model.preferredAccount()
        val token = credentialToken(providerSetting, preferred)
        if (params.model.type == ModelType.OCR) {
            return ocrAPI.generateText(
                baseUrl = DeepSeekAccountPlatform.INFERENCE_ORIGIN,
                requestHeaders = providerSetting.mergeCustomHeaders(params.customHeaders),
                sessionId = params.sessionId,
                model = params.model,
                messages = messages,
                auth = { writeTokenAuth(providerSetting, token, preferred) },
            )
        }
        return this.messages.generateText(
            providerSetting = providerSetting.asMessages(),
            messages = messages,
            params = params,
            auth = providerSetting.authOf(token, preferred),
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.DeepSeekAccount,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<StreamChunk> {
        val preferred = params.model.preferredAccount()
        val token = credentialToken(providerSetting, preferred)
        if (params.model.type == ModelType.OCR) {
            return ocrAPI.streamText(
                baseUrl = DeepSeekAccountPlatform.INFERENCE_ORIGIN,
                requestHeaders = providerSetting.mergeCustomHeaders(params.customHeaders),
                sessionId = params.sessionId,
                model = params.model,
                messages = messages,
                auth = { writeTokenAuth(providerSetting, token, preferred) },
            )
        }
        return this.messages.streamText(
            providerSetting = providerSetting.asMessages(),
            messages = messages,
            params = params,
            auth = providerSetting.authOf(token, preferred),
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

    private fun ProviderSetting.DeepSeekAccount.authOf(
        token: String,
        preferredAccountId: Uuid? = null,
    ): MessagesAuth = MessagesAuth(
        headerName = DeepSeekAccountPlatform.TOKEN_HEADER,
        token = token,
        tag = requestTag(preferredAccountId),
    )

    /**
     * 账号请求的 tag：401 时按这个头取被拒的 token，换一份凭据重试。
     *
     * [preferredAccountId] 是模型绑定的账号，重试也只找它。
     */
    private fun ProviderSetting.DeepSeekAccount.requestTag(
        preferredAccountId: Uuid? = null,
    ): AccountRequestTag = AccountRequestTag(
        setting = this,
        applyHeaders = { credential ->
            header(DeepSeekAccountPlatform.TOKEN_HEADER, credential.accessToken)
        },
        authHeader = AccountAuthHeader.DeepSeekToken,
        preferredAccountId = preferredAccountId,
    )

    /** OCR 等其他请求也要带同一个鉴权头与 tag */
    private fun Request.Builder.writeTokenAuth(
        setting: ProviderSetting.DeepSeekAccount,
        token: String,
        preferredAccountId: Uuid? = null,
    ) {
        header(DeepSeekAccountPlatform.TOKEN_HEADER, token)
        tag(AccountRequestTag::class.java, setting.requestTag(preferredAccountId))
    }

    /** 模型绑定的账号：按账号分组存下的第一条就是它的归属 */
    private fun Model.preferredAccount(): Uuid? = accountIds.firstOrNull()

    private suspend fun credentialToken(
        setting: ProviderSetting.DeepSeekAccount,
        preferredAccountId: Uuid?,
    ): String = credentials.getCredential(
        setting = setting,
        rejectedAccessToken = null,
        preferredAccountId = preferredAccountId,
    ).accessToken

    /**
     * 取账号 token 执行一次账号查询；平台拒绝时按「这个 token 被拒」交给账号管理清凭据，
     * 再把登录失效报给界面。
     */
    private suspend fun <T> withAccount(
        setting: ProviderSetting.DeepSeekAccount,
        preferredAccountId: Uuid? = null,
        block: suspend (token: String) -> T,
    ): T {
        val token = credentialToken(setting, preferredAccountId)
        return try {
            block(token)
        } catch (e: DeepSeekAccountUnauthorizedException) {
            credentials.getCredential(
                setting = setting,
                rejectedAccessToken = token,
                preferredAccountId = preferredAccountId,
            )
            throw e
        }
    }

    private fun DeepSeekWallet.isPositive(): Boolean =
        (balance.toBigDecimalOrNull()?.signum() ?: 0) == 1

    /** 同币种相加，不同币种并列；一位不进、直接截到两位，和平台网页一致 */
    private fun formatWallets(wallets: List<DeepSeekWallet>): String {
        if (wallets.isEmpty()) return "—"
        return wallets.groupBy { it.currency.uppercase() }
            .map { (currency, group) ->
                val total = group.fold(BigDecimal.ZERO) { sum, wallet ->
                    sum + (wallet.balance.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                }
                val symbol = if (currency == "USD") "$" else "¥"
                "$symbol${total.setScale(2, RoundingMode.DOWN).toPlainString()}"
            }
            .joinToString(" / ")
    }

    private suspend fun readWallets(
        setting: ProviderSetting.DeepSeekAccount,
        binding: AccountBinding,
    ): DeepSeekWallets? = try {
        DeepSeekAccountPlatform.wallets(client, binding.credential.accessToken)
    } catch (e: DeepSeekAccountUnauthorizedException) {
        // 顺手把被拒的账号交给账号管理清掉，下一次请求就不会再挑到它
        runCatching {
            credentials.getCredential(
                setting = setting,
                rejectedAccessToken = binding.credential.accessToken,
                preferredAccountId = binding.id,
            )
        }
        null
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
