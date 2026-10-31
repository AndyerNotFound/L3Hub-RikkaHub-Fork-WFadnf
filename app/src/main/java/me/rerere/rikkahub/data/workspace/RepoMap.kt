package me.rerere.rikkahub.data.workspace

/** 仓库地图里的一个文件：相对路径（'/' 分隔）、行数、顶层符号 */
data class RepoMapFile(
    val path: String,
    val lines: Int,
    val symbols: List<String>,
)

/**
 * 把源码文本压成「顶层符号」列表，再把多个文件渲染成紧凑文本地图。
 *
 * 所有处理都是逐行字符串/正则，不建语法树，因此有几条明确的取舍：
 * - 只认空格缩进的声明；Kotlin 放宽到 ≤2 个空格，Java 类型 ≤2、方法 ≤4，
 *   C# 类型 ≤4、方法 ≤8；Python 只认列 0；其余语言只认 0 缩进。Tab 缩进一律忽略。
 * - 多行签名（参数换行书写）、左花括号另起一行的 K&R 风格、缩进块内的嵌套类型识别不到。
 * - 参数列表按顶层逗号切分并去掉默认值，整条签名最多 80 字符，超出用 "…" 结尾。
 * - 单文件最多保留 40 个符号，同名声明只留第一次出现。
 * - [render] 按目录、文件名字母序输出，超出 maxChars 时**按顺序整行截断**并在末尾补
 *   "… (truncated)"；不做「优先保留符号多的文件」这类挑选，保证前缀稳定、可复现。
 */
object RepoMap {

    private const val MAX_SYMBOLS = 40
    private const val MAX_SIGNATURE = 80
    private const val TRUNCATED_SUFFIX = "… (truncated)"
    private const val DEEP_INDENT = 100

    private val WHITESPACE = Regex("\\s+")
    private val METHOD_NAME = Regex("[A-Za-z_$][\\w$:]*")

    /** 这些词出现在 '(' 前说明不是方法声明 */
    private val CONTROL_WORDS = setOf(
        "if", "else", "for", "foreach", "while", "do", "switch", "case", "catch", "try",
        "return", "throw", "new", "synchronized", "using", "lock", "when", "sizeof", "yield",
    )

    private enum class Lang { KOTLIN, JAVA, PYTHON, JS, GO, RUST, SWIFT, RUBY, CSHARP, CPP, NONE }

    // ---------- 各语言的声明正则 ----------

    private const val KOTLIN_MODS =
        "(?:(?:public|private|protected|internal|open|abstract|sealed|data|enum|value|annotation" +
            "|inline|suspend|tailrec|operator|infix|external|const|lateinit|override|actual|expect" +
            "|companion|noinline|crossinline)\\s+)*"

    private val KOTLIN_DECL = Regex("^($KOTLIN_MODS)(class|interface|object|fun|val|typealias)\\s+(.*)$")

    private val JAVA_TYPE = Regex(
        "^((?:(?:public|protected|private|static|final|abstract|sealed|strictfp)\\s+)*)" +
            "(class|interface|enum|record)\\s+(.*)$"
    )

    private val CSHARP_TYPE = Regex(
        "^((?:(?:public|protected|private|internal|static|abstract|sealed|partial|readonly" +
            "|unsafe|virtual|override)\\s+)*)(class|interface|enum|record|struct|namespace)\\s+(.*)$"
    )

    private val PYTHON_DECL = Regex("^(async\\s+def|def|class)\\s+(.*)$")

    private const val JS_MODS =
        "(?:(?:export|default|declare|async|abstract|public|private|protected|static|readonly)\\s+)*"

    private val JS_DECL = Regex("^($JS_MODS)(function|class|interface|type|const|let|var)\\s+(.*)$")

    private const val RUST_MODS = "(?:(?:pub(?:\\([^)]*\\))?|async|const|unsafe|extern|default)\\s+)*"

    private val RUST_DECL = Regex("^($RUST_MODS)(fn|struct|enum|trait|impl|union|mod)\\s+(.*)$")

    private const val SWIFT_MODS =
        "(?:(?:public|private|internal|fileprivate|open|final|static|override|mutating" +
            "|nonmutating|convenience|required|lazy|async)\\s+)*"

    private val SWIFT_DECL =
        Regex("^($SWIFT_MODS)(func|class|struct|enum|protocol|extension|actor|typealias)\\s+(.*)$")

    private val RUBY_DECL = Regex("^(def|class|module)\\s+([A-Za-z_][\\w.]*[?!=]?)(.*)$")

    private val CPP_TYPE =
        Regex("^(enum(?:\\s+class|\\s+struct)?|class|struct|union|namespace)\\s+([A-Za-z_]\\w*)(.*)$")

