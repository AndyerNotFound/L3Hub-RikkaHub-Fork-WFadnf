package me.rerere.ai.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

/**
 * 用一条 cURL 命令覆写提供商的聊天请求。
 *
 * [curl] 是模板：先替换 `{{变量}}`，再按 shell 规则拆成 curl 参数。支持
 * `{{apiKey}}`、`{{baseUrl}}`、`{{model}}`、`{{stream}}`、`{{body}}`（App 生成的完整请求体）、
 * `{{messages}}`、`{{tools}}`。请求头和请求体都可以引用这些变量，例如：
 *
 * ```
 * curl '{{baseUrl}}/chat/completions' \
 *   -H 'Authorization: Bearer {{apiKey}}' \
 *   -H 'Content-Type: application/json' \
 *   -d '{"model":"{{model}}","stream":{{stream}},"messages":{{messages}}}'
 * ```
 *
 * 响应仍按提供商自己的协议解析；OCR、生图和余额查询不受影响。
 */
@Serializable
data class RequestOverride(
    val enabled: Boolean = false,
    val curl: String = "",
) {
    val isActive: Boolean get() = enabled && curl.isNotBlank()

    companion object {
        val VARIABLES = listOf("apiKey", "baseUrl", "model", "stream", "body", "messages", "tools")
    }
}

/** 把请求覆写所需的上下文打包给共用的请求构建器 */
data class RequestOverrideContext(
    val guard: ProviderGuard,
    val apiKey: String,
    val baseUrl: String,
)

