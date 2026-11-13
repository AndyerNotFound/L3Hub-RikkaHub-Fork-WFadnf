package me.rerere.ai.provider.providers.local

import kotlinx.coroutines.flow.Flow
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderQuota
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.provider.providers.openai.OpenAIProvider
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage

/**
 * 用户自行安装的本地推理服务：把本地提供商的配置换成 OpenAI 兼容请求发到本机地址。
 *
 * llama.cpp 的 llama-server 与 ONNX Runtime 的推理服务都按 OpenAI Chat Completions 协议工作，
 * 因此这里不复制一套 HTTP 逻辑，直接复用 [OpenAIProvider]。
 */
class LocalServerProvider(
    private val delegate: OpenAIProvider,
) : Provider<ProviderSetting.LocalServer> {
    override suspend fun listModels(providerSetting: ProviderSetting.LocalServer): List<Model> =
        delegate.listModels(providerSetting.asOpenAI())

    override suspend fun getQuota(providerSetting: ProviderSetting.LocalServer): ProviderQuota? = null

    override suspend fun generateText(
        providerSetting: ProviderSetting.LocalServer,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): TextGenerationResult = delegate.generateText(providerSetting.asOpenAI(), messages, params)

    override suspend fun streamText(
        providerSetting: ProviderSetting.LocalServer,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<StreamChunk> = delegate.streamText(providerSetting.asOpenAI(), messages, params)
}
