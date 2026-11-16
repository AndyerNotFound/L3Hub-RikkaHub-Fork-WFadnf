package me.rerere.ai.provider

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.uuid.Uuid

/** 账号登录得到的 OAuth 凭据 */
@Serializable
data class OAuthCredential(
    val accessToken: String,
    val refreshToken: String? = null,
    // access token 过期时间（毫秒时间戳），0 表示未知
    val expiresAt: Long = 0L,
    // 厂商侧的账号/工作区 ID，ChatGPT 请求需要带上
    val accountId: String? = null,
    // 以下仅用于界面展示
    val email: String? = null,
    val displayName: String? = null,
    val plan: String? = null,
) {
    override fun toString(): String =
        "OAuthCredential(accessToken=${accessToken.masked()}, refreshToken=${refreshToken?.masked()}, " +
            "expiresAt=$expiresAt, accountId=$accountId, email=$email, displayName=$displayName, plan=$plan)"

    private fun String.masked(): String = if (length <= 8) "***" else "${take(4)}***${takeLast(4)}"
}

/** 账号型提供商里的一份登录；多账号时按列表顺序路由 */
@Serializable
data class AccountBinding(
    val id: Uuid = Uuid.random(),
    val credential: OAuthCredential,
    // 手动停用的账号不参与路由，凭据仍然保留
    val disabled: Boolean = false,
)

@Serializable
enum class ClaudePromptCacheTtl(val apiValue: String?) {
    @SerialName("5m")
    FIVE_MINUTES(null),

    @SerialName("1h")
    ONE_HOUR("1h")
}

/**
 * 是否支持模型内置联网搜索（[BuiltInTools.Search]）。
 *
 * OpenAI 兼容提供商只有 Responses API 有 `web_search` 工具，Chat Completions 没有。
 */
val ProviderSetting.supportsBuiltInSearch: Boolean
    get() = when (this) {
        is ProviderSetting.OpenAI -> useResponseApi
        is ProviderSetting.Google -> true
        is ProviderSetting.Claude -> true
        is ProviderSetting.ChatGPT, is ProviderSetting.Grok -> true
        // DeepSeek 账号走 Messages 协议，联网搜索是客户端工具，不是服务端内置工具
        is ProviderSetting.DeepSeekAccount -> false
        // 本地模型没有厂商侧的联网搜索
        is ProviderSetting.LiteRT,
        is ProviderSetting.LocalServer,
        -> false
    }

/**
 * 当前提供商能不能真的发出这个内置工具。
 *
 * 界面按它决定模型编辑页列出哪些开关。列出来却发不出去的开关只会把功能弄坏：账号型提供商
 * 走 Messages 协议，模型上留着 web_search 时请求里没有它，客户端搜索又会以为已经有服务端搜索。
 */
fun ProviderSetting.supportsBuiltInTool(tool: BuiltInTools): Boolean = when (tool) {
    BuiltInTools.Search -> supportsBuiltInSearch
    // 只有 Gemini 的接口认 urlContext，其余实现里这一项是空转
    BuiltInTools.UrlContext -> this is ProviderSetting.Google
    // image_generation 是 Responses 协议的服务端工具
    BuiltInTools.ImageGeneration ->
        this is ProviderSetting.ChatGPT || this is ProviderSetting.Grok ||
            (this is ProviderSetting.OpenAI && useResponseApi)
}

/**
 * 多 Key 文本框的分隔符：换行、逗号、空白都当作两个 Key 之间。
 *
 * 分组页、故障转移和配置页读的是同一份文本框，拆分规则必须一致，
 * 否则分组页里的 Key 和请求实际用上的 Key 会对不上。
 */
private val API_KEY_SEPARATOR = Regex("[\\s,]+")

/** 启用中的 Key，按文本框里的顺序去重；停用的 Key 不参与请求，也不单独分组 */
val ProviderSetting.ApiKey.availableKeys: List<String>
    get() {
        val disabled = disabledApiKeys.toSet()
        return apiKey.split(API_KEY_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in disabled }
            .distinct()
    }

/** 这个 Key 的备注，没写过备注时返回 null */
fun ProviderSetting.ApiKey.keyLabel(key: String): String? =
    apiKeyLabels[key]?.trim()?.takeIf { it.isNotEmpty() }

/**
 * 掩码后的 Key：没有备注时用它给分组起一个能认人的名字。
 *
 * 短 Key 也要留住首尾，否则几把同样短的 Key 会撞出同一个名字。
 */