    private val GO_TYPE_KINDS = setOf("struct", "interface", "func", "map", "chan")

    // ---------- 提取 ----------

    /** 支持 kt/kts/java/py/ts/tsx/js/jsx/go/rs/swift/rb/cs/cpp/h；其他扩展名返回空列表 */
    fun extractSymbols(path: String, content: String): List<String> {
        val lang = languageOf(path)
        if (lang == Lang.NONE || content.isEmpty()) return emptyList()
        val symbols = ArrayList<String>()
        val seen = HashSet<String>()
        for (raw in content.lineSequence()) {
            if (symbols.size >= MAX_SYMBOLS) break
            val symbol = symbolOf(lang, raw.removeSuffix("\r")) ?: continue
            if (seen.add(symbol)) symbols += symbol
        }
        return symbols
    }

    private fun languageOf(path: String): Lang = when (path.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> Lang.KOTLIN
        "java" -> Lang.JAVA
        "py" -> Lang.PYTHON
        "ts", "tsx", "js", "jsx" -> Lang.JS
        "go" -> Lang.GO
        "rs" -> Lang.RUST
        "swift" -> Lang.SWIFT
        "rb" -> Lang.RUBY
        "cs" -> Lang.CSHARP
        "cpp", "h" -> Lang.CPP
        else -> Lang.NONE
    }

    private fun symbolOf(lang: Lang, line: String): String? {
        var spaces = 0
        var start = 0
        while (start < line.length && line[start] == ' ') {
            spaces++
            start++
        }
        if (start >= line.length) return null
        val text = line.substring(start).trimEnd()
        if (text.isEmpty() || isComment(text)) return null
        // Tab 缩进一律当作深缩进，避免 Tab 缩进的成员被算成顶层
        val indent = if (line[start].isWhitespace()) DEEP_INDENT else spaces
        return when (lang) {
            Lang.KOTLIN -> kotlinSymbol(text, indent)
            Lang.JAVA -> javaLikeSymbol(text, indent, JAVA_TYPE, typeIndent = 2, methodIndent = 4)
            Lang.CSHARP -> javaLikeSymbol(text, indent, CSHARP_TYPE, typeIndent = 4, methodIndent = 8)
            Lang.PYTHON -> pythonSymbol(text, indent)
            Lang.JS -> jsSymbol(text, indent)
            Lang.GO -> goSymbol(text, indent)
            Lang.RUST -> rustSymbol(text, indent)
            Lang.SWIFT -> swiftSymbol(text, indent)
            Lang.RUBY -> rubySymbol(text, indent)
            Lang.CPP -> cppSymbol(text, indent)
            Lang.NONE -> null
        }
    }

    private fun isComment(text: String): Boolean =
        text.startsWith("//") || text.startsWith("/*") || text.startsWith("*") || text.startsWith("#")

    // ---------- 各语言规则 ----------

    private fun kotlinSymbol(text: String, indent: Int): String? {
        if (indent > 2) return null
        val body = stripLeadingAnnotations(text)
        if (body.isEmpty()) return null
        val match = KOTLIN_DECL.find(body) ?: return null
        val head = joinHead(match.groupValues[1], match.groupValues[2])
        val rest = match.groupValues[3].trimStart()
        if (rest.startsWith("{") || rest.startsWith(":") || rest.startsWith("=")) return null
        return when (match.groupValues[2]) {
            "val", "typealias" -> identifier(rest)?.let { fit("$head $it") }
            else -> declSignature(head, rest)
        }
    }

    private fun javaLikeSymbol(
        text: String,
        indent: Int,
        typeRe: Regex,
        typeIndent: Int,
        methodIndent: Int,
    ): String? {
        val body = stripLeadingAnnotations(text)
        if (body.isEmpty()) return null
        val type = typeRe.find(body)
        if (type != null && indent <= typeIndent) {
            declSignature(joinHead(type.groupValues[1], type.groupValues[2]), type.groupValues[3])?.let { return it }
        }
        if (indent > methodIndent) return null
        return braceMethod(body)
    }

    private fun pythonSymbol(text: String, indent: Int): String? {
        if (indent != 0) return null
        val match = PYTHON_DECL.find(text) ?: return null
        return declSignature(compact(match.groupValues[1]), match.groupValues[2])
    }

    private fun jsSymbol(text: String, indent: Int): String? {
        if (indent != 0) return null
        val match = JS_DECL.find(text) ?: return null
        val head = joinHead(match.groupValues[1], match.groupValues[2])
        val rest = match.groupValues[3]
        return when (match.groupValues[2]) {
            "function", "class", "interface", "type" -> declSignature(head, rest)
            else -> {
                val name = identifier(rest) ?: return null
                val afterName = rest.substring(name.length).trimStart()
                if (afterName.startsWith("==") || afterName.startsWith("=>")) return null
                if (!afterName.startsWith("=")) return null
                val value = afterName.substring(1).trimStart()
                if (value.startsWith("function")) return fit("$head $name = function")
                val arrow = value.indexOf("=>")
                if (arrow < 0) return null
                fit("$head $name = ${compact(value.substring(0, arrow))} =>")
            }
        }
    }

