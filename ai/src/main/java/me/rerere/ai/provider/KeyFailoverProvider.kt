package me.rerere.ai.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

/**
 * 多 Key 故障转移：每次请求从轮换的 Key 开始，失败时自动换下一个。
 * 流式请求一旦已经吐过内容就不再重试，避免重复输出。
 */
class KeyFailoverProvider<T : ProviderSetting>(
    private val delegate: Provider<T>,
) : Provider<T> by delegate {
    private val counter = AtomicInteger(0)

    private fun keysOf(setting: ProviderSetting): List<String> {
        val provider = setting as? ProviderSetting.ApiKey ?: return emptyList()
        val disabled = provider.disabledApiKeys.toSet()
        return provider.apiKey.split(Regex("[\\s,]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in disabled }
            .distinct()
    }

    /**
     * 只剩一个可用 Key 时也要把它换回去：apiKey 里可能存着多行 Key，
     * 直接下发等于把整串当成一个 Key 发出去。
     */
    private fun singleKeySetting(setting: T, keys: List<String>): T = when {
        keys.size == 1 -> withKey(setting, keys[0])
        keys.isEmpty() && setting is ProviderSetting.ApiKey && setting.apiKey.isNotBlank() ->
            throw IllegalStateException("All API keys are disabled")
        else -> setting
    }

    private fun order(keys: List<String>): List<String> {
        if (keys.size <= 1) return keys
        val start = (counter.getAndIncrement() and Int.MAX_VALUE) % keys.size
        return List(keys.size) { index -> keys[(start + index) % keys.size] }
    }

    @Suppress("UNCHECKED_CAST")
    private fun withKey(setting: T, key: String): T = when (setting) {
        is ProviderSetting.OpenAI -> setting.copy(apiKey = key) as T
        is ProviderSetting.Google -> setting.copy(apiKey = key) as T
        is ProviderSetting.Claude -> setting.copy(apiKey = key) as T
        else -> setting
    }

    override suspend fun generateText(
        providerSetting: T,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): TextGenerationResult {
        val keys = keysOf(providerSetting)
        if (keys.size <= 1) return delegate.generateText(singleKeySetting(providerSetting, keys), messages, params)

        var lastError: Throwable? = null
        order(keys).forEach { key ->
            try {
                return delegate.generateText(withKey(providerSetting, key), messages, params)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No API key available")
    }

    override suspend fun streamText(
        providerSetting: T,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<StreamChunk> {
        val keys = keysOf(providerSetting)
        if (keys.size <= 1) return delegate.streamText(singleKeySetting(providerSetting, keys), messages, params)

        return flow {
            var lastError: Throwable? = null
            for (key in order(keys)) {
                var emitted = false
                try {
                    delegate.streamText(withKey(providerSetting, key), messages, params)
                        .collect { chunk ->
                            emitted = true
                            emit(chunk)
                        }
                    return@flow
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    lastError = e
                    if (emitted) throw e
                }
            }
            throw lastError ?: IllegalStateException("No API key available")
        }
    }

    override suspend fun getQuota(providerSetting: T): ProviderQuota? {
        val keys = keysOf(providerSetting)
        if (keys.size <= 1) return delegate.getQuota(singleKeySetting(providerSetting, keys))

        val results = keys.map { key ->
            runCatching { delegate.getQuota(withKey(providerSetting, key)) }
        }
        val quotas = results.mapNotNull { it.getOrNull() }
        if (quotas.isEmpty()) {
            results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
            return null
        }
        // 余额类按币种求和；订阅用量各 Key 独立，取第一个能查到的结果
        val balances = quotas.filterIsInstance<ProviderQuota.Balance>().map { it.value }
        if (balances.isEmpty()) return quotas.first()
        return ProviderQuota.Balance(sumBalanceValues(balances))
    }

    override suspend fun listModels(providerSetting: T): List<Model> {
        val keys = keysOf(providerSetting)
        if (keys.size <= 1) return delegate.listModels(singleKeySetting(providerSetting, keys))

        var lastError: Throwable? = null
        keys.forEach { key ->
            try {
                return delegate.listModels(withKey(providerSetting, key))
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No API key available")
    }
}

private val BALANCE_TOKEN = Regex(
    """(?:(?<code>[A-Z]{3})\s*)?(?<symbol>[$€£¥￥])?\s*(?<amount>[-+]?\d+(?:\.\d+)?)\s*(?<suffix>[A-Z]{3})?"""
)

/**
 * 多 Key 余额求和。同一币种相加，不同币种按首次出现顺序用 · 分隔；
 * 例如 ["CNY 10.00 · USD 2.00", "CNY 5.00"] -> "CNY 15.00 · USD 2.00"。
 * 无法解析的文本保留为 "a + b"，避免界面变成空白。
 */
internal fun sumBalanceValues(values: List<String>): String {
    data class Bucket(var amount: BigDecimal, var scale: Int)
    val buckets = linkedMapOf<String, Bucket>()
    var parsed = false

    values.forEach { value ->
        BALANCE_TOKEN.findAll(value).forEach { match ->
            val amountText = match.groups["amount"]?.value ?: return@forEach
            val number = amountText.toBigDecimalOrNull() ?: return@forEach
            val currency = match.groups["code"]?.value
                ?: match.groups["symbol"]?.value
                ?: match.groups["suffix"]?.value
                ?: ""
            val scale = amountText.substringAfter('.', "").length
            val bucket = buckets.getOrPut(currency) { Bucket(BigDecimal.ZERO, 0) }
            bucket.amount += number
            bucket.scale = maxOf(bucket.scale, scale)
            parsed = true
        }
    }

    if (!parsed) {
        return values.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" + ")
    }
    return buckets.entries.joinToString(" · ") { (currency, bucket) ->
        val amount = bucket.amount.setScale(bucket.scale).toPlainString()
        when {
            currency.isEmpty() -> amount
            currency.length == 1 -> "$currency$amount"
            else -> "$currency $amount"
        }
    }
}
