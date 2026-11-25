package me.rerere.rikkahub.ui.components.richtext

/**
 * 代码围栏语言的归一化与 diff 识别。
 *
 * 模型经常把语言写成 `language-kotlin`、`{.json}`、`application/json` 或者带上额外参数
 * (`python title=main.py`)，这些都不该影响最终选用的渲染器。归一化只做展示层的判断，
 * 原始源码和语言标签始终原样保留给复制、源码视图和导出使用。
 */
internal fun normalizeCodeFenceLanguage(language: String): String = language
    .trim()
    .lowercase()
    .removePrefix("language-")
    .removePrefix("{")
    .removeSuffix("}")
    .removePrefix(".")
    .substringBefore(' ')
    .substringBefore(',')
    .substringBefore(';')
    .let {
        when (it) {
            "application/json", "text/json" -> "json"
            "application/xml", "text/xml" -> "xml"
            "image/svg+xml" -> "svg"
            "application/geo+json", "application/vnd.geo+json", "geo-json" -> "geojson"
            "sh", "shell", "zsh", "bash" -> "shell"
            "yml" -> "yaml"
            "kt", "kts" -> "kotlin"
            "py" -> "python"
            "rs" -> "rust"
            else -> it
        }
    }

/** 明确声明为补丁的语言别名。 */
internal val DIFF_FENCE_LANGUAGES = setOf(
    "diff",
    "patch",
    "udiff",
    "unified-diff",
    "git-diff",
    "gitdiff",
)

internal fun isDiffFenceLanguage(language: String): Boolean =
    normalizeCodeFenceLanguage(language) in DIFF_FENCE_LANGUAGES

/** 未声明语言或声明为纯文本时，用它判断内容本身是不是一段 unified diff。 */
private val GENERIC_FENCE_LANGUAGES = setOf("", "text", "plaintext", "plain", "txt", "log")

internal fun looksLikeUnifiedDiff(code: String): Boolean {
    var hasOldFile = false
    var hasNewFile = false
    var hasHunk = false
    var hasChange = false
    code.lineSequence().take(MAX_DIFF_DETECT_LINES).forEach { rawLine ->
        // 模型常把补丁缩进放进普通段落里，忽略这层展示缩进，但保留 diff 自身的 +/- 标记。
        val line = rawLine.removeSuffix("\r").trimStart()
        when {
            line.startsWith("diff --git ") -> hasHunk = true
            line.startsWith("--- ") -> hasOldFile = true
            line.startsWith("+++ ") -> hasNewFile = true
            line.startsWith("@@ ") || line.startsWith("@@-") -> hasHunk = true
            line.startsWith('+') && !line.startsWith("+++") -> hasChange = true
            line.startsWith('-') && !line.startsWith("---") -> hasChange = true
        }
    }
    return hasChange && (hasHunk || (hasOldFile && hasNewFile))
}

/**
 * 内容自身就足以说明是补丁：首个非空行是 diff 头。
 * 正文里顺带提到 `- 列表项` 之类的普通文本不会被当成补丁。
 */
internal fun looksLikeStandaloneUnifiedDiff(code: String): Boolean {
    val firstLine = code.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart() ?: return false
    return (firstLine.startsWith("diff --git ") ||
        firstLine.startsWith("--- ") ||
        firstLine.startsWith("@@ ") ||
        firstLine.startsWith("@@-")) &&
        looksLikeUnifiedDiff(code)
}

/**
 * 是否用原生 diff 视图渲染这个代码块。
 *
 * 显式声明优先；泛型围栏只在内容明显是补丁时才转换，避免误判普通文本。
 */
internal fun shouldRenderAsDiff(language: String, code: String): Boolean {
    if (code.length > MAX_DIFF_SOURCE_CHARS) return false
    if (isDiffFenceLanguage(language)) return true
    return normalizeCodeFenceLanguage(language) in GENERIC_FENCE_LANGUAGES &&
        looksLikeStandaloneUnifiedDiff(code)
}

/** 与 diff 视图同源的增删行数，供折叠标题显示。 */
internal fun diffLineStats(code: String): Pair<Int, Int> {
    var additions = 0
    var deletions = 0
    code.lineSequence().forEach { rawLine ->
        val line = rawLine.trimStart()
        when {
            line.startsWith("+++") || line.startsWith("---") -> Unit
            line.startsWith("+") -> additions++
            line.startsWith("-") -> deletions++
        }
    }
    return additions to deletions
}

private const val MAX_DIFF_DETECT_LINES = 200
internal const val MAX_DIFF_SOURCE_CHARS = 256 * 1024