fun maskedKey(key: String): String = when {
    key.length <= 4 -> "••••"
    key.length <= 12 -> "${key.take(2)}••${key.takeLast(2)}"
    else -> "${key.take(4)}••••${key.takeLast(4)}"
}

@Serializable
sealed class ProviderSetting {
    abstract val id: Uuid
    abstract val enabled: Boolean
    abstract val name: String
    abstract val models: List<Model>
    abstract val customHeaders: List<CustomHeader>

    /**
     * 请求频率、时间段限额、定时锁定、余额刷新和请求覆写。
     *
     * 具体的设置类型都把它作为构造参数，`copy()` 时不会丢；本地模型没有覆写请求的入口，
     * 但同样可以设置频率限制和锁定。
     */
    open val guard: ProviderGuard get() = ProviderGuard()

    abstract val builtIn: Boolean
    abstract val description:
        @Composable()
        () -> Unit
    abstract val shortDescription:
        @Composable()
        () -> Unit

    fun addModel(model: Model): ProviderSetting = copyProvider(models = models + model)

    /**
     * 从远端目录添加一条模型。
     *
     * 同一个分组（同一把 Key、同一个账号）里已经有这条就什么都不做：分页里点错一次不该多出
     * 第二条空价目的副本。别的分组里有同一个模型时沿用那一条的价目与变价规则——用户填过的价格
     * 不该因为重加一次就归零。
     */
    fun addCatalogModel(model: Model): ProviderSetting {
        val sameGroup = models.any { existing ->
            existing.modelId == model.modelId &&
                existing.apiKeys == model.apiKeys &&
                existing.accountIds == model.accountIds
        }
        if (sameGroup) return this
        val priced = models.firstOrNull { it.modelId == model.modelId && it.pricing.isConfigured }
        val entry = if (priced != null && !model.pricing.isConfigured) {
            model.copy(pricing = priced.pricing)
        } else {
            model
        }
        return copyProvider(models = models + entry)
    }

    fun editModel(model: Model): ProviderSetting =
        copyProvider(models = models.map { if (it.id == model.id) model.copy() else it })

    fun delModel(model: Model): ProviderSetting = copyProvider(models = models.filter { it.id != model.id })

    fun moveMove(from: Int, to: Int): ProviderSetting =
        copyProvider(models = models.toMutableList().apply { add(to, removeAt(from)) })

    abstract fun copyProvider(
        id: Uuid = this.id,
        enabled: Boolean = this.enabled,
        name: String = this.name,
        models: List<Model> = this.models,
        customHeaders: List<CustomHeader> = this.customHeaders,
        builtIn: Boolean = this.builtIn,
        description: @Composable (() -> Unit) = this.description,
        shortDescription: @Composable (() -> Unit) = this.shortDescription,
        guard: ProviderGuard = this.guard,
    ): ProviderSetting

