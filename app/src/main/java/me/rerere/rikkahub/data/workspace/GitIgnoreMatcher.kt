package me.rerere.rikkahub.data.workspace

/**
 * 一条 .gitignore 规则。
 *
 * @param baseDir 规则所在目录的绝对路径，末尾不带 '/'（文件系统根写作 "/"）。
 * @param pattern 原始 pattern：已去掉前导 '!'、末尾 '/' 和前导 '/'，保留 '\\' 转义。
 * @param negated '!' 开头的取反规则。
 * @param directoryOnly 末尾带 '/'，只匹配目录。
 * @param anchored pattern 里含 '/'（除末尾）或原本以 '/' 开头，相对 [baseDir] 锚定。
 */
data class GitIgnoreRule(
    val baseDir: String,
    val pattern: String,
    val negated: Boolean,
    val directoryOnly: Boolean,
    val anchored: Boolean,
) {
    private val compiled: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) {
        GlobToRegex.compile(pattern, anchored)
    }

    /** relativePath 相对 [baseDir]，'/' 分隔，不含首尾 '/'。 */
    internal fun matchesRelative(relativePath: String): Boolean = compiled.matches(relativePath)
}

/**
 * .gitignore 规则解析与匹配（纯逻辑，不读文件、不起 git 进程）。
 *
 * ### 匹配域
 * [isIgnored] 接受**绝对路径**，规则用 [GitIgnoreRule.baseDir] 换算成相对路径再匹配，
 * 因此一份调用可以同时用上仓库根和各级子目录的 .gitignore。
 *
 * ### 父目录被忽略 → 子路径也被忽略
 * 采用「逐级祖先重新评估」的策略：[isIgnored] 先把 path 的每一级祖先目录当作
 * `isDirectory = true` 各评估一遍，只要有一级被忽略就直接返回 true；否则才评估 path 自身。
 * 调用方**不需要**自己保证只判断直接子项，直接传任意深度的路径即可。
 *
 * ### 取反规则的 git 限制
 * 因为祖先独立评估，`build/` + `!build/keep.txt` 里的 keep.txt 仍然是忽略的
 * （取反捞不回被忽略目录内的文件，和 git 一致）。
 * 想只排除目录内容、保留个别文件，请写 `build/` 下全部内容再加 `!build/keep.txt`。
 *
 * ### 取舍（与 git 的差异）
 * - '\\' 只当作转义下一个字符，不做 git 的完整转义语义。
 * - 取反字符类 `[!abc]` 额外排除 '/'（单字符类里的 '/' 不特殊处理）。
 * - 不读 `.git/info/exclude`、全局 gitignore、也不处理 `.gitignore` 自身的继承顺序以外的东西。
 * - 大小写敏感；不处理 `core.ignoreCase`。
 * - `**` 只在作为整段（`**` 后接 `/`、`/` 后接 `**`）时跨目录，`a**b` 按 `*` 处理。
 */
object GitIgnoreMatcher {

    /** 解析一个 .gitignore 文件内容；[baseDir] 是该文件所在目录的绝对路径。 */
    fun parse(baseDir: String, content: String): List<GitIgnoreRule> {
        val base = normalizeDir(baseDir)
        val rules = ArrayList<GitIgnoreRule>()
        for (rawLine in content.lineSequence()) {
            val line = trimTrailingSpaces(rawLine)
            // 空行与注释
            if (line.isEmpty() || line.startsWith("#")) continue

            var body = line
            var negated = false
            if (body.startsWith("!")) {
                negated = true
                body = body.substring(1)
            }
            // 末尾 '/' 表示只匹配目录
            var directoryOnly = false
            if (body.endsWith("/") && !body.endsWith("\\/")) {
                directoryOnly = true
                body = body.dropLast(1)
            }
            // 前导 '/' 表示锚定，去掉后不参与匹配
            var anchored = false
            if (body.startsWith("/")) {
                anchored = true
                body = body.drop(1)
            }
            if (body.contains('/')) anchored = true
            if (body.isEmpty()) continue

            rules.add(
                GitIgnoreRule(
                    baseDir = base,
                    pattern = body,
                    negated = negated,
                    directoryOnly = directoryOnly,
                    anchored = anchored,
                )
            )
        }
        return rules
    }

    /**
     * path 是否被忽略。
     *
     * [rules] 传入从仓库根到该目录沿路收集到的所有规则，**顺序即文件顺序，后面的覆盖前面的**
     * （同一份文件内也是后者胜，仅最后一个命中的规则决定结果）。
     * [isDirectory] 影响 directoryOnly 规则；祖先目录一律按目录评估。
     */
    fun isIgnored(rules: List<GitIgnoreRule>, path: String, isDirectory: Boolean): Boolean {
        if (rules.isEmpty()) return false
        val normalized = normalizePath(path)
        if (normalized.isEmpty()) return false
        for (ancestor in ancestorDirectories(normalized)) {
            if (evaluate(rules, ancestor, isDirectory = true)) return true
        }
        return evaluate(rules, normalized, isDirectory)
    }