/** 解析后的 cURL 命令 */
data class CurlCommand(
    val url: String,
    val method: String,
    val headers: List<CustomHeader>,
    val body: String?,
) {
    companion object {
        fun parse(command: String): CurlCommand {
            val tokens = tokenize(command)
            var method: String? = null
            var url: String? = null
            var basicAuth: String? = null
            var cookie: String? = null
            var userAgent: String? = null
            var referer: String? = null
            val headers = mutableListOf<CustomHeader>()
            val data = mutableListOf<String>()
            var jsonMode = false
            var index = 0

            fun takeValue(flag: String): String {
                val value = tokens.getOrNull(index + 1)
                    ?: throw IllegalArgumentException("$flag 缺少参数")
                index += 1
                return value
            }

            while (index < tokens.size) {
                val token = tokens[index]
                when {
                    token == "curl" || token.endsWith("/curl") -> Unit
                    token.startsWith("--") -> {
                        val name = token.substringBefore('=')
                        val inline = token.substringAfter('=', "").takeIf { '=' in token }
                        when (name) {
                            "--url" -> url = inline ?: takeValue(name)
                            "--request" -> method = (inline ?: takeValue(name)).uppercase()
                            "--header" -> headers += parseHeader(inline ?: takeValue(name))
                            "--data", "--data-raw", "--data-binary", "--data-urlencode", "--json" -> {
                                val value = inline ?: takeValue(name)
                                if (name == "--json") jsonMode = true
                                checkData(value)
                                data += value
                            }
                            "--user" -> basicAuth = inline ?: takeValue(name)
                            "--cookie" -> cookie = inline ?: takeValue(name)
                            "--user-agent" -> userAgent = inline ?: takeValue(name)
                            "--referer" -> referer = inline ?: takeValue(name)
                            "--form", "--form-string" -> throw IllegalArgumentException("不支持 -F/--form 表单上传")
                            else -> {
                                // 其他参数（--compressed、--insecure、--location 等）不影响请求内容，直接跳过
                            }
                        }
                    }
                    token.startsWith("-") && token.length > 1 -> {
                        when (val flag = token.substring(0, 2)) {
                            "-X" -> method = (token.substring(2).takeIf { it.isNotEmpty() } ?: takeValue(flag)).uppercase()
                            "-H" -> headers += parseHeader(token.substring(2).takeIf { it.isNotEmpty() } ?: takeValue(flag))
                            "-d" -> {
                                val value = token.substring(2).takeIf { it.isNotEmpty() } ?: takeValue(flag)
                                checkData(value)
                                data += value
                            }
                            "-u" -> basicAuth = token.substring(2).takeIf { it.isNotEmpty() } ?: takeValue(flag)
                            "-b" -> cookie = token.substring(2).takeIf { it.isNotEmpty() } ?: takeValue(flag)
                            "-A" -> userAgent = token.substring(2).takeIf { it.isNotEmpty() } ?: takeValue(flag)
                            "-e" -> referer = token.substring(2).takeIf { it.isNotEmpty() } ?: takeValue(flag)
                            "-F" -> throw IllegalArgumentException("不支持 -F/--form 表单上传")
                            else -> Unit
                        }
                    }
                    else -> if (url == null) url = token
                }
                index += 1
            }

            val parsedUrl = url?.trim()
                ?: throw IllegalArgumentException("没有找到请求地址")
            if (!parsedUrl.startsWith("http://") && !parsedUrl.startsWith("https://")) {
                throw IllegalArgumentException("请求地址必须以 http:// 或 https:// 开头")
            }
            val parsedMethod = method ?: if (data.isEmpty()) "GET" else "POST"
            val finalHeaders = headers.toMutableList()
            basicAuth?.let { auth ->
                val encoded = Base64.getEncoder().encodeToString(auth.toByteArray())
                finalHeaders += CustomHeader("Authorization", "Basic $encoded")
            }
            cookie?.let { finalHeaders += CustomHeader("Cookie", it) }
            userAgent?.let { finalHeaders += CustomHeader("User-Agent", it) }
            referer?.let { finalHeaders += CustomHeader("Referer", it) }
            val body = data.takeIf { it.isNotEmpty() }?.joinToString("&")
            if (jsonMode && finalHeaders.none { it.name.equals("Content-Type", ignoreCase = true) }) {
                finalHeaders += CustomHeader("Content-Type", "application/json")
            }
            return CurlCommand(
                url = parsedUrl,
                method = parsedMethod,
                headers = finalHeaders,
                body = body,
            )
        }

        private fun checkData(value: String) {
            if (value.startsWith("@")) {
                throw IllegalArgumentException("不支持从文件读取请求体（@file）")
            }
        }

        private fun parseHeader(raw: String): CustomHeader {
            val separator = raw.indexOf(':')
            return if (separator < 0) {
                CustomHeader(raw.trim().removeSuffix(";").trim(), "")
            } else {
                CustomHeader(raw.substring(0, separator).trim(), raw.substring(separator + 1).trim())
            }
        }

        /** 按 shell 规则拆分参数：单双引号、ANSI-C 引号、反斜杠转义和续行 */
        internal fun tokenize(command: String): List<String> {
            // cmd 风格的续行是 ^ + 换行
            val normalized = command.replace(Regex("\\^\\r?\\n"), " ")
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            var ansiQuote = false
            var started = false
            var index = 0
            while (index < normalized.length) {
                val char = normalized[index]
                when {
                    quote == '\'' && !ansiQuote -> {
                        if (char == '\'') quote = null else current.append(char)
                    }
                    quote == '\'' && ansiQuote -> {
                        when {
                            char == '\'' -> {
                                quote = null
                                ansiQuote = false
                            }
                            char == '\\' -> {
                                when (val next = normalized.getOrNull(index + 1)) {
                                    'n' -> { current.append('\n'); index += 1 }
                                    't' -> { current.append('\t'); index += 1 }
                                    'r' -> { current.append('\r'); index += 1 }
                                    '\\' -> { current.append('\\'); index += 1 }
                                    '\'' -> { current.append('\''); index += 1 }
                                    '"' -> { current.append('"'); index += 1 }
                                    else -> current.append(char)
                                }
                            }
                            else -> current.append(char)
                        }
                    }
                    quote == '"' -> {
                        if (char == '"') {
                            quote = null
                        } else if (char == '\\') {
                            val next = normalized.getOrNull(index + 1)
                            when (next) {
                                '"', '\\', '$', '`' -> {
                                    current.append(next)
                                    index += 1
                                }
                                '\n' -> index += 1
                                '\r' -> index += if (normalized.getOrNull(index + 2) == '\n') 2 else 1
                                else -> current.append(char)
                            }
                        } else {
                            current.append(char)
                        }
                    }
                    char == '$' && normalized.getOrNull(index + 1) == '\'' -> {
                        quote = '\''
                        ansiQuote = true
                        started = true
                        index += 1
                    }
                    char == '\'' || char == '"' -> {
                        quote = char
                        started = true
                    }
                    char == '\\' -> {
                        val next = normalized.getOrNull(index + 1)
                        when {
                            next == '\n' -> index += 1
                            next == '\r' && normalized.getOrNull(index + 2) == '\n' -> index += 2
                            next != null -> {
                                current.append(next)
                                index += 1
                                started = true
                            }
                        }
                    }
                    char.isWhitespace() -> {
                        if (started) {
                            tokens += current.toString()
                            current.clear()
                            started = false
                        }
                    }
                    else -> {
                        current.append(char)
                        started = true
                    }
                }
                index += 1
            }
            if (quote != null) throw IllegalArgumentException("引号没有闭合")
            if (started) tokens += current.toString()
            return tokens
        }
    }
}

