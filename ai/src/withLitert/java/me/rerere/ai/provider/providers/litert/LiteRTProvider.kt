package me.rerere.ai.provider.providers.litert

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.LiteRTBackend
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.io.File
import java.util.concurrent.Executors
import kotlin.uuid.Uuid

/**
 * 应用内 LiteRT-LM 推理。
 *
 * 引擎按模型文件与后端缓存，同一时间只跑一个会话（多步生成、标题、压缩会排队），
 * 所有原生调用都固定在同一个单线程上，符合 LiteRT 对线程的要求。
 */
class LiteRTProvider(context: Context) : Provider<ProviderSetting.LiteRT> {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litert-lm").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val mutex = Mutex()
    private var cachedEngine: CachedEngine? = null

    private data class CachedEngine(val key: String, val engine: Engine)

    override suspend fun listModels(providerSetting: ProviderSetting.LiteRT): List<Model> =
        providerSetting.models

    override suspend fun generateText(
        providerSetting: ProviderSetting.LiteRT,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): TextGenerationResult {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        streamText(providerSetting, messages, params).collect { chunk ->
            when (chunk) {
                is StreamChunk.TextDelta -> text.append(chunk.text)
                is StreamChunk.ReasoningDelta -> reasoning.append(chunk.text)
                else -> Unit
            }
        }
        val parts = buildList {
            if (reasoning.isNotEmpty()) {
                add(UIMessagePart.Reasoning(reasoning = reasoning.toString()))
            }
            add(UIMessagePart.Text(text.toString()))
        }
        return TextGenerationResult(
            id = Uuid.random().toString(),
            model = params.model.modelId,
            message = UIMessage(
                role = MessageRole.ASSISTANT,
                parts = parts,
                modelId = params.model.id,
            ),
            finishReason = "stop",
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.LiteRT,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<StreamChunk> = flow {
        require(providerSetting.modelPath.isNotBlank()) { "尚未导入 LiteRT 模型文件" }
        require(File(providerSetting.modelPath).isFile) {
            "模型文件不存在：${providerSetting.modelPath}"
        }
        require(messages.isNotEmpty()) { "消息为空" }

        val systemInstruction = messages
            .filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n\n") { it.textContent() }
            .trim()
        // 本地模型的对话模板通常要求 user/model 交替；连续同角色先合并再交给运行时
        val history = messages
            .dropLast(1)
            .filter { it.role != MessageRole.SYSTEM }
            .fold(mutableListOf<Pair<MessageRole, String>>()) { acc, message ->
                val text = message.textContent()
                val last = acc.lastOrNull()
                if (last != null && last.first == message.role && message.role != MessageRole.TOOL) {
                    acc[acc.lastIndex] = last.first to (last.second + "\n\n" + text)
                } else {
                    acc.add(message.role to text)
                }
                acc
            }
            .filter { it.second.isNotBlank() }
            .mapNotNull { (role, text) ->
                when (role) {
                    MessageRole.USER -> Message.user(text)
                    MessageRole.ASSISTANT -> Message.model(text)
                    MessageRole.TOOL -> Message.user("工具结果：\n$text")
                    MessageRole.SYSTEM -> null
                }
            }
        val lastContents = messages.last().let { last ->
            last.liteRTContents(providerSetting.visionEnabled)
                .ifEmpty { listOf(Content.Text(" ")) }
        }

        mutex.withLock {
            val engine = ensureEngine(providerSetting)
            val conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = systemInstruction
                        .takeIf { it.isNotBlank() }
                        ?.let { Contents.of(it) },
                    initialMessages = history,
                    samplerConfig = SamplerConfig(
                        topK = (params.topK ?: providerSetting.topK).coerceAtLeast(1),
                        topP = (params.topP ?: providerSetting.topP).toDouble().coerceIn(0.0, 1.0),
                        temperature = (params.temperature ?: providerSetting.temperature)
                            .toDouble()
                            .coerceAtLeast(0.0),
                    ),
                    maxOutputToken = (params.maxTokens ?: providerSetting.maxOutputTokens)
                        .coerceAtLeast(1),
                    thinkingConfig = ThinkingConfig(
                        enableThinking = providerSetting.enableThinking,
                        thinkingTokenBudget = providerSetting.thinkingBudget,
                    ),
                )
            )

            val textId = "litert-text"
            val reasoningId = "litert-reasoning"
            val textBuffer = StringBuilder()
            val reasoningBuffer = StringBuilder()
            var textStarted = false
            var reasoningStarted = false

            try {
                conversation.sendMessageAsync(Contents.of(lastContents)).collect { message ->
                    val thought = message.channels["thought"].orEmpty()
                    if (thought.isNotEmpty()) {
                        if (!reasoningStarted) {
                            reasoningStarted = true
                            emit(StreamChunk.ReasoningStart(reasoningId))
                        }
                        emit(StreamChunk.ReasoningDelta(reasoningId, deltaOf(reasoningBuffer, thought)))
                    }
                    val text = message.contents.toString()
                    if (text.isNotEmpty()) {
                        if (!textStarted) {
                            textStarted = true
                            emit(StreamChunk.TextStart(textId))
                        }
                        emit(StreamChunk.TextDelta(textId, deltaOf(textBuffer, text)))
                    }
                }
                if (reasoningStarted) emit(StreamChunk.ReasoningEnd(reasoningId))
                if (textStarted) emit(StreamChunk.TextEnd(textId))
                emit(StreamChunk.Finish(finishReason = "stop", model = params.model.modelId))
            } finally {
                withContext(NonCancellable) {
                    runCatching { withContext(dispatcher) { conversation.cancelProcess() } }
                    runCatching { withContext(dispatcher) { conversation.close() } }
                }
            }
        }
    }.flowOn(dispatcher)

