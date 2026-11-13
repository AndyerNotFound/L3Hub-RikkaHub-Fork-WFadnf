package me.rerere.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.util.fetchBody
import me.rerere.ai.util.json
import me.rerere.ai.util.mergeCustomHeaders
import me.rerere.common.http.getByKey
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal
import java.math.RoundingMode

private val TEMPLATE_VARIABLE = Regex("""\{\{\s*(\w+)\s*\}\}""")

/** 替换模板里的 `{{name}}`，引用了不存在的变量时报错 */
fun renderQuotaTemplate(template: String, variable: (String) -> String?): String =
    TEMPLATE_VARIABLE.replace(template) { match ->
        val name = match.groupValues[1]
        variable(name) ?: throw IllegalArgumentException("Unknown variable {{$name}}")
    }

/**
 * 执行 API Key 类提供商的自定义额度查询，没有开启时返回 null。
 *
 * 先放提供商级的自定义请求头，再写入查询配置的请求头，同名时以查询配置为准。
 */
internal suspend fun OkHttpClient.runQuotaQuery(
    setting: ProviderSetting.ApiKey,
    nextApiKey: () -> String,
): ProviderQuota.Balance? = withContext(Dispatchers.IO) {
    val query = setting.quotaQuery.takeIf { it.enabled } ?: return@withContext null
    // URL、请求头和请求体引用的是同一个 key
    val apiKey by lazy(nextApiKey)
    val render = { template: String ->
        renderQuotaTemplate(template) { name ->
            when (name) {
                QuotaQuery.VARIABLE_BASE_URL -> setting.baseUrl
                QuotaQuery.VARIABLE_API_KEY -> apiKey
                else -> null
            }
        }
    }
    val request = Request.Builder()
        .url(render(query.url.trim()))
        .headers(setting.mergeCustomHeaders())
        .apply {
            query.headers
                .filter { it.name.isNotBlank() }
                .forEach { header(it.name.trim(), render(it.value)) }
        }
        .apply {
            when (query.method) {
                QuotaQueryMethod.GET -> get()
                QuotaQueryMethod.POST -> post(render(query.body).toRequestBody("application/json".toMediaType()))
            }
        }
        .build()
    val root = json.parseToJsonElement(fetchBody(request)).jsonObject
    // DeepSeek 的余额接口把各币种放在 balance_infos 数组里，一并显示，避免只看到第一种货币
    formatBalanceInfos(root)?.let { return@withContext ProviderQuota.Balance(it) }
    val value = root.getByKey(query.resultPath)
    ProviderQuota.Balance(formatBalanceAmount(value))
}

private const val BALANCE_MAX_DECIMALS = 12
private const val BALANCE_MIN_DECIMALS = 2

/**
 * 余额按最多 12 位小数显示，去掉多余的 0，至少保留 2 位。
 *
 * 不是数字的文本（例如 unlimited）原样返回。
 */
internal fun formatBalanceAmount(value: String): String =
    value.toBigDecimalOrNull()?.let { formatBalanceAmount(it) } ?: value

internal fun formatBalanceAmount(value: BigDecimal): String =
    value.setScale(BALANCE_MAX_DECIMALS, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .let { it.setScale(it.scale().coerceAtLeast(BALANCE_MIN_DECIMALS)) }
        .toPlainString()

private fun formatBalanceInfos(root: JsonObject): String? {
    val infos = root["balance_infos"] as? JsonArray ?: return null
    return infos.mapNotNull { element ->
        val info = element as? JsonObject ?: return@mapNotNull null
        val amount = info["total_balance"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val currency = info["currency"]?.jsonPrimitive?.contentOrNull
        val formatted = formatBalanceAmount(amount)
        if (currency.isNullOrBlank()) formatted else "$currency $formatted"
    }.joinToString(" · ").ifBlank { null }
}
