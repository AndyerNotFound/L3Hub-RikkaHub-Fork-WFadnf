package me.rerere.rikkahub.ui.components.ai.completion

import me.rerere.rikkahub.data.workspace.GitIgnoreMatcher
import me.rerere.rikkahub.data.workspace.GitIgnoreRule

/**
 * 补全索引用的 .gitignore 匹配器。
 *
 * 规则解析与匹配语义复用检索工具同一套实现 [GitIgnoreMatcher]，这里只负责：
 * 把补全场景的「相对路径」规则逐目录累积成一份规则列表，并补上默认忽略集。
 * 补全的 directory 与候选 entry.path 都是相对工作区文件区的路径，基准一致，
 * 可以直接交给 GitIgnoreMatcher 按同一基准求值。
 */
internal class WorkspaceIgnoreMatcher private constructor(
    private val rules: List<GitIgnoreRule> = DEFAULT_RULES,
) {
    constructor() : this(DEFAULT_RULES)

    fun withGitignore(directory: String, content: String): WorkspaceIgnoreMatcher {
        val basePath = directory.normalizeWorkspacePath()
        val parsed = GitIgnoreMatcher.parse(basePath, content)
        return if (parsed.isEmpty()) this else WorkspaceIgnoreMatcher(rules + parsed)
    }

    fun isIgnored(path: String, isDirectory: Boolean): Boolean {
        val normalized = path.normalizeWorkspacePath()
        return GitIgnoreMatcher.isIgnored(rules, normalized, isDirectory)
    }

    companion object {
        // 与补全场景一致的基准：规则挂在空基目录上，匹配任意相对路径
        private val DEFAULT_RULES = listOf(
            "build/",
            ".gradle/",
            ".git/",
            "node_modules/",
            "dist/",
            "out/",
        ).let { content -> GitIgnoreMatcher.parse("", content.joinToString("\n")) }
    }
}

private fun String.normalizeWorkspacePath(): String =
    replace('\\', '/').trim().trim('/')
