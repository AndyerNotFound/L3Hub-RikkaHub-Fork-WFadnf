package me.rerere.ai.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
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
        val raw = (setting as? ProviderSetting.ApiKey)?.apiKey ?: return emptyList()
        return raw.split(Regex("[\\s,]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
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
        if (keys.size <= 1) return delegate.generateText(providerSetting, messages, params)

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
        if (keys.size <= 1) return delegate.streamText(providerSetting, messages, params)

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
        if (keys.size <= 1) return delegate.getQuota(providerSetting)
        return delegate.getQuota(withKey(providerSetting, keys.first()))
    }

    override suspend fun listModels(providerSetting: T): List<Model> {
        val keys = keysOf(providerSetting)
        if (keys.size <= 1) return delegate.listModels(providerSetting)

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