private val CURL_VARIABLE = Regex("""\{\{\s*(\w+)\s*\}\}""")

/** 替换 cURL 模板里的 `{{name}}`，引用了未知变量时抛错，避免把拼写错误静默发出去 */
fun renderCurlTemplate(template: String, variable: (String) -> String?): String =
    CURL_VARIABLE.replace(template) { match ->
        val name = match.groupValues[1]
        variable(name) ?: throw IllegalArgumentException("未知变量 {{$name}}")
    }

/** 一次请求里可用的模板变量 */
fun requestOverrideVariables(
    apiKey: String,
    baseUrl: String,
    model: String,
    body: JsonObject,
    stream: Boolean,
): Map<String, String> = buildMap {
    put("apiKey", apiKey)
    put("baseUrl", baseUrl)
    put("model", model)
    put("stream", stream.toString())
    put("body", body.toString())
    put("messages", (body["messages"] ?: body["contents"] ?: body["input"])?.toString() ?: "[]")
    put("tools", body["tools"]?.toString() ?: "[]")
}

/**
 * 用 [ProviderGuard.requestOverride] 覆写已经构建好的请求。
 *
 * 原有请求头（鉴权、代理、会话头）会保留，cURL 里的同名请求头覆盖它们；地址、方法和请求体以 cURL 为准。
 */
fun Request.applyRequestOverride(
    guard: ProviderGuard,
    apiKey: String,
    baseUrl: String,
    model: String,
    body: JsonObject,
    stream: Boolean,
): Request {
    val override = guard.requestOverride
    if (!override.isActive) return this
    val variables = requestOverrideVariables(apiKey, baseUrl, model, body, stream)
    val command = CurlCommand.parse(renderCurlTemplate(override.curl) { variables[it] })
    val method = command.method.uppercase()
    val contentType = command.headers
        .firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }
        ?.value
        ?.takeIf { it.isNotBlank() }
        ?: "application/json"
    val requestBody: RequestBody? = command.body?.toRequestBody(contentType.toMediaType())
    val builder = newBuilder().url(command.url)
    command.headers
        .filter { it.name.isNotBlank() }
        .forEach { builder.header(it.name.trim(), it.value) }
    return when (method) {
        "GET", "HEAD" -> builder.method(method, null).build()
        else -> builder.method(method, requestBody ?: ByteArray(0).toRequestBody(null)).build()
    }
}

/** 检查 cURL 模板能否解析；返回 null 表示没问题 */
fun RequestOverride.validate(): String? {
    if (!isActive) return null
    return runCatching {
        val body = JsonObject(
            mapOf(
                "model" to kotlinx.serialization.json.JsonPrimitive("test-model"),
                "stream" to kotlinx.serialization.json.JsonPrimitive(true),
                "messages" to kotlinx.serialization.json.JsonArray(emptyList()),
            )
        )
        val variables = requestOverrideVariables(
            apiKey = "sk-test",
            baseUrl = "https://example.com/v1",
            model = "test-model",
            body = body,
            stream = true,
        )
        CurlCommand.parse(renderCurlTemplate(curl) { variables[it] })
        null
    }.exceptionOrNull()?.message ?: "无法解析"
}

/** 供界面展示的变量说明 */
fun RequestOverride.variableHint(): String =
    RequestOverride.VARIABLES.joinToString("、") { "{{$it}}" }
