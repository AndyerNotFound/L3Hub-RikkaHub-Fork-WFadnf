package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderException
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "DeepSeekTTSProvider"
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * DeepSeek TTS：POST /v1/tools/tts，一次请求返回整段音频。
 *
 * 请求体是 `{text, voice_id, format, filter_markdown}`，文本上限 10,000 个计费字符，
 * 音频格式只有 wav（默认）与 pcm（24 kHz / 16 bit / 单声道）。语音按文本自动识别，
 * 没有语种参数，也不支持调节语速。
 */
class DeepSeekTTSProvider : TTSProvider<TTSProviderSetting.DeepSeek> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.DeepSeek,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val format = providerSetting.format.trim().lowercase().let {
            if (it == "pcm") "pcm" else "wav"
        }
        val body = buildJsonObject {
            put("text", request.text)
            put("voice_id", providerSetting.voiceId)
            put("format", format)
            put("filter_markdown", providerSetting.filterMarkdown)
        }

        val httpRequest = Request.Builder()
            .url(deepSeekToolsUrl(providerSetting.baseUrl, "tts"))
            .addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", if (format == "pcm") "audio/pcm" else "audio/wav")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        Log.i(TAG, "generateSpeech: voice=${providerSetting.voiceId} format=$format chars=${request.text.length}")

        val response = httpClient.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = runCatching { response.body?.string() }.getOrNull().orEmpty()
            throw TTSProviderException(
                message = "DeepSeek TTS request failed: HTTP ${response.code} ${response.message}. body=$errorBody",
                statusCode = response.code
            )
        }

        val audioBytes = response.body?.bytes()
            ?: throw TTSProviderException("DeepSeek TTS returned an empty body", response.code)
        if (audioBytes.isEmpty()) {
            throw TTSProviderException("DeepSeek TTS returned 0 bytes", response.code)
        }

        emit(
            AudioChunk(
                data = audioBytes,
                format = if (format == "pcm") AudioFormat.PCM else AudioFormat.WAV,
                sampleRate = if (format == "pcm") 24_000 else null,
                isLast = true,
                metadata = mapOf(
                    "provider" to "deepseek",
                    "voice" to providerSetting.voiceId,
                    "format" to format,
                )
            )
        )
    }
}

/** baseUrl 一般已经带 /v1；用户把它改成裸域名时补上，避免请求打到 /tools/tts */
internal fun deepSeekToolsUrl(baseUrl: String, tool: String): String {
    val base = baseUrl.trim().trimEnd('/')
    return if (base.endsWith("/v1")) "$base/tools/$tool" else "$base/v1/tools/$tool"
}