    private fun ensureEngine(providerSetting: ProviderSetting.LiteRT): Engine {
        val key = listOf(
            providerSetting.modelPath,
            providerSetting.backend.name,
            providerSetting.cpuThreads,
            providerSetting.contextTokens,
            providerSetting.visionEnabled,
        ).joinToString("|")
        cachedEngine?.takeIf { it.key == key }?.let { return it.engine }

        cachedEngine?.engine?.let { old -> runCatching { old.close() } }
        cachedEngine = null

        val cacheDir = File(appContext.cacheDir, "litertlm").apply { mkdirs() }
        val engine = Engine(
            EngineConfig(
                modelPath = providerSetting.modelPath,
                backend = providerSetting.nativeBackend(),
                visionBackend = if (providerSetting.visionEnabled) {
                    providerSetting.nativeBackend()
                } else {
                    null
                },
                maxNumTokens = providerSetting.contextTokens.takeIf { it > 0 },
                cacheDir = cacheDir.absolutePath,
            )
        )
        engine.initialize()
        cachedEngine = CachedEngine(key, engine)
        return engine
    }
}

private fun ProviderSetting.LiteRT.nativeBackend(): Backend = when (backend) {
    LiteRTBackend.CPU -> Backend.CPU(threadCount = cpuThreads.takeIf { it > 0 })
    LiteRTBackend.GPU -> Backend.GPU()
}

private fun UIMessage.textContent(): String =
    parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }

private fun UIMessage.liteRTContents(visionEnabled: Boolean): List<Content> {
    val contents = mutableListOf<Content>()
    parts.forEach { part ->
        when (part) {
            is UIMessagePart.Text -> if (part.text.isNotEmpty()) {
                contents += Content.Text(part.text)
            }

            is UIMessagePart.Image -> if (visionEnabled) {
                part.localImageBytes()?.let { contents += Content.ImageBytes(it) }
            }

            else -> Unit
        }
    }
    return contents
}

private fun UIMessagePart.Image.localImageBytes(): ByteArray? {
    val file = when {
        url.startsWith("file://") -> Uri.parse(url).path?.let(::File)
        url.startsWith("content://") -> null
        url.startsWith("data:") -> null
        else -> File(url)
    } ?: return null
    return runCatching { file.takeIf { it.isFile }?.readBytes() }.getOrNull()
}

/**
 * 流式分片可能是增量，也可能是截至当前的完整文本；按前缀关系取新增部分，两种都能处理。
 */
private fun deltaOf(buffer: StringBuilder, incoming: String): String {
    val current = buffer.toString()
    val delta = if (incoming.length > current.length && incoming.startsWith(current)) {
        incoming.substring(current.length)
    } else {
        incoming
    }
    buffer.append(delta)
    return delta
}
