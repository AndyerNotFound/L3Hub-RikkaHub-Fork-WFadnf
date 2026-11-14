package me.rerere.rikkahub.ui.components.message.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import me.rerere.rikkahub.data.skills.SkillFrontmatterParser
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.JsonInstantPretty

/** 工具结果或文件正文在详情面板里的展示形态 */
enum class ToolTextFormat {
    /** JSON: 格式化后按 json 语法高亮 */
    Json,

    /** Markdown: 默认渲染成富文本, 可切回源码 */
    Markdown,

    /** 其它代码: 按语言语法高亮 */
    Code,

    /** 纯文本: 按正文排版, 可选中 */
    Plain,
}

/**
 * 一段文本该怎么展示
 *
 * @property text 原始文本, 切到源码时显示这份
 * @property richText 渲染成富文本时用的正文, Markdown 会去掉 frontmatter
 * @property language 语法高亮语言, 非代码时为 plaintext
 */
data class ToolTextLayout(
    val format: ToolTextFormat,
    val text: String,
    val richText: String = text,
    val language: String = "plaintext",
)

/**
 * 详情面板的文本形态识别, 与 Compose 无关, 便于单测
 *
 * 显式给了语言 (文件扩展名、工具约定) 时按语言来; 没给时才看内容:
 * 先试 JSON, 再按 Markdown 特征判断, 都不是就当纯文本. 宁可少认成 Markdown,
 * 也不要让 shell 输出或日志被排版成标题和列表.
 */
object ToolTextPresentation {
    /** 超过这个长度的 Markdown 默认只显示源码, 避免详情面板一次排版几万行 */
    const val MARKDOWN_AUTO_RENDER_MAX_CHARS: Int = 60_000

    private val MARKDOWN_LANGUAGES = setOf("markdown", "md")

    // 出现这些就基本可以确定是 Markdown: 代码围栏、表格分隔行、frontmatter
    private val FENCED_CODE = Regex("(?m)^\\s*```")
    private val TABLE_SEPARATOR = Regex("(?m)^\\s*\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$")
    private val FRONTMATTER = Regex("^---\\r?\\n[\\s\\S]*?\\r?\\n---(?:\\r?\\n|$)")

    // 单独出现时不够确定, 至少凑够两种才当作 Markdown
    private val HEADING = Regex("(?m)^ {0,3}#{1,6}\\s+\\S")
    private val BULLET = Regex("(?m)^\\s*(?:[-*+]|\\d+[.)])\\s+\\S")
    private val QUOTE = Regex("(?m)^\\s*>\\s+\\S")
    private val LINK = Regex("\\[[^\\]\\n]+\\]\\([^)\\s]+\\)")
    private val BOLD = Regex("\\*\\*[^*\\n]+\\*\\*")
    private val INLINE_CODE = Regex("`[^`\\n]+`")

    /** 按显式语言解释 [text]; [languageHint] 为 null 时才按内容猜 */
    fun of(text: String, languageHint: String? = null): ToolTextLayout {
        val hint = languageHint?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return when {
            hint != null && hint in MARKDOWN_LANGUAGES -> markdown(text)
            hint == "json" -> jsonOrCode(text)
            hint != null -> ToolTextLayout(format = ToolTextFormat.Code, text = text, language = hint)
            else -> generic(text)
        }
    }

    /** 文件正文: 优先按路径推断语言, 未知扩展名再退回内容识别 */
    fun forPath(path: String?, text: String): ToolTextLayout = when (val language = syntaxLanguageOf(path)) {
        "markdown" -> markdown(text)
        "json" -> jsonOrCode(text)
        "plaintext" -> generic(text)
        else -> ToolTextLayout(format = ToolTextFormat.Code, text = text, language = language)
    }

    /**
     * 渲染 Markdown 时用的正文
     *
     * 记忆和技能文件以 YAML frontmatter 开头, 直接排版会变成两条分割线加一坨键值;
     * 只有 frontmatter 能被解析时才去掉, 普通的 `---` 分割线不受影响.
     */
    fun markdownBody(content: String): String {
        if (!content.startsWith("---")) return content
        val frontmatter = runCatching { SkillFrontmatterParser.parse(content) }.getOrNull()
        if (frontmatter == null || frontmatter.toMap().isEmpty()) return content
        return runCatching { SkillFrontmatterParser.extractBody(content) }.getOrDefault(content)
    }

    private fun generic(text: String): ToolTextLayout {
        jsonLayout(text)?.let { return it }
        if (looksLikeMarkdown(text)) return markdown(text)
        return ToolTextLayout(format = ToolTextFormat.Plain, text = text)
    }

    private fun jsonOrCode(text: String): ToolTextLayout =
        jsonLayout(text) ?: ToolTextLayout(format = ToolTextFormat.Code, text = text, language = "json")

    private fun jsonLayout(text: String): ToolTextLayout? {
        val first = text.trimStart().firstOrNull()
        if (first != '{' && first != '[') return null
        val element = runCatching { JsonInstant.parseToJsonElement(text) }.getOrNull()
        if (element !is JsonObject && element !is JsonArray) return null
        if (element is JsonObject && element.isEmpty()) return null
        if (element is JsonArray && element.isEmpty()) return null
        val pretty = runCatching { JsonInstantPretty.encodeToString(element) }.getOrNull() ?: return null
        return ToolTextLayout(format = ToolTextFormat.Json, text = pretty, language = "json")
    }

    private fun markdown(text: String): ToolTextLayout = ToolTextLayout(
        format = ToolTextFormat.Markdown,
        text = text,
        richText = markdownBody(text),
        language = "markdown",
    )

    private fun looksLikeMarkdown(text: String): Boolean {
        if (text.isBlank()) return false
        val sample = if (text.length > MARKDOWN_SAMPLE_CHARS) text.take(MARKDOWN_SAMPLE_CHARS) else text
        if (FENCED_CODE.containsMatchIn(sample) ||
            TABLE_SEPARATOR.containsMatchIn(sample) ||
            FRONTMATTER.containsMatchIn(sample)
        ) {
            return true
        }
        var markers = 0
        if (HEADING.containsMatchIn(sample)) markers++
        if (BULLET.containsMatchIn(sample)) markers++
        if (QUOTE.containsMatchIn(sample)) markers++
        if (LINK.containsMatchIn(sample)) markers++
        if (BOLD.containsMatchIn(sample)) markers++
        if (INLINE_CODE.containsMatchIn(sample)) markers++
        return markers >= 2
    }

    private const val MARKDOWN_SAMPLE_CHARS = 20_000
}