    private fun goSymbol(text: String, indent: Int): String? {
        if (indent != 0) return null
        return when {
            text.startsWith("func ") -> goFunc(text)
            text.startsWith("type ") -> goType(text)
            else -> null
        }
    }

    private fun goFunc(text: String): String? {
        var rest = text.removePrefix("func").trimStart()
        var receiver = ""
        if (rest.startsWith("(")) {
            val close = closingParen(rest) ?: return null
            receiver = " (" + compact(rest.substring(1, close)) + ")"
            rest = rest.substring(close + 1).trimStart()
        }
        val name = identifier(rest) ?: return null
        val tail = rest.substring(name.length).trimStart()
        val params = if (tail.startsWith("(")) {
            closingParen(tail)?.let { cleanParams(tail.substring(1, it)) }
        } else {
            null
        }
        val signature = if (params == null) "func$receiver $name" else "func$receiver $name($params)"
        return fit(signature)
    }

    private fun goType(text: String): String? {
        val rest = text.removePrefix("type").trimStart()
        val name = identifier(rest) ?: return null
        val tail = rest.substring(name.length).trimStart()
        val kind = tail.takeWhile { it.isLetter() }
        return fit(if (kind in GO_TYPE_KINDS) "type $name $kind" else "type $name")
    }

    private fun rustSymbol(text: String, indent: Int): String? {
        if (indent != 0) return null
        val match = RUST_DECL.find(text) ?: return null
        val head = joinHead(match.groupValues[1], match.groupValues[2])
        var rest = match.groupValues[3].trimStart()
        if (match.groupValues[2] == "impl" || match.groupValues[2] == "mod") {
            if (rest.startsWith("<")) {
                val close = rest.indexOf('>')
                if (close < 0) return null
                rest = rest.substring(close + 1).trimStart()
            }
            val target = rest.substringBefore('{').substringBefore(" where").trim()
            if (target.isEmpty()) return null
            return fit("$head $target")
        }
        if (rest.startsWith("<")) {
            val close = rest.indexOf('>')
            if (close < 0) return null
            rest = rest.substring(close + 1).trimStart()
        }
        return declSignature(head, rest)
    }

    private fun swiftSymbol(text: String, indent: Int): String? {
        if (indent != 0) return null
        val match = SWIFT_DECL.find(text) ?: return null
        return declSignature(joinHead(match.groupValues[1], match.groupValues[2]), match.groupValues[3])
    }

    private fun rubySymbol(text: String, indent: Int): String? {
        if (indent != 0) return null
        val match = RUBY_DECL.find(text) ?: return null
        val keyword = match.groupValues[1]
        val name = match.groupValues[2]
        val tail = match.groupValues[3].trimStart()
        val params = if (tail.startsWith("(")) {
            closingParen(tail)?.let { cleanParams(tail.substring(1, it)) }
        } else {
            null
        }
        return fit(if (params == null) "$keyword $name" else "$keyword $name($params)")
    }

    private fun cppSymbol(text: String, indent: Int): String? {
        if (indent != 0) return null
        val type = CPP_TYPE.find(text)
        if (type != null) {
            return declSignature(compact(type.groupValues[1]), type.groupValues[2] + type.groupValues[3])
        }
        return braceMethod(text)
    }

