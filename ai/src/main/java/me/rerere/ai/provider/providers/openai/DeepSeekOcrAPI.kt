package me.rerere.ai.provider.providers.openai

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.configureReferHeaders
import me.rerere.ai.util.configureSessionHeaders
import me.rerere.ai.util.encodeBase64
import me.rerere.ai.util.json
import me.rerere.ai.util.mergeCustomHeaders
import me.rerere.common.http.await
import me.rerere.common.http.jsonObjectOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "DeepSeekOcrAPI"

private val OCR_BOX_BLOCK = Regex("<[|｜]box[|｜]>.*?<[|｜]/box[|｜]>", RegexOption.DOT_MATCHES_ALL)
private val OCR_LAYOUT_TOKEN = Regex("<[|｜]/?[^|｜>]+[|｜]>")
private val OCR_BLANK_LINES = Regex("\n{3,}")

/**
 * 逐页识别会带 `<|ref|>标题<|/ref|><|box|>[[x1, y1, x2, y2]]<|/box|>` 这类版面标记。
 * OCR 辅助模型的输出会替换原图进入对话上下文，坐标和标记对下游模型没有用处，这里只留文字。
 */
internal fun cleanDeepSeekOcrText(raw: String): String = raw.replace(OCR_BOX_BLOCK, "")
    .replace(OCR_LAYOUT_TOKEN, "")
    .replace(OCR_BLANK_LINES, "\n\n")
    .trim()

/**
 * DeepSeek OCR：`POST /v1/tools/ocr` 是 multipart 接口，和 Chat Completions 完全不同。
 *
 * L3Hub 的 OCR 辅助模型走的是一套「模型」入口（模型类型 [me.rerere.ai.provider.ModelType.OCR]），
 * 这里把用户消息里的第一张图片取出来上传，再把逐页文本拼成一条 assistant 消息返回，
 * 这样 OCR 缓存、并发限制、结果固化等现有逻辑都不用改。
 */
class DeepSeekOcrAPI(
    private val client: OkHttpClient,
    private val keyRoulette: KeyRoulette,
) {
    suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): TextGenerationResult = withContext(Dispatchers.IO) {
        val image = messages.firstImage()
            ?: error("DeepSeek OCR needs an image message, but none was provided")
        val upload = image.readUpload()
        val key = keyRoulette.next(providerSetting.apiKey, providerSetting.id.toString())

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("purpose", "text_recognition")
            .addFormDataPart(
                "file",
                upload.fileName,
                upload.bytes.toRequestBody(upload.mimeType.toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(deepSeekOcrUrl(providerSetting.baseUrl))
            .headers(providerSetting.mergeCustomHeaders(params.customHeaders))
            .addHeader("Authorization", "Bearer $key")
            .configureReferHeaders(providerSetting.baseUrl)
            .configureSessionHeaders(providerSetting.baseUrl, params.sessionId)
            .post(multipart)
            .build()

        Log.i(TAG, "generateText: ${upload.fileName} (${upload.bytes.size} bytes, ${upload.mimeType})")

        val response = client.newCall(request).await()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw Exception("DeepSeek OCR failed: ${response.code} $body")
        }

        val root = runCatching { json.parseToJsonElement(body.trim()).jsonObject }.getOrElse {
            throw Exception("DeepSeek OCR returned invalid JSON: ${body.take(500)}")
        }
        root["error"]?.jsonObjectOrNull?.get("message")?.jsonPrimitive?.contentOrNull?.let { message ->
            throw Exception("DeepSeek OCR failed: $message")
        }

        val pages = root["pages"]?.jsonArray
            ?: throw Exception("DeepSeek OCR response has no pages: ${body.take(500)}")
        val truncated = pages.any { page ->
            page.jsonObject["finish_reason"]?.jsonPrimitive?.contentOrNull == "length"
        }
        val text = buildString {
            pages.forEachIndexed { index, page ->
                if (index > 0) append("\n\n")
                append(page.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
            }
            if (truncated) {
                if (isNotEmpty()) append("\n\n")
                append("[OCR output truncated: the page reached the output limit]")
            }
        }.trim().let(::cleanDeepSeekOcrText)

        val usage = root["usage"]?.jsonObjectOrNull?.let { usage ->
            val input = usage["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            val output = usage["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            TokenUsage(
                promptTokens = input,
                completionTokens = output,
                totalTokens = usage["total_tokens"]?.jsonPrimitive?.intOrNull ?: (input + output),
            )
        }

        TextGenerationResult(
            id = root["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            model = params.model.modelId,
            message = UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text(text)),
            ),
            finishReason = "stop",
            usage = usage,
        )
    }

    fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<StreamChunk> = flow {
        val result = generateText(providerSetting, messages, params)
        val id = result.id.ifBlank { "deepseek-ocr" }
        val text = result.message.toText()
        emit(StreamChunk.TextStart(id))
        emit(StreamChunk.TextDelta(id, text))
        emit(StreamChunk.TextEnd(id))
        result.usage?.let { emit(StreamChunk.Usage(it)) }
        emit(
            StreamChunk.Finish(
                finishReason = result.finishReason,
                responseId = result.id.takeIf { it.isNotBlank() },
                model = result.model,
            )
        )
    }

    private fun List<UIMessage>.firstImage(): UIMessagePart.Image? = asSequence()
        .flatMap { message -> message.parts.asSequence() }
        .filterIsInstance<UIMessagePart.Image>()
        .firstOrNull { it.url.isNotBlank() }

    private fun UIMessagePart.Image.readUpload(): OcrUpload {
        if (url.startsWith("data:")) {
            val header = url.substringAfter("data:").substringBefore(",")
            val mimeType = header.substringBefore(";")
                .takeIf { it.startsWith("image/") }
                ?: "image/png"
            val bytes = Base64.decode(url.substringAfter(","), Base64.DEFAULT)
            return OcrUpload(bytes, "image.${mimeType.substringAfter('/')}", mimeType)
        }
        if (url.startsWith("file:")) {
            // 走与聊天图片相同的压缩/EXIF 归一化流程：DeepSeek 不认 HEIC，原图也可能过大
            val normalized = if (url.startsWith("file://")) {
                this
            } else {
                copy(url = "file://" + url.removePrefix("file:"))
            }
            val encoded = normalized.encodeBase64(withPrefix = false).getOrThrow()
            val bytes = Base64.decode(encoded.base64, Base64.DEFAULT)
            return OcrUpload(
                bytes = bytes,
                fileName = "image.${encoded.mimeType.substringAfter('/')}",
                mimeType = encoded.mimeType,
            )
        }
        throw IllegalArgumentException("DeepSeek OCR only supports local files and data URLs: $url")
    }

    private fun deepSeekOcrUrl(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return if (base.endsWith("/v1")) "$base/tools/ocr" else "$base/v1/tools/ocr"
    }

    private data class OcrUpload(
        val bytes: ByteArray,
        val fileName: String,
        val mimeType: String,
    )
}
