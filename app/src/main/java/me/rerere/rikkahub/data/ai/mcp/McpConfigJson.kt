package me.rerere.rikkahub.data.ai.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * MCP 配置的导出与导入。
 *
 * 导出文件顶层是 `mcpServers`，与本应用导入弹窗接受的标准格式同名；
 * 本应用导出的是数组，能保留 enable、headers 和工具开关，同名服务器也不会互相覆盖：
 *
 * ```json
 * {"version": 1, "mcpServers": [{"name": "...", "type": "sse", "url": "...", ...}]}
 * ```
 *
 * 导入同时接受外部工具常见的对象映射格式：
 * `{"mcpServers": {"name": {"type": "sse", "url": "...", "headers": {...}}}}`。
 *
 * OAuth 令牌不写进导出文件：令牌与当前设备绑定，换设备后本来就要重新授权，
 * 导出只会多留一份凭据副本。
 */
const val MCP_CONFIG_EXPORT_VERSION = 1

private val McpConfigJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
private data class McpServersDocument(
    val version: Int = MCP_CONFIG_EXPORT_VERSION,
    val mcpServers: List<McpServerEntry>,
)

@Serializable
private data class McpServerEntry(
    val name: String,
    val type: String,
    val url: String,
    val enable: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
    val tools: List<McpToolEntry> = emptyList(),
)

@Serializable
private data class McpToolEntry(
    val name: String,
    val enable: Boolean = true,
    val needsApproval: Boolean = false,
)

/** 把全部 MCP 配置导出为可再次导入的 JSON。 */
fun exportMcpServersToJson(servers: List<McpServerConfig>): String {
    return McpConfigJson.encodeToString(
        McpServersDocument.serializer(),
        McpServersDocument(mcpServers = servers.map { it.toExportEntry() }),
    )
}

/**
 * 解析 MCP 配置 JSON，支持本应用的导出格式和标准的 mcpServers 对象格式。
 *
 * @throws IllegalArgumentException JSON 结构不认识或缺少 mcpServers 字段
 */
fun parseMcpServersFromJson(json: String): List<McpServerConfig> {
    val root = McpConfigJson.parseToJsonElement(json).jsonObject
    return when (val servers = root["mcpServers"]) {
        null -> throw IllegalArgumentException("mcpServers field is missing")
        is JsonArray -> servers.mapNotNull { it.toExportedConfig() }
        is JsonObject -> servers.entries.mapNotNull { (name, element) -> element.toLegacyConfig(name) }
        else -> throw IllegalArgumentException("mcpServers must be an object or an array")
    }
}

private fun McpServerConfig.toExportEntry(): McpServerEntry = McpServerEntry(
    name = commonOptions.name,
    type = when (this) {
        is McpServerConfig.SseTransportServer -> "sse"
        is McpServerConfig.StreamableHTTPServer -> "streamable_http"
    },
    url = serverUrl,
    enable = commonOptions.enable,
    // 设置页“添加请求头”后没填名字会留下空行，空名头发不出去，也不值得写进导出文件
    headers = commonOptions.headers
        .filter { it.first.isNotBlank() }
        .associate { it.first to it.second },
    // 工具 schema 会在连接时重新同步，导出只保留名字和开关，免得文件被 schema 撑爆
    tools = commonOptions.tools
        .filter { it.name.isNotBlank() }
        .map { tool ->
            McpToolEntry(
                name = tool.name,
                enable = tool.enable,
                needsApproval = tool.needsApproval,
            )
        },
)

private fun JsonElement.toExportedConfig(): McpServerConfig? {
    val obj = this as? JsonObject ?: return null
    val url = obj.string("url")?.takeIf { it.isNotBlank() } ?: return null
    val tools = (obj["tools"] as? JsonArray).orEmpty().mapNotNull { element ->
        val tool = element as? JsonObject ?: return@mapNotNull null
        val name = tool.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        McpTool(
            enable = tool.boolean("enable") ?: true,
            name = name,
            needsApproval = tool.boolean("needsApproval") ?: false,
        )
    }
    return createConfig(
        type = obj.string("type") ?: "streamable_http",
        name = obj.string("name").orEmpty(),
        url = url,
        enable = obj.boolean("enable") ?: true,
        headers = obj["headers"].toHeaderPairs(),
        tools = tools,
    )
}

private fun JsonElement.toLegacyConfig(name: String): McpServerConfig? {
    val obj = this as? JsonObject ?: return null
    val url = obj.string("url")?.takeIf { it.isNotBlank() } ?: return null
    return createConfig(
        type = obj.string("type") ?: "streamable_http",
        name = name,
        url = url,
        enable = true,
        headers = obj["headers"].toHeaderPairs(),
        tools = emptyList(),
    )
}

private fun createConfig(
    type: String,
    name: String,
    url: String,
    enable: Boolean,
    headers: List<Pair<String, String>>,
    tools: List<McpTool>,
): McpServerConfig {
    val commonOptions = McpCommonOptions(
        enable = enable,
        name = name,
        headers = headers,
        tools = tools,
    )
    return when (type) {
        "sse" -> McpServerConfig.SseTransportServer(commonOptions = commonOptions, url = url)
        else -> McpServerConfig.StreamableHTTPServer(commonOptions = commonOptions, url = url)
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonElement?.toHeaderPairs(): List<Pair<String, String>> {
    val obj = this as? JsonObject ?: return emptyList()
    return obj.entries.mapNotNull { (name, value) ->
        if (name.isBlank()) return@mapNotNull null
        name to ((value as? JsonPrimitive)?.content.orEmpty())
    }
}