    /** Java/C#/C++ 里「缩进较浅 + 以 '{' 结尾 + 有 '(' 」的方法声明 */
    private fun braceMethod(text: String): String? {
        if (!text.endsWith("{")) return null
        val decl = text.removeSuffix("{").trim()
        val open = decl.indexOf('(')
        if (open <= 0) return null
        val close = decl.lastIndexOf(')')
        if (close < open) return null
        val head = decl.substring(0, open).trim()
        val params = decl.substring(open + 1, close)
        val words = head.split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.size < 2) return null
        val name = words.last()
        if (!METHOD_NAME.matches(name) || name in CONTROL_WORDS) return null
        if (words.first() in CONTROL_WORDS) return null
        return fit("$head(${cleanParams(params)})")
    }

    // ---------- 渲染 ----------

    /** 生成紧凑文本地图：按目录分组，单文件一行，符号用逗号连接 */
    fun render(files: List<RepoMapFile>, maxChars: Int = 12000): String {
        if (files.isEmpty() || maxChars <= 0) return ""
        val sorted = files
            .filter { it.path.isNotBlank() }
            .sortedWith(compareBy({ directoryOf(it.path) }, { it.path }))
        if (sorted.isEmpty()) return ""

        val lines = ArrayList<String>()
        for (group in sorted.groupBy { directoryOf(it.path) }) {
            lines += "${group.key} (${group.value.size} files)"
            for (file in group.value) {
                val name = file.path.substringAfterLast('/')
                val head = "  $name (${file.lines})"
                lines += if (file.symbols.isEmpty()) head else "$head — ${file.symbols.joinToString(", ")}"
            }
        }

        val builder = StringBuilder()
        for (line in lines) {
            val addition = if (builder.isEmpty()) line else "\n$line"
            if (builder.length + addition.length + TRUNCATED_SUFFIX.length + 1 > maxChars) {
                if (builder.isNotEmpty()) builder.append('\n')
                builder.append(TRUNCATED_SUFFIX)
                return builder.toString()
            }
            builder.append(addition)
        }
        return builder.toString()
    }

    private fun directoryOf(path: String): String {
        val dir = path.substringBeforeLast('/', "")
        return if (dir.isEmpty()) "./" else "$dir/"
    }

    // ---------- 通用小工具 ----------

    private fun joinHead(modifiers: String, keyword: String): String =
        compact("$modifiers $keyword")

    private fun compact(text: String): String = text.trim().replace(WHITESPACE, " ")

    private fun fit(signature: String): String =
        if (signature.length <= MAX_SIGNATURE) signature else signature.take(MAX_SIGNATURE - 1).trimEnd() + "…"

    /** 拼出 "head name(params)"，没有括号就只有 "head name" */
    private fun declSignature(head: String, rest: String): String? {
        val body = rest.trimStart()
        val name = identifier(body) ?: return null
        val tail = body.substring(name.length).trimStart()
        val params = if (tail.startsWith("(")) {
            closingParen(tail)?.let { cleanParams(tail.substring(1, it)) }
        } else {
            null
        }
        return fit(if (params == null) "$head $name" else "$head $name($params)")
    }

    /** 取开头的标识符；Kotlin 的反引号名字整体保留 */
    private fun identifier(text: String): String? {
        if (text.isEmpty()) return null
        if (text[0] == '`') {
            val close = text.indexOf('`', 1)
            return if (close < 0) null else text.substring(0, close + 1)
        }
        var end = 0
        while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_' || text[end] == '$')) end++
        return if (end == 0) null else text.substring(0, end)
    }

    /** text 以 '(' 开头时返回配对的 ')' 下标 */
    private fun closingParen(text: String): Int? {
        var depth = 0
        for (index in text.indices) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    /** 去掉参数里的默认值，并统一成 ", " 分隔 */
    private fun cleanParams(raw: String): String =
        splitTopLevel(raw, ',')
            .map { dropDefault(it).trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")

    private fun splitTopLevel(text: String, separator: Char): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var depth = 0
        for (char in text) {
            when (char) {
                '(', '<', '[', '{' -> {
                    depth++
                    current.append(char)
                }
                ')', '>', ']', '}' -> {
                    if (depth > 0) depth--
                    current.append(char)
                }
                separator -> if (depth == 0) {
                    result += current.toString()
                    current.setLength(0)
                } else {
                    current.append(char)
                }
                else -> current.append(char)
            }
        }
        result += current.toString()
        return result
    }

    /** 截断单个参数里的 "= 默认值" */
    private fun dropDefault(param: String): String {
        var depth = 0
        for (index in param.indices) {
            val char = param[index]
            when {
                char == '(' || char == '<' || char == '[' || char == '{' -> depth++
                char == ')' || char == '>' || char == ']' || char == '}' -> if (depth > 0) depth--
                char == '=' && depth == 0 -> {
                    val prev = param.getOrNull(index - 1)
                    val next = param.getOrNull(index + 1)
                    val comparison = prev == '=' || prev == '!' || prev == '<' || prev == '>' ||
                        next == '=' || next == '>'
                    if (!comparison) return param.substring(0, index).trim()
                }
            }
        }
        return param.trim()
    }

    /** 去掉行首注解，支持 @Foo 与 @Foo("x") */
    private fun stripLeadingAnnotations(text: String): String {
        var rest = text
        while (rest.startsWith("@")) {
            var index = 1
            while (index < rest.length) {
                val char = rest[index]
                if (char.isLetterOrDigit() || char == '_' || char == '.' || char == ':') index++ else break
            }
            if (index < rest.length && rest[index] == '(') {
                val close = closingParen(rest.substring(index)) ?: return ""
                index += close + 1
            }
            rest = rest.substring(index).trimStart()
        }
        return rest
    }
}
