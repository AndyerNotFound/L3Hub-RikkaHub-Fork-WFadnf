package me.rerere.ai.provider.providers.litert

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage

/**
 * 精简版（`-PnoLitert=true`）里的占位实现。
 *
 * 精简版不打包 LiteRT-LM 运行时，完整实现放在 `src/withLitert` 下，构建时二选一。
 * 保留这个类，是为了让 Provider 注册表、设置反序列化和已存的 `ProviderSetting.LiteRT`
 * 配置在完整版与精简版之间互通——装回完整版后原来的本地模型配置还能直接用。
 * 真正发起请求时明确报错，不去碰不存在的原生库。
 */
class LiteRTProvider(@Suppress("UNUSED_PARAMETER") context: Context) :
    Provider<ProviderSetting.LiteRT> {

    override suspend fun listModels(providerSetting: ProviderSetting.LiteRT): List<Model> =
        providerSetting.models

    override suspend fun generateText(
        providerSetting: ProviderSetting.LiteRT,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): TextGenerationResult = error(UNAVAILABLE_MESSAGE)

    override suspend fun streamText(
        providerSetting: ProviderSetting.LiteRT,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<StreamChunk> = flow { error(UNAVAILABLE_MESSAGE) }

    companion object {
        const val UNAVAILABLE_MESSAGE = "当前安装的是不含 LiteRT 运行时的精简版，本地模型不可用；换装完整版即可恢复"
    }
}
