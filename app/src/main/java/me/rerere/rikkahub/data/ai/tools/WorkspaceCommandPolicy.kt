package me.rerere.rikkahub.data.ai.tools

/**
 * `workspace_shell` 的只读命令判定。
 *
 * 只放行「不写文件、不执行任意代码、不改变仓库状态」的命令，用来免掉逐条审批；
 * 任何解析不确定的写法一律判为需要审批——误判只会多一次点击，不会绕过确认。
 */
internal object WorkspaceCommandPolicy {

    /** 命令风险分级：只读直接放行，危险命令无论用户怎么配置都必须确认 */
    enum class Risk { READ_ONLY, WRITE, DANGEROUS }

    private val READ_ONLY_COMMANDS = setOf(
        "ls", "cat", "head", "tail", "wc", "grep", "egrep", "fgrep", "rg", "find", "tree",
        "file", "stat", "du", "df", "pwd", "echo", "printf", "basename", "dirname",
        "realpath", "readlink", "sort", "uniq", "cut", "tr", "jq", "which", "type",
        "diff", "comm", "column", "nl", "od", "xxd", "strings", "md5sum", "sha256sum",
        "git",
    )

    private val GIT_READ_ONLY = setOf(
        "status", "diff", "log", "show", "blame", "branch", "remote", "rev-parse",
        "ls-files", "grep", "describe", "shortlog", "show-ref", "cat-file", "whatchanged", "tag",
    )

    private val FIND_BLOCKED = setOf(
        "-exec", "-execdir", "-ok", "-okdir", "-delete",
        "-fprint", "-fprint0", "-fprintf", "-fls",
    )

    private val ASSIGNMENT = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*$")

    /** 整条命令是否全部由只读片段组成；含管道、`;`、`&&` 时逐段判断 */
    fun isReadOnly(command: String): Boolean {
        val segments = splitSegments(command) ?: return false
        if (segments.isEmpty()) return false
        return segments.all(::isReadOnlySegment)
    }

    /**
     * 按 `;` `\n` `|` `&&` 切分命令，同时拒绝会执行代码或写文件的语法。
     * 返回 null 表示语法不安全（命令替换、重定向、子 shell、后台运行等）。
     */
    private fun splitSegments(command: String): List<List<String>>? {
        val segments = mutableListOf<List<String>>()
        var tokens = mutableListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var index = 0

        fun flushToken() {
            if (token.isNotEmpty()) {
                tokens += token.toString()
                token.clear()
            }
        }

        fun flushSegment() {
            flushToken()
            if (tokens.isNotEmpty()) segments += tokens.toList()
            tokens = mutableListOf()
        }

        while (index < command.length) {
            val c = command[index]
            when {
                quote == '\'' -> if (c == '\'') quote = null else token.append(c)
                quote == '"' -> when (c) {
                    '"' -> quote = null
                    '$', '`', '\\' -> return null
                    else -> token.append(c)
                }
                c == '\'' || c == '"' -> quote = c
                c == '\\' -> {
                    index++
                    if (index >= command.length) return null
                    token.append(command[index])
                }
                c == ';' || c == '\n' -> flushSegment()
                c == '|' -> flushSegment()
                c == '&' -> {
                    if (command.getOrNull(index + 1) != '&') return null
                    index++
                    flushSegment()
                }
                c == '$' || c == '`' || c == '>' || c == '<' ||
                    c == '(' || c == ')' || c == '{' || c == '}' -> return null
                c.isWhitespace() -> flushToken()
                else -> token.append(c)
            }
            index++
        }
        if (quote != null) return null
        flushSegment()
        return segments
    }

    private fun isReadOnlySegment(tokens: List<String>): Boolean {
        var index = 0
        while (index < tokens.size && ASSIGNMENT.matches(tokens[index])) index++
        if (index >= tokens.size) return false

        val command = tokens[index].substringAfterLast('/')
        val args = tokens.drop(index + 1)
        if (command !in READ_ONLY_COMMANDS) return false

        return when (command) {
            "find" -> args.none { arg -> FIND_BLOCKED.any { arg == it || arg.startsWith("$it=") } }
            "git" -> isReadOnlyGit(args)
            "rg" -> args.none { it == "--pre" || it.startsWith("--pre=") }
            "sort" -> args.none {
                it == "-o" || it.startsWith("--output") || (it.startsWith("-o") && it.length > 2) ||
                    it == "--compress-program" || it.startsWith("--compress-program=")
            }
            "uniq" -> args.count { !it.startsWith("-") } <= 1
            "diff" -> args.none { it.startsWith("--output") }
            "xxd" -> args.none { it == "-r" }
            "type", "which" -> true
            else -> true
        }
    }

    private fun isReadOnlyGit(args: List<String>): Boolean {
        if (args.any {
                it == "-c" || it.startsWith("--exec-path") || it.startsWith("--upload-pack") ||
                    it.startsWith("--receive-pack") || it == "--ext-diff"
            }
        ) {
            return false
        }
        if (args.any { it.startsWith("--output") }) return false
        val subcommand = args.firstOrNull { !it.startsWith("-") } ?: return false
        if (subcommand !in GIT_READ_ONLY) return false

        val subArgs = args.dropWhile { it != subcommand }.drop(1)
        return when (subcommand) {
            "branch" -> subArgs.none {
                it in setOf("-d", "-D", "-m", "-M", "-f", "-u", "--delete", "--move", "--force", "--set-upstream-to")
            }
            "remote" -> subArgs.isEmpty() || subArgs.all { it.startsWith("-") }
            "tag" -> subArgs.all { it.startsWith("-") }
            else -> true
        }
    }

    // 破坏性/提权/远程执行类命令，单独判定为 DANGEROUS
    private val DANGEROUS_PATTERNS = listOf(
        Regex("""(^|[;&|]\s*)sudo\b"""),
        Regex("""\brm\s+(-[A-Za-z]*[rf][A-Za-z]*\s+)+(/|~|${'$'}HOME|\*)\s*$"""),
        Regex("""\bmkfs(\.\w+)?\b"""),
        Regex("""\bdd\b[^\n]*\bof=/dev/"""),
        Regex("""\b(chmod|chown)\s+-R\s+(777|root)\s+/(\s|$)"""),
        Regex("""\b(curl|wget)\b[^\n|;]*\|\s*(sudo\s+)?(ba|z|fi)?sh\b"""),
        Regex("""\bgit\s+push\b[^\n]*\s(--force|-f)\b"""),
        Regex("""\bgit\s+clean\b[^\n]*\s-[A-Za-z]*[xdf]"""),
        Regex("""\b(shutdown|reboot|poweroff|halt)\b"""),
        Regex("""\bhistory\s+-c\b"""),
        Regex(""":\(\)\s*\{.*\}\s*;?\s*:"""),
    )

    fun classify(command: String): Risk = when {
        isReadOnly(command) -> Risk.READ_ONLY
        DANGEROUS_PATTERNS.any { it.containsMatchIn(command) } -> Risk.DANGEROUS
        else -> Risk.WRITE
    }
}
