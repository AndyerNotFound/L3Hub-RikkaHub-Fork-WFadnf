package me.rerere.ai.provider.providers.deepseek

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.util.json
import me.rerere.common.http.await
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/** 账号凭据被平台拒绝（HTTP 401 或业务码 40003）：需要重新登录 */
class DeepSeekAccountUnauthorizedException(message: String) : Exception(message)

/** 一次浏览器授权的起始信息 */
data class DeepSeekSignInStart(
    val authorizeUrl: String,
    val authorizeId: String,
    val expiresInSeconds: Int,
)

/** 平台用户资料；联系方式保留平台脱敏后的写法 */
data class DeepSeekPlatformUser(
    val id: String?,
    val name: String?,
    val contact: String?,
    val avatarUrl: String?,
)

data class DeepSeekSignInResult(
    val token: String,
    val user: DeepSeekPlatformUser?,
    /** 兑换后浏览器应打开的平台完成页，没有时为空 */
    val authorizedUrl: String?,
)

data class DeepSeekWallet(val currency: String, val balance: String)

/** 充值钱包与赠金钱包分开返回，界面上也分行显示 */
data class DeepSeekWallets(val normal: List<DeepSeekWallet>, val bonus: List<DeepSeekWallet>)

/** 账号模型目录里的一条；能力字段按 Messages 协议读 */
data class DeepSeekListedModel(
    val id: String,
    val name: String,
    val contextWindow: Int?,
    val inputModalities: List<String>,
)

/**
 * DeepSeek 平台账号的 HTTP 协议：浏览器登录、资料、钱包、退登与模型目录。
 *
 * 站点、路径与请求头与 DeepSeek Harness 保持一致：授权走 PKCE，账号 token 只放在 `x-dsh-auth-token` 上，
 * 不加 Bearer 前缀；平台按客户端自报的身份头与版本号作答，没有签名校验。模型目录由平台按
 * `client_requirements["deepseek-harness"].min_version` 决定，因此版本号沿用 Harness 的写法。
 */
object DeepSeekAccountPlatform {
    const val PLATFORM_ORIGIN = "https://platform.deepseek.com"
    const val INFERENCE_ORIGIN = "https://api.deepseek.com"

    /** Messages 端点根，请求地址是 `${MESSAGES_BASE_URL}/messages` */
    const val MESSAGES_BASE_URL = "https://api.deepseek.com/anthropic/v1"

    /** 账号 token 的请求头；只放这一个头，不附带 `Authorization` */
    const val TOKEN_HEADER = "x-dsh-auth-token"

    /** 账号请求唯一允许携带 token 的两个来源，都在官方 https 域名下 */
    internal val OFFICIAL_ORIGINS = setOf(PLATFORM_ORIGIN, INFERENCE_ORIGIN)

    /** 这些头由账号鉴权独占，自定义头里出现就不发，避免一个请求带两份凭据 */
    internal val RESERVED_AUTH_HEADERS = setOf(TOKEN_HEADER, "authorization")

    /** 已经加固过的 client：newBuilder 共享连接池，按实例缓存即可，不必每个请求包一层 */
    private val guardedClients = ConcurrentHashMap<OkHttpClient, OkHttpClient>()

    /** OCR 工具端点的模型 id，和官方 API Key 路由同名 */
    const val OCR_MODEL_ID = "deepseek-ocr"

    /** 平台按这个版本号决定给出哪些模型 */
    const val CLIENT_VERSION = "0.2.2-rc.1"

    /** 这个头是不是由账号鉴权独占 */
    fun isReservedAuthHeader(name: String): Boolean = name.lowercase() in RESERVED_AUTH_HEADERS

    /**
     * 加固后的 client：不跟重定向，且只放行官方来源。
     *
     * 幂等：已经加固过的实例原样返回，重复包一层没有意义。
     */
    internal fun guardedClient(client: OkHttpClient): OkHttpClient {
        if (client.interceptors.any { it is OfficialOriginInterceptor }) return client
        return guardedClients.getOrPut(client) {
            client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(OfficialOriginInterceptor)
                .build()
        }
    }