    @Serializable
    @SerialName("openai")
    data class OpenAI(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "OpenAI",
        override var models: List<Model> = emptyList(),
        override val quotaQuery: QuotaQuery = QuotaQuery(),
        override val customHeaders: List<CustomHeader> = emptyList(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        override var apiKey: String = "",
        // 用户手动关掉的 Key，仍然留在 apiKey 里，随时可以重新启用
        override var disabledApiKeys: List<String> = emptyList(),
        override var apiKeyLabels: Map<String, String> = emptyMap(),
        override var baseUrl: String = "https://api.openai.com/v1",
        var chatCompletionsPath: String = "/chat/completions",
        var useResponseApi: Boolean = false,
        var includeHistoryReasoning: Boolean = true,
        var responsesPath: String = "/responses",
        override val guard: ProviderGuard = ProviderGuard(),
    ) : ApiKey() {
        override fun withQuotaQuery(query: QuotaQuery): ApiKey = copy(quotaQuery = query)

        override fun withApiKey(apiKey: String): ApiKey = copy(apiKey = apiKey)

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            customHeaders: List<CustomHeader>,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
            guard: ProviderGuard,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                customHeaders = customHeaders,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                guard = guard,
            )
        }
    }

    @Serializable
    @SerialName("google")
    data class Google(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "Google",
        override var models: List<Model> = emptyList(),
        override val quotaQuery: QuotaQuery =
            QuotaQuery(headers = listOf(CustomHeader("x-goog-api-key", "{{apiKey}}"))),
        override val customHeaders: List<CustomHeader> = emptyList(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        override var apiKey: String = "",
        override var disabledApiKeys: List<String> = emptyList(),
        override var apiKeyLabels: Map<String, String> = emptyMap(),
        override var baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
        var vertexAI: Boolean = false,
        var useServiceAccount: Boolean = false,
        var privateKey: String = "", // only for vertex AI service account
        var serviceAccountEmail: String = "", // only for vertex AI service account
        var location: String = "us-central1", // only for vertex AI service account
        var projectId: String = "", // only for vertex AI service account
        var useInteractionsApi: Boolean = false, // ignored when vertex AI is enabled
        override val guard: ProviderGuard = ProviderGuard(),
    ) : ApiKey() {
        override fun withQuotaQuery(query: QuotaQuery): ApiKey = copy(quotaQuery = query)

        override fun withApiKey(apiKey: String): ApiKey = copy(apiKey = apiKey)

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            customHeaders: List<CustomHeader>,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
            guard: ProviderGuard,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                customHeaders = customHeaders,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                guard = guard,
            )
        }
    }

    @Serializable
    @SerialName("claude")
    data class Claude(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "Claude",
        override var models: List<Model> = emptyList(),
        override val quotaQuery: QuotaQuery = QuotaQuery(headers = listOf(CustomHeader("x-api-key", "{{apiKey}}"))),
        override val customHeaders: List<CustomHeader> = emptyList(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        override var apiKey: String = "",
        override var disabledApiKeys: List<String> = emptyList(),
        override var apiKeyLabels: Map<String, String> = emptyMap(),
        override var baseUrl: String = "https://api.anthropic.com/v1",
        var promptCaching: Boolean = false,
        var promptCacheTtl: ClaudePromptCacheTtl = ClaudePromptCacheTtl.FIVE_MINUTES,
        override val guard: ProviderGuard = ProviderGuard(),
    ) : ApiKey() {
        override fun withQuotaQuery(query: QuotaQuery): ApiKey = copy(quotaQuery = query)

        override fun withApiKey(apiKey: String): ApiKey = copy(apiKey = apiKey)

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            customHeaders: List<CustomHeader>,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
            guard: ProviderGuard,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                customHeaders = customHeaders,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                guard = guard,
            )
        }
    }

    /** 用 API Key 鉴权、地址可以自填的提供商 */
    @Serializable
    sealed class ApiKey : ProviderSetting() {
        abstract val apiKey: String
        abstract val disabledApiKeys: List<String>

        /** 每个 Key 的自定义标签，按 Key 文本索引，只用于界面显示 */
        abstract val apiKeyLabels: Map<String, String>
        abstract val baseUrl: String

        /** 同一份配置换成另一个 Key，用于多 Key 轮询或逐个 Key 拉模型 */
        abstract fun withApiKey(apiKey: String): ApiKey

        /** 用户自定义的额度查询 */
        abstract val quotaQuery: QuotaQuery

        abstract fun withQuotaQuery(query: QuotaQuery): ApiKey
    }

    /**
     * 绑定官方账号的提供商。
     *
     * 没有 baseUrl 和 apiKey：请求地址由具体类型写死，凭据只能通过账号登录获得，保证 token
     * 不会被发到第三方地址。凭据由 app 层的账号管理负责登录、刷新和写回，界面编辑不应改动它。
     */
    @Serializable
    sealed class Account : ProviderSetting() {
        /** 未登录时为 null；多账号时是当前用于路由的那一个 */
        abstract val credential: OAuthCredential?

        /** 已登录的账号，按路由优先级排序；单账号类型就是 0 或 1 个 */
        open val accounts: List<AccountBinding>
            get() = credential?.let { listOf(AccountBinding(id = id, credential = it)) } ?: emptyList()

        /** 写回整份账号列表；单账号类型只保留第一个 */
        open fun withAccounts(accounts: List<AccountBinding>): Account =
            withCredential(accounts.firstOrNull()?.credential)

        abstract fun withCredential(credential: OAuthCredential?): Account
    }

    @Serializable
    @SerialName("chatgpt")
    data class ChatGPT(
        override val id: Uuid = Uuid.random(),
        override val enabled: Boolean = true,
        override val name: String = "ChatGPT",
        override val models: List<Model> = emptyList(),
        override val customHeaders: List<CustomHeader> = emptyList(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        override val credential: OAuthCredential? = null,
        override val guard: ProviderGuard = ProviderGuard(),
    ) : Account() {
        override fun withCredential(credential: OAuthCredential?): Account = copy(credential = credential)

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            customHeaders: List<CustomHeader>,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
            guard: ProviderGuard,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                customHeaders = customHeaders,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                guard = guard,
            )
        }
    }

    @Serializable
    @SerialName("grok")
    data class Grok(
        override val id: Uuid = Uuid.random(),
        override val enabled: Boolean = true,
        override val name: String = "Grok",
        override val models: List<Model> = emptyList(),
        override val customHeaders: List<CustomHeader> = emptyList(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        @Transient override val shortDescription: @Composable (() -> Unit) = {},
        override val credential: OAuthCredential? = null,
        override val guard: ProviderGuard = ProviderGuard(),
    ) : Account() {
        override fun withCredential(credential: OAuthCredential?): Account = copy(credential = credential)

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            customHeaders: List<CustomHeader>,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
            guard: ProviderGuard,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                customHeaders = customHeaders,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                guard = guard,
            )
        }
    }

    /**
     * DeepSeek 平台账号（浏览器登录）。
     *
     * 没有 baseUrl 和 apiKey：推理走 api.deepseek.com 的 Messages 端点，只带 `x-dsh-auth-token`；
     * 模型目录、充值余额与赠金都由账号读取，地址不可改，凭据也不会被发往第三方。
     */
    @Serializable
    @SerialName("deepseek-account")
    data class DeepSeekAccount(
        override val id: Uuid = Uuid.random(),
        override val enabled: Boolean = true,
        override val name: String = "DeepSeek",
        override val models: List<Model> = emptyList(),
        override val customHeaders: List<CustomHeader> = emptyList(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {
            Text("用 DeepSeek 平台账号登录，不需要 API Key。模型目录、充值余额与赠金都由账号读取，接口地址固定。")
        },
        @Transient override val shortDescription: @Composable (() -> Unit) = {
            Text("账号登录 · 地址固定")
        },
        override val accounts: List<AccountBinding> = emptyList(),
        override val guard: ProviderGuard = ProviderGuard(),
    ) : Account() {
        /** 路由用的账号：第一个未停用的 */
        override val credential: OAuthCredential?
            get() = accounts.firstOrNull { !it.disabled }?.credential

        override fun withCredential(credential: OAuthCredential?): Account = copy(
            accounts = credential?.let { listOf(AccountBinding(credential = it)) } ?: emptyList()
        )

        override fun withAccounts(accounts: List<AccountBinding>): Account = copy(accounts = accounts)

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            customHeaders: List<CustomHeader>,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
            guard: ProviderGuard,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                customHeaders = customHeaders,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                guard = guard,
            )
        }
    }

    /**
     * 本地 LiteRT 模型。
     *
     * 运行时由应用携带（litertlm-android），模型是应用私有目录里的一个 .litertlm 文件，
     * 推理全程在本机完成，不经过网络。界面上的 [modelPath] 由导入流程写入。
     */
    @Serializable
    @SerialName("litert")
    data class LiteRT(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "LiteRT",
        override var models: List<Model> = emptyList(),
        override val customHeaders: List<CustomHeader> = emptyList(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {
            Text("在本机运行 LiteRT-LM 模型。运行时随应用提供，导入 .litertlm 模型文件后即可离线对话。")
        },
        @Transient override val shortDescription: @Composable (() -> Unit) = {
            Text("本机运行 · 运行时内置")
        },
        // 已导入到应用私有目录的模型文件绝对路径
        var modelPath: String = "",
        var backend: LiteRTBackend = LiteRTBackend.CPU,
        // 0 表示交给运行时决定
        var cpuThreads: Int = 0,
        // KV cache 大小，同时也是模型可用的上下文窗口
        var contextTokens: Int = 4096,
        var maxOutputTokens: Int = 2048,
        var temperature: Float = 0.8f,
        var topK: Int = 40,
        var topP: Float = 0.95f,
        var enableThinking: Boolean = false,
        var thinkingBudget: Int = 1024,
        // 模型支持视觉输入时开启，图片会随消息传入
        var visionEnabled: Boolean = false,
        override val guard: ProviderGuard = ProviderGuard(),
    ) : ProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            customHeaders: List<CustomHeader>,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
            guard: ProviderGuard,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                customHeaders = customHeaders,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                guard = guard,
            )
        }
    }

    /**
     * 通过用户自行安装的本地推理服务运行的模型。
     *
     * 应用不携带 llama.cpp / ONNX Runtime，只按 OpenAI 兼容协议连接本机地址；
     * 运行时的安装、模型路径和启动命令都由用户配置。
     */
    @Serializable
    sealed class LocalServer : ProviderSetting() {
        abstract val baseUrl: String
        abstract val apiKey: String
        // 运行时可执行文件的路径，只用于展示启动命令和排查问题
        abstract val runtimePath: String
        // 运行时加载的模型文件或目录
        abstract val modelPath: String
        abstract val contextTokens: Int
        // 用户记录的启动命令；应用不会执行它，只用于复制和备忘
        abstract val launchCommand: String

        /** 交给 OpenAI 兼容实现去请求；地址始终是用户填的本机服务 */
        fun asOpenAI(): OpenAI = OpenAI(
            id = id,
            enabled = enabled,
            name = name,
            models = models,
            customHeaders = customHeaders,
            builtIn = builtIn,
            description = description,
            shortDescription = shortDescription,
            apiKey = apiKey.ifBlank { "local" },
            baseUrl = baseUrl.trim().trimEnd('/'),
            useResponseApi = false,
            guard = guard,
        )
    }

    /** 本地 GGUF 模型：llama.cpp 的 llama-server 暴露 OpenAI 兼容接口 */
    @Serializable
    @SerialName("gguf")
    data class GGUF(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "GGUF",
        override var models: List<Model> = emptyList(),
        override val customHeaders: List<CustomHeader> = emptyList(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {
            Text("用自行安装的 llama.cpp（llama-server）在本机运行 GGUF 模型。应用只连接本机地址，模型与推理都不出设备。")
        },
        @Transient override val shortDescription: @Composable (() -> Unit) = {
            Text("llama.cpp 本地服务")
        },
        override val apiKey: String = "",
        override val baseUrl: String = "http://127.0.0.1:8080/v1",
        override val runtimePath: String = "",
        override val modelPath: String = "",
        override val contextTokens: Int = 4096,
        var gpuLayers: Int = 0,
        var threads: Int = 4,
        override var launchCommand: String = "",
        override val guard: ProviderGuard = ProviderGuard(),
    ) : LocalServer() {
        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            customHeaders: List<CustomHeader>,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
            guard: ProviderGuard,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                customHeaders = customHeaders,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                guard = guard,
            )
        }
    }

    /** 本地 ONNX 模型：连接用户安装的 ONNX Runtime 推理服务 */
    @Serializable
    @SerialName("onnx")
    data class ONNX(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "ONNX",
        override var models: List<Model> = emptyList(),
        override val customHeaders: List<CustomHeader> = emptyList(),
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {
            Text("用自行安装的 ONNX Runtime 推理服务在本机运行 ONNX 模型。应用按 OpenAI 兼容协议连接你配置的本地地址。")
        },
        @Transient override val shortDescription: @Composable (() -> Unit) = {
            Text("ONNX Runtime 本地服务")
        },
        override val apiKey: String = "",
        override val baseUrl: String = "http://127.0.0.1:8001/v1",
        override val runtimePath: String = "",
        override val modelPath: String = "",
        override val contextTokens: Int = 4096,
        // 例如 cpu / nnapi / xnnpack，供启动命令展示使用
        var executionProvider: String = "",
        var threads: Int = 4,
        override var launchCommand: String = "",
        override val guard: ProviderGuard = ProviderGuard(),
    ) : LocalServer() {
        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            customHeaders: List<CustomHeader>,
            builtIn: Boolean,
            description: @Composable (() -> Unit),
            shortDescription: @Composable (() -> Unit),
            guard: ProviderGuard,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                customHeaders = customHeaders,
                builtIn = builtIn,
                description = description,
                shortDescription = shortDescription,
                guard = guard,
            )
        }
    }

    companion object {
        val Types by lazy {
            listOf(
                OpenAI::class,
                Google::class,
                Claude::class,
            )
        }
    }
}

/** LiteRT-LM 的执行后端 */
@Serializable
enum class LiteRTBackend {
    @SerialName("cpu")
    CPU,

    @SerialName("gpu")
    GPU,
}