    /** 按顺序求值，最后一个命中的规则决定结果。 */
    private fun evaluate(rules: List<GitIgnoreRule>, path: String, isDirectory: Boolean): Boolean {
        var ignored = false
        for (rule in rules) {
            if (matches(rule, path, isDirectory)) ignored = !rule.negated
        }
        return ignored
    }

    private fun matches(rule: GitIgnoreRule, path: String, isDirectory: Boolean): Boolean {
        if (rule.directoryOnly && !isDirectory) return false
        val relative = relativeTo(rule.baseDir, path) ?: return false
        if (relative.isEmpty()) return false
        return rule.matchesRelative(relative)
    }

    private fun normalizeDir(dir: String): String {
        val trimmed = dir.trimEnd('/')
        return if (trimmed.isEmpty() && dir.startsWith("/")) "/" else trimmed
    }

    private fun normalizePath(path: String): String {
        if (path.isEmpty()) return ""
        var p = path
        while (p.length > 1 && p.endsWith("/")) p = p.substring(0, p.length - 1)
        return if (p == "/") "" else p
    }

    /** path 相对 [baseDir] 的路径；不在其下返回 null，等于 baseDir 返回空串。 */
    private fun relativeTo(baseDir: String, path: String): String? {
        if (baseDir.isEmpty() || baseDir == "/") return path.removePrefix("/")
        if (path == baseDir) return ""
        if (!path.startsWith(baseDir) || path.length <= baseDir.length) return null
        if (path[baseDir.length] != '/') return null
        return path.substring(baseDir.length + 1)
    }

    /** 所有祖先目录，从浅到深，不含 path 自身。 */
    private fun ancestorDirectories(path: String): List<String> {
        val absolute = path.startsWith("/")
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.size <= 1) return emptyList()
        val result = ArrayList<String>(segments.size - 1)
        val builder = StringBuilder(if (absolute) "/" else "")
        for (index in 0 until segments.size - 1) {
            if (index > 0) builder.append('/')
            builder.append(segments[index])
            result.add(builder.toString())
        }
        return result
    }

    /** 去掉行尾未转义的空格。 */
    private fun trimTrailingSpaces(line: String): String {
        var s = line
        while (s.endsWith(" ")) {
            var backslashes = 0
            var index = s.length - 2
            while (index >= 0 && s[index] == '\\') {
                backslashes++
                index--
            }
            if (backslashes % 2 == 1) break
            s = s.dropLast(1)
        }
        return s
    }
}

/** glob → 正则。只用标准库。 */
private object GlobToRegex {

    fun compile(pattern: String, anchored: Boolean): Regex {
        val body = translate(pattern)
        // 非锚定规则等价于在所有层级前挂 "**/"
        val prefix = if (anchored) "^" else "^(.*/)?"
        return Regex(prefix + body + "$")
    }

    private fun translate(glob: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < glob.length) {
            when (glob[i]) {
                '*' -> {
                    var j = i
                    while (j < glob.length && glob[j] == '*') j++
                    if (j - i >= 2) {
                        if (j < glob.length && glob[j] == '/') {
                            out.append("(.*/)?")
                            j++
                        } else {
                            out.append(".*")
                        }
                    } else {
                        out.append("[^/]*")
                    }
                    i = j
                }

                '?' -> {
                    out.append("[^/]")
                    i++
                }

                '[' -> {
                    val close = findClassEnd(glob, i)
                    if (close < 0) {
                        out.append("\\[")
                        i++
                    } else {
                        out.append(translateClass(glob.substring(i + 1, close)))
                        i = close + 1
                    }
                }

                '\\' -> {
                    if (i + 1 < glob.length) {
                        out.append(Regex.escape(glob[i + 1].toString()))
                        i += 2
                    } else {
                        out.append("\\\\")
                        i++
                    }
                }

                else -> {
                    out.append(Regex.escape(glob[i].toString()))
                    i++
                }
            }
        }
        return out.toString()
    }

    /** 找到字符类的结束 ']'，找不到返回 -1。 */
    private fun findClassEnd(glob: String, start: Int): Int {
        var i = start + 1
        if (i < glob.length && (glob[i] == '!' || glob[i] == '^')) i++
        if (i < glob.length && glob[i] == ']') i++
        while (i < glob.length) {
            if (glob[i] == ']') return i
            i++
        }
        return -1
    }

    private fun translateClass(body: String): String {
        var content = body
        var negated = false
        if (content.startsWith("!") || content.startsWith("^")) {
            negated = true
            content = content.substring(1)
        }
        if (content.isEmpty()) return Regex.escape(body)
        val out = StringBuilder("[")
        if (negated) {
            out.append('^')
            // 取反字符类不能吃掉 '/'
            content += "/"
        }
        for (ch in content) {
            if (ch == '\\' || ch == ']' || ch == '[' || ch == '^' || ch == '&') {
                out.append('\\').append(ch)
            } else {
                out.append(ch)
            }
        }
        out.append(']')
        return out.toString()
    }
}