    /**
     * 账号 token 只发官方地址。
     *
     * 请求覆写（cURL）能把地址换成任意值，这一层是最后一道闸：非官方来源当场失败，
     * 而不是带着 `x-dsh-auth-token` 发出去。
     */
    internal object OfficialOriginInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url
            val origin = "${url.scheme}://${url.host}"
            // 用 IOException 而不是 require：拦截器里抛非 IO 异常会从 OkHttp 的执行线程上逃出去，
            // 报成 IOException 才会走调用方正常的失败路径
            if (origin !in OFFICIAL_ORIGINS) {
                throw IOException("DeepSeek 账号请求只能发往官方地址，已拦截：$origin")
            }
            return chain.proceed(chain.request())
        }
    }

    private const val PLATFORM_HOST = "platform.deepseek.com"

    private const val USER_AGENT =
        "deepseek-harness/$CLIENT_VERSION (+https://github.com/deepseek-ai/deepseek-harness)"

    /** 发起授权：返回浏览器要打开的授权页，会话 10 分钟内有效 */
    suspend fun startSignIn(
        client: OkHttpClient,
        codeChallenge: String,
        state: String,
        redirectUri: String,
    ): DeepSeekSignInStart = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("code_challenge", codeChallenge)
            put("code_challenge_method", "S256")
            put("state", state)
            put("redirect_uri", redirectUri)
            put("locale", wireLocale())
            put("login_source", "web")
        }
        val data = postPlatform(client, "/auth-api/v0/dsh/auth_init", body.toString(), token = null)
        DeepSeekSignInStart(
            authorizeUrl = validatedAuthorizeUrl(
                data["authorize_url"].stringOrNull() ?: error("DeepSeek 没有返回授权页地址")
            ),
            authorizeId = data["authorize_id"].stringOrNull() ?: error("DeepSeek 没有返回授权 ID"),
            expiresInSeconds = (data["expires_in"] as? JsonPrimitive)?.intOrNull ?: 600,
        )
    }

    /** 用回调拿到的 code 换账号 token，同时带上设备信息 */
    suspend fun exchange(
        client: OkHttpClient,
        code: String,
        codeVerifier: String,
        redirectUri: String,
        deviceId: String,
    ): DeepSeekSignInResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("code", code)
            put("code_verifier", codeVerifier)
            put("redirect_uri", redirectUri)
            put("device_id", deviceId)
            put("device_model", "${Build.MANUFACTURER}-${Build.MODEL}")
            put("os_version", Build.VERSION.RELEASE.orEmpty())
        }
        val data = postPlatform(client, "/auth-api/v0/dsh/auth_exchange", body.toString(), token = null)
        DeepSeekSignInResult(
            token = data["token"].stringOrNull() ?: error("DeepSeek 没有返回账号 token"),
            user = data["user"]?.let(::parseUser),
            authorizedUrl = completionUrl(data["authorized_url"].stringOrNull()),
        )
    }

    /** 本地取消授权时作废远端尚未兑换的申请；失败不影响本地取消 */
    suspend fun cancel(client: OkHttpClient, authorizeId: String, codeVerifier: String) =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("authorize_id", authorizeId)
                put("code_verifier", codeVerifier)
            }
            postPlatform(client, "/auth-api/v0/dsh/auth_cancel", body.toString(), token = null)
            Unit
        }

    /** 账号资料：昵称、联系方式与头像，联系方式保留平台脱敏后的写法 */
    suspend fun profile(client: OkHttpClient, token: String): DeepSeekPlatformUser =
        withContext(Dispatchers.IO) {
            parseUser(getPlatform(client, "/auth-api/v0/users/current", token))
        }

    /** 充值余额与赠金余额 */
    suspend fun wallets(client: OkHttpClient, token: String): DeepSeekWallets =
        withContext(Dispatchers.IO) {
            parseWallets(getPlatform(client, "/api/v0/users/get_user_summary", token))
        }

    /** 退登：用捕获的 token 结束平台会话 */
    suspend fun logout(client: OkHttpClient, token: String) = withContext(Dispatchers.IO) {
        postPlatform(client, "/auth-api/v0/users/logout", body = null, token = token)
        Unit
    }

    /**
     * 账号模型目录。
     *
     * 只保留声明了 Messages 能力的条目；要求比当前客户端更新的模型按 Harness 的规则隐藏，
     * 否则选到它也只会在请求前失败。
     */
    suspend fun modelListing(client: OkHttpClient, token: String): List<DeepSeekListedModel> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$INFERENCE_ORIGIN/models")
                .header("accept", "application/json")
                .header("user-agent", USER_AGENT)
                .header(TOKEN_HEADER, token)
                .get()
                .build()
            val root = json.parseToJsonElement(execute(client, request)).jsonObject
            val rows = root["data"] as? JsonArray ?: return@withContext emptyList()
            rows.mapNotNull { row ->
                val entry = row as? JsonObject ?: return@mapNotNull null
                val id = entry["id"].stringOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val capabilities = entry["api_capabilities"] as? JsonObject
                if (capabilities?.get("anthropic_messages") !is JsonObject) return@mapNotNull null
                val required = (entry["client_requirements"] as? JsonObject)
                    ?.get("deepseek-harness") as? JsonObject
                val minVersion = required?.get("min_version").stringOrNull()
                if (minVersion != null && compareVersions(CLIENT_VERSION, minVersion) < 0) {
                    return@mapNotNull null
                }
                val inputs = (entry["input_modalities"] as? JsonArray)
                    ?.mapNotNull { it.stringOrNull() }
                    .orEmpty()
                DeepSeekListedModel(
                    id = id,
                    name = entry["name"].stringOrNull()?.takeIf { it.isNotEmpty() } ?: id,
                    contextWindow = (entry["context_window"] as? JsonPrimitive)?.intOrNull,
                    inputModalities = inputs,
                )
            }
        }

    /**
     * 授权页地址只接受平台来源上的 `/dsh/authorize`。
     *
     * 这个地址会被直接交给浏览器；平台或链路上的任何改写都不该把用户带去别处，认不出来时当场失败，
     * 而不是打开一个来路不明的页面。
     */
    fun validatedAuthorizeUrl(authorizeUrl: String): String {
        val url = authorizeUrl.toHttpUrlOrNull()
            ?: error("DeepSeek 返回的授权页地址无法解析")
        if (url.scheme != "https" || url.host != PLATFORM_HOST || url.encodedPath != "/dsh/authorize") {
            error("DeepSeek 返回的授权页不在官方地址上，已拒绝打开")
        }
        return url.toString()
    }

    /**
     * 兑换成功后浏览器要落到平台的完成页。
     *
     * 只接受平台来源上的 `/dsh/authorized`，避免把浏览器（以及它带上的查询参数）交给任意地址；
     * 认不出来时返回 null，由调用方改用自己的页面。
     */
    fun completionUrl(authorizedUrl: String?, loginSource: String = "web"): String? {
        val url = authorizedUrl?.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.host != PLATFORM_HOST || url.encodedPath != "/dsh/authorized") {
            return null
        }
        return url.newBuilder().setQueryParameter("login_source", loginSource).build().toString()
    }

    private suspend fun postPlatform(
        client: OkHttpClient,
        path: String,
        body: String?,
        token: String?,
    ): JsonObject {
        val builder = Request.Builder()
            .url("$PLATFORM_ORIGIN$path")
            .header("accept", "application/json")
            .header("user-agent", USER_AGENT)
        if (body != null) {
            builder.post(body.toRequestBody("application/json".toMediaType()))
        } else {
            builder.post(ByteArray(0).toRequestBody(null))
        }
        clientIdentityHeaders().forEach { (name, value) -> builder.header(name, value) }
        token?.let { builder.header(TOKEN_HEADER, it) }
        return readBizData(execute(client, builder.build()), authenticated = token != null)
    }

    private suspend fun getPlatform(client: OkHttpClient, path: String, token: String): JsonObject {
        val builder = Request.Builder()
            .url("$PLATFORM_ORIGIN$path")
            .header("accept", "application/json")
            .header("user-agent", USER_AGENT)
            .header(TOKEN_HEADER, token)
            .get()
        clientIdentityHeaders().forEach { (name, value) -> builder.header(name, value) }
        return readBizData(execute(client, builder.build()), authenticated = true)
    }

    private suspend fun execute(client: OkHttpClient, request: Request): String {
        // 平台请求同样带账号 token：不跟重定向，且只发官方地址
        val response = client.forDeepSeekAccount().newCall(request).await()
        return response.use {
            val body = it.body?.string().orEmpty()
            if (it.code == 401) throw DeepSeekAccountUnauthorizedException("DeepSeek 账号凭据已被拒绝")
            if (!it.isSuccessful) error("DeepSeek 请求失败：HTTP ${it.code} ${body.take(200)}")
            body
        }
    }

    /** 平台的外层信封：`code` 与 `data.biz_code` 都为 0 才算成功 */
    private fun readBizData(body: String, authenticated: Boolean): JsonObject {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return JsonObject(emptyMap())
        val code = (root["code"] as? JsonPrimitive)?.intOrNull ?: 0
        if (authenticated && code == 40003) {
            throw DeepSeekAccountUnauthorizedException("DeepSeek 账号凭据已被拒绝")
        }
        if (code != 0) error("DeepSeek 平台返回 code=$code")
        val data = root["data"] as? JsonObject ?: return JsonObject(emptyMap())
        val bizCode = (data["biz_code"] as? JsonPrimitive)?.intOrNull ?: 0
        if (authenticated && bizCode == 40003) {
            throw DeepSeekAccountUnauthorizedException("DeepSeek 账号凭据已被拒绝")
        }
        if (bizCode != 0) {
            val message = data["biz_msg"].stringOrNull().orEmpty()
            error("DeepSeek 平台返回 biz_code=$bizCode $message".trim())
        }
        return data["biz_data"] as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun parseUser(element: JsonElement): DeepSeekPlatformUser {
        val user = element as? JsonObject ?: return DeepSeekPlatformUser(null, null, null, null)
        val identity = user["id_profile"] as? JsonObject
        val contact = listOf("mobile", "mobile_number", "email")
            .firstNotNullOfOrNull { user[it].stringOrNull()?.takeIf(String::isNotBlank) }
        return DeepSeekPlatformUser(
            id = user["id"].stringOrNull()?.takeIf { it.isNotBlank() },
            name = identity?.get("name").stringOrNull()?.takeIf { it.isNotBlank() },
            contact = contact,
            avatarUrl = identity?.get("picture").stringOrNull()?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseWallets(data: JsonObject): DeepSeekWallets {
        fun read(name: String): List<DeepSeekWallet> =
            (data[name] as? JsonArray).orEmpty().mapNotNull { row ->
                val wallet = row as? JsonObject ?: return@mapNotNull null
                val balance = wallet["balance"].stringOrNull() ?: return@mapNotNull null
                DeepSeekWallet(currency = wallet["currency"].stringOrNull() ?: "CNY", balance = balance)
            }

        return DeepSeekWallets(normal = read("normal_wallets"), bonus = read("bonus_wallets"))
    }

    /** 平台只认 `zh_CN` 与 `en_US` 两种界面语言 */
    private fun wireLocale(): String =
        if (Locale.getDefault().language == "zh") "zh_CN" else "en_US"

    private fun clientIdentityHeaders(): List<Pair<String, String>> = listOf(
        "x-client-platform" to "web",
        "x-client-version" to CLIENT_VERSION,
        "x-client-locale" to wireLocale(),
        "x-client-timezone-offset" to (TimeZone.getDefault().rawOffset / 1000).toString(),
    )

    private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

    /** 语义化版本比较；预发布号低于同号正式版 */
    private fun compareVersions(current: String, required: String): Int {
        val currentCore = current.substringBefore('-')
        val requiredCore = required.substringBefore('-')
        compareParts(currentCore, requiredCore).let { if (it != 0) return it }
        val currentPre = current.substringAfter('-', "")
        val requiredPre = required.substringAfter('-', "")
        if (currentPre.isEmpty() && requiredPre.isEmpty()) return 0
        if (currentPre.isEmpty()) return 1
        if (requiredPre.isEmpty()) return -1
        return compareParts(currentPre, requiredPre)
    }

    private fun compareParts(left: String, right: String): Int {
        val leftParts = left.split('.')
        val rightParts = right.split('.')
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val leftPart = leftParts.getOrNull(index)
            val rightPart = rightParts.getOrNull(index)
            val leftNumber = leftPart?.toIntOrNull()
            val rightNumber = rightPart?.toIntOrNull()
            if (leftNumber != null && rightNumber != null) {
                if (leftNumber != rightNumber) return leftNumber.compareTo(rightNumber)
            } else {
                val compared = (leftPart ?: "").compareTo(rightPart ?: "")
                if (compared != 0) return compared
            }
        }
        return 0
    }
}

/**
 * 账号 token 只发官方地址，且绝不跟随重定向。
 *
 * OkHttp 跟随跨站重定向时只会摘掉 `Authorization`，自定义头 `x-dsh-auth-token` 会原样带到重定向
 * 目标；平台一旦回了 3xx，账号 token 就等于交给了第三方。这里直接关掉重定向，并在拦截器里钉死来源，
 * 非官方地址当场失败，而不是带着 token 发出去。
 */
internal fun OkHttpClient.forDeepSeekAccount(): OkHttpClient =
    DeepSeekAccountPlatform.guardedClient(this)
