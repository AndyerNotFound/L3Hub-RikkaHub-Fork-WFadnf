package me.rerere.rikkahub.data.workspace

/** 测试框架；[AUTO] 表示由解析器根据输出自行识别 */
enum class TestFramework {
    AUTO,
    GRADLE,
    PYTEST,
    JEST,
    GO,
    CARGO,
}

/** 一条测试诊断：跳转所需的文件:行 + 单行摘要 */
data class TestDiagnostic(
    val file: String?,
    val line: Int?,
    val test: String?,
    val message: String,
    val severity: String = "error",
)

/** 一次测试运行的解析结果 */
data class TestRunSummary(
    val framework: TestFramework,
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val diagnostics: List<TestDiagnostic>,
    val durationMs: Long?,
    val rawTail: String,
)

/**
 * 把 Gradle / pytest / jest / go / cargo 的原始输出解析成结构化诊断。
 *
 * 只做字符串与正则处理，不依赖执行环境，方便单测。
 */
object TestOutputParser {
    private const val MAX_DIAGNOSTICS = 50
    private const val RAW_TAIL_LENGTH = 4000
    private const val MAX_MESSAGE_LENGTH = 300

    private val ANSI_ESCAPE = Regex("\u001B\\[[;\\d]*[A-Za-z]")
    private val WHITESPACE = Regex("\\s+")

    // ---------- 命令行识别 ----------

    private val CMD_GO = Regex("""(?:^|\s)go\s+test(?:\s|$)""")
    private val CMD_CARGO = Regex("""(?:^|\s)cargo\s+test(?:\s|$)""")
    private val CMD_GRADLE = Regex("""(?:^|[\s/])(?:\./)?gradlew?(?:\.bat)?(?:\s|$)""")
    private val CMD_PYTEST = Regex("""(?:^|[\s/])(?:pytest|py\.test)(?:\s|$)|(?:^|\s)-m\s+pytest(?:\s|$)""")
    private val CMD_JEST = Regex(
        """(?:^|[\s/])(?:jest|vitest)(?:\s|$)|(?:^|\s)(?:npm|yarn|pnpm|bun)\s+(?:run\s+)?test(?::[\w:.-]*)?(?:\s|$)"""
    )

    // ---------- 输出特征 ----------

    private val GRADLE_SIGNAL = Regex(
        """(?m)(?:^> Task |^BUILD SUCCESSFUL|^BUILD FAILED|\d+ tests? completed|^e: file://)"""
    )
    private val PYTEST_SIGNAL = Regex("""(?m)(?:^=+ .*\b(?:passed|failed)\b|^(?:FAILED|ERROR) \S+::)""")
    private val JEST_SIGNAL = Regex("""(?m)(?:^Tests:\s|^\s*● )""")
    private val GO_SIGNAL = Regex("""(?m)^--- (?:FAIL|PASS|SKIP): """)
    private val CARGO_SIGNAL = Regex("""(?m)(?:^test result: (?:ok|FAILED)|^thread '[^']*' panicked)""")

    // ---------- Gradle ----------

    private val GRADLE_FAILED_HEADER = Regex("""^(\S+)\s+>\s+(.+?)\s+FAILED\s*$""")
    private val GRADLE_STACK_FRAME = Regex("""^\s*at\s+([\w.<>]+)\.([\w<>]+)\(([^()]+):(\d+)\)""")
    private val GRADLE_STATS = Regex("""(\d+)\s+tests?\s+completed(?:,\s*(\d+)\s+failed)?(?:,\s*(\d+)\s+skipped)?""")
    private val KOTLIN_DIAGNOSTIC = Regex("""^e:\s*(?:file://)?(.+?):(\d+):(\d+):?\s*(.*)$""")
    private val EXCEPTION_LINE = Regex("""^(?:Caused by:\s*)?[\w.]*[A-Z]\w*(?:Exception|Error|Failure|Throwable)\b.*$""")

    // ---------- pytest ----------

    private val PYTEST_FAILED = Regex("""^(FAILED|ERROR)\s+(\S+?)(?:\s+-\s+(.*))?$""")
    private val PYTEST_VERBOSE_FAILED = Regex("""^(\S+::\S+)\s+(FAILED|ERROR)(?:\s+\[.*?\])?$""")
    private val PYTEST_TRACEBACK = Regex("""^(\S+?\.py):(\d+):\s*(.*)$""")
    private val PYTEST_ERROR_TEXT = Regex("""^E\s{2,}(\w*(?:Error|Exception)\b.*)$""")
    private val PYTEST_COUNT = Regex("""(\d+)\s+(passed|failed|errors?|skipped|xfailed|xpassed|warnings?|deselected)""")
    private val PYTEST_DURATION = Regex("""\bin\s+([\d.]+)\s*s""")

    // ---------- jest ----------

    private val JEST_SECTION = Regex("""^\s*●\s+(.+)$""")
    private val JEST_LOCATION = Regex(
        """at\s+(?:.*?\()?(?:file://)?([^\s()]+\.(?:tsx?|jsx?|mjs|cjs|vue)):(\d+):(\d+)"""
    )
    private val JEST_TESTS_SUMMARY = Regex("""^Tests:\s+(.*)$""")
    private val JEST_TIME = Regex("""^Time:\s+([\d.]+)\s*s""")
    private val JEST_COUNT = Regex("""(\d+)\s+(failed|passed|skipped|todo|pending|total)""")
    private val JEST_CODE_FRAME = Regex("""^\s*>?\s*\d+\s*\|""")

    // ---------- go ----------

    private val GO_TEST_RESULT = Regex("""^\s*--- (FAIL|PASS|SKIP):\s+(\S+)\s+\(([\d.]+)s\)""")
    private val GO_RUN = Regex("""^=== RUN\s+(\S+)""")
    private val GO_MESSAGE = Regex("""^\s+(\S+?\.go):(\d+):\s*(.*)$""")
    private val GO_PACKAGE_RESULT = Regex("""^(?:ok|FAIL)\s+(\S+)\s+([\d.]+)s""")

    // ---------- cargo ----------

    private val CARGO_RESULT = Regex("""^test result:\s+\w+\..*?(\d+)\s+passed;\s*(\d+)\s+failed;\s*(\d+)\s+ignored""")
    private val CARGO_PANIC = Regex("""^thread '([^']*)' panicked at\s+(?:'([^']*)',\s*)?(\S+?):(\d+):(\d+)""")
    private val CARGO_STDOUT_HEADER = Regex("""^---- (\S+) stdout ----""")

    // ---------- JUnit XML ----------

    private val JUNIT_TESTCASE = Regex("""<testcase\b([^>]*)>""")
    private val JUNIT_FAILURE = Regex("""<(failure|error)\b([^>]*)>""")
    private val JUNIT_STACK = Regex("""at\s+([\w.<>]+)\.([\w<>]+)\(([^()]+):(\d+)\)""")
    private val JUNIT_ATTR = Regex("([\\w:-]+)\\s*=\\s*\"([^\"]*)\"")

    // ---------- 公开 API ----------

    /** 根据命令行猜测框架，猜不出返回 [TestFramework.AUTO]。 */
    fun detectFramework(command: String): TestFramework {
        val cmd = command.lowercase()
        return when {
            CMD_GO.containsMatchIn(cmd) -> TestFramework.GO
            CMD_CARGO.containsMatchIn(cmd) -> TestFramework.CARGO
            CMD_GRADLE.containsMatchIn(cmd) -> TestFramework.GRADLE
            CMD_PYTEST.containsMatchIn(cmd) -> TestFramework.PYTEST
            CMD_JEST.containsMatchIn(cmd) -> TestFramework.JEST
            else -> TestFramework.AUTO
        }
    }

    /** 解析原始 stdout+stderr。 */
    fun parse(framework: TestFramework, output: String, workspaceRoot: String? = null): TestRunSummary {
        val rawTail = output.takeLast(RAW_TAIL_LENGTH)
        val resolved = if (framework == TestFramework.AUTO) detectFromOutput(output) else framework
        val summary = when (resolved) {
            TestFramework.GRADLE -> parseGradle(output, workspaceRoot)
            TestFramework.PYTEST -> parsePytest(output, workspaceRoot)
            TestFramework.JEST -> parseJest(output, workspaceRoot)
            TestFramework.GO -> parseGo(output, workspaceRoot)
            TestFramework.CARGO -> parseCargo(output, workspaceRoot)
            TestFramework.AUTO -> parseAuto(output, workspaceRoot)
        }
        return summary.copy(rawTail = rawTail)
    }

    /** 解析 Gradle 的 JUnit XML（TEST-*.xml 内容），只取失败/错误节点。 */
    fun parseJUnitXml(xml: String): List<TestDiagnostic> {
        if (xml.isBlank()) return emptyList()
        val drafts = ArrayList<Draft>()
        for (testcase in JUNIT_TESTCASE.findAll(xml)) {
            val attrs = testcase.groupValues[1]
            if (attrs.trimEnd().endsWith("/")) continue
            val bodyStart = testcase.range.last + 1
            val bodyEnd = xml.indexOf("</testcase>", bodyStart).let { if (it < 0) xml.length else it }
            val body = xml.substring(bodyStart, bodyEnd)
            val failure = JUNIT_FAILURE.find(body) ?: continue
            val nodeName = failure.groupValues[1]
            val failureAttrs = failure.groupValues[2]
            val textStart = failure.range.last + 1
            val closeTag = "</" + nodeName + ">"
            val textEnd = body.indexOf(closeTag, textStart).let { if (it < 0) body.length else it }
            val text = unescapeXml(body.substring(textStart, textEnd)).trim()

            val name = attrValue(attrs, "name").removeSuffix("()")
            val className = attrValue(attrs, "classname")
            val test = when {
                name.isBlank() -> null
                className.isBlank() -> name
                else -> className.substringAfterLast('.').substringBefore('$') + "." + name
            }
            val frame = findStackFrame(text, className)
            drafts += Draft(
                file = attrValue(attrs, "file").ifBlank { frame?.groupValues?.get(3) },
                line = attrValue(attrs, "line").toIntOrNull() ?: frame?.groupValues?.get(4)?.toIntOrNull(),
                test = test,
                message = attrValue(failureAttrs, "message").ifBlank { text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
                    .ifBlank { "test failed" },
                severity = if (nodeName == "error") "error" else "failure",
            )
        }
        return finalize(drafts, null)
    }

    // ---------- AUTO ----------

    private fun detectFromOutput(output: String): TestFramework = when {
        GRADLE_SIGNAL.containsMatchIn(output) -> TestFramework.GRADLE
        PYTEST_SIGNAL.containsMatchIn(output) -> TestFramework.PYTEST
        JEST_SIGNAL.containsMatchIn(output) -> TestFramework.JEST
        GO_SIGNAL.containsMatchIn(output) -> TestFramework.GO
        CARGO_SIGNAL.containsMatchIn(output) -> TestFramework.CARGO
        else -> TestFramework.AUTO
    }

    private fun hasSignal(framework: TestFramework, output: String): Boolean = when (framework) {
        TestFramework.GRADLE -> GRADLE_SIGNAL.containsMatchIn(output)
        TestFramework.PYTEST -> PYTEST_SIGNAL.containsMatchIn(output)
        TestFramework.JEST -> JEST_SIGNAL.containsMatchIn(output)
        TestFramework.GO -> GO_SIGNAL.containsMatchIn(output)
        TestFramework.CARGO -> CARGO_SIGNAL.containsMatchIn(output)
        TestFramework.AUTO -> false
    }

    /** 识别不出框架时：把所有有信号特征的解析结果合并，统计取信息量最大的一份。 */
    private fun parseAuto(output: String, root: String?): TestRunSummary {
        val candidates = listOf(
            TestFramework.GRADLE,
            TestFramework.PYTEST,
            TestFramework.JEST,
            TestFramework.GO,
            TestFramework.CARGO,
        ).filter { hasSignal(it, output) }
        if (candidates.isEmpty()) {
            return TestRunSummary(TestFramework.AUTO, 0, 0, 0, emptyList(), null, "")
        }
        val summaries = candidates.map { parse(it, output, root) }
        val primary = summaries.maxByOrNull { it.passed + it.failed + it.skipped } ?: summaries.first()
        val drafts = summaries.flatMap { summary ->
            summary.diagnostics.map { Draft(it.file, it.line, it.test, it.message, it.severity) }
        }
        return TestRunSummary(
            framework = primary.framework,
            passed = primary.passed,
            failed = primary.failed,
            skipped = primary.skipped,
            diagnostics = finalize(drafts, null),
            durationMs = summaries.firstNotNullOfOrNull { it.durationMs },
            rawTail = "",
        )
    }

    // ---------- Gradle ----------

    private class GradleFailure(val className: String, val method: String) {
        var file: String? = null
        var line: Int? = null
        var firstSourceFrame: Pair<String, Int>? = null
        var detail: String? = null
        val test: String get() = className.substringAfterLast('.') + "." + method
    }

    private fun parseGradle(output: String, root: String?): TestRunSummary {
        val drafts = ArrayList<Draft>()
        var passed = 0
        var failed = 0
        var skipped = 0
        var statsSeen = false
        var current: GradleFailure? = null

        fun toDraft(failure: GradleFailure): Draft {
            val file = failure.file ?: failure.firstSourceFrame?.first
            val line = if (failure.file != null) failure.line else failure.firstSourceFrame?.second
            return Draft(
                file = file,
                line = line,
                test = failure.test,
                message = failure.detail ?: (failure.test + " FAILED"),
                severity = "failure",
            )
        }

        fun flush() {
            val failure = current ?: return
            drafts += toDraft(failure)
            current = null
        }

        for (raw in output.lineSequence()) {
            val line = stripAnsi(raw)
            val trimmed = line.trim()

            val header = GRADLE_FAILED_HEADER.matchEntire(trimmed)
            if (header != null) {
                flush()
                current = GradleFailure(
                    className = header.groupValues[1],
                    method = header.groupValues[2].trim().removeSuffix("()").replace(" > ", "."),
                )
                continue
            }

            val kotlinError = KOTLIN_DIAGNOSTIC.matchEntire(trimmed)
            if (kotlinError != null) {
                flush()
                drafts += Draft(
                    file = kotlinError.groupValues[1],
                    line = kotlinError.groupValues[2].toIntOrNull(),
                    test = null,
                    message = kotlinError.groupValues[4],
                    severity = "error",
                )
                continue
            }

            val stats = GRADLE_STATS.find(line)
            if (stats != null) {
                val total = stats.groupValues[1].toIntOrNull() ?: 0
                val failedCount = stats.groupValues[2].toIntOrNull() ?: 0
                val skippedCount = stats.groupValues[3].toIntOrNull() ?: 0
                passed = (total - failedCount - skippedCount).coerceAtLeast(0)
                failed = failedCount
                skipped = skippedCount
                statsSeen = true
                continue
            }

            val failure = current ?: continue

            val frame = GRADLE_STACK_FRAME.find(line)
            if (frame != null) {
                val frameClass = frame.groupValues[1]
                val frameFile = frame.groupValues[3]
                val frameLine = frame.groupValues[4].toIntOrNull()
                if (frameFile.isNotEmpty() && frameLine != null) {
                    if (failure.file == null && sameTestClass(frameClass, failure.className)) {
                        failure.file = frameFile
                        failure.line = frameLine
                    } else if (failure.firstSourceFrame == null && isSourceFile(frameFile)) {
                        failure.firstSourceFrame = frameFile to frameLine
                    }
                }
                continue
            }

            if (failure.detail == null && EXCEPTION_LINE.matches(trimmed)) {
                failure.detail = trimmed
            }
        }
        flush()

        if (!statsSeen) failed = drafts.count { it.severity == "failure" }
        return TestRunSummary(TestFramework.GRADLE, passed, failed, skipped, finalize(drafts, root), null, "")
    }

    // ---------- pytest ----------

    private class TraceLine(val file: String, val line: Int, val rest: String)

    private fun parsePytest(output: String, root: String?): TestRunSummary {
        val lines = output.lineSequence().map { stripAnsi(it) }.toList()
        val traces = ArrayList<TraceLine>()
        val errorTexts = HashMap<String, String>()
        var currentFile: String? = null
        for (line in lines) {
            val trace = PYTEST_TRACEBACK.matchEntire(line.trimEnd())
            if (trace != null) {
                val fileName = trace.groupValues[1]
                val lineNo = trace.groupValues[2].toIntOrNull()
                if (lineNo != null) {
                    traces += TraceLine(fileName, lineNo, trace.groupValues[3].trim())
                    currentFile = fileName
                    continue
                }
            }
            val errorLine = PYTEST_ERROR_TEXT.matchEntire(line.trimEnd())
            if (errorLine != null) {
                val fileName = currentFile
                if (fileName != null && errorTexts[fileName] == null) {
                    errorTexts[fileName] = errorLine.groupValues[1].trim()
                }
            }
        }

        val drafts = ArrayList<Draft>()
        var passed = 0
        var failed = 0
        var skipped = 0
        var durationMs: Long? = null
        var summarySeen = false

        for (line in lines) {
            val trimmed = line.trim()
            if (isPytestSummary(trimmed)) {
                summarySeen = true
                for (count in PYTEST_COUNT.findAll(trimmed)) {
                    val value = count.groupValues[1].toIntOrNull() ?: 0
                    when (count.groupValues[2]) {
                        "passed" -> passed = value
                        "failed" -> failed = value
                        "error", "errors" -> failed += value
                        "skipped" -> skipped = value
                    }
                }
                val duration = PYTEST_DURATION.find(trimmed)?.groupValues?.get(1)
                if (duration != null) durationMs = secondsToMillis(duration)
                continue
            }

            val simpleFailure = PYTEST_FAILED.matchEntire(trimmed)
            if (simpleFailure != null) {
                addPytestFailure(
                    drafts = drafts,
                    testId = simpleFailure.groupValues[2],
                    message = simpleFailure.groupValues[3],
                    status = simpleFailure.groupValues[1],
                    traces = traces,
                    errors = errorTexts,
                )
                continue
            }

            val verboseFailure = PYTEST_VERBOSE_FAILED.matchEntire(trimmed)
            if (verboseFailure != null) {
                addPytestFailure(
                    drafts = drafts,
                    testId = verboseFailure.groupValues[1],
                    message = "",
                    status = verboseFailure.groupValues[2],
                    traces = traces,
                    errors = errorTexts,
                )
            }
        }

        if (!summarySeen) {
            passed = countOccurrences(output, Regex("""\bPASSED\b"""))
            skipped = countOccurrences(output, Regex("""\bSKIPPED\b"""))
            failed = drafts.count { it.test != null }
        }
        return TestRunSummary(TestFramework.PYTEST, passed, failed, skipped, finalize(drafts, root), durationMs, "")
    }

    private fun addPytestFailure(
        drafts: MutableList<Draft>,
        testId: String,
        message: String,
        status: String,
        traces: List<TraceLine>,
        errors: Map<String, String>,
    ) {
        val file = if (testId.contains("::")) testId.substringBefore("::") else testId
        val func = testId.substringAfter("::", "").substringBefore("[")
        val trace = lookupTrace(traces, file, func)
        drafts.add(
            Draft(
                file = file.ifBlank { null },
                line = trace?.line,
                test = testId,
                message = message.trim()
                    .ifBlank { errorTextFor(errors, file) ?: "" }
                    .ifBlank { testId + " failed" },
                severity = if (status == "ERROR") "error" else "failure",
            )
        )
    }

    private fun isPytestSummary(line: String): Boolean {
        if (!PYTEST_COUNT.containsMatchIn(line)) return false
        return line.startsWith("=") || PYTEST_DURATION.containsMatchIn(line)
    }

    private fun lookupTrace(traces: List<TraceLine>, file: String, func: String): TraceLine? {
        val candidates = traces.filter { pathMatches(it.file, file) }
        if (candidates.isEmpty()) return null
        if (func.isNotEmpty()) {
            val named = candidates.lastOrNull {
                it.rest.startsWith("in ") && it.rest.removePrefix("in ").startsWith(func)
            }
            if (named != null) return named
        }
        return candidates.last()
    }

    private fun errorTextFor(errors: Map<String, String>, file: String): String? {
        errors[file]?.let { return it }
        return errors.entries.firstOrNull { pathMatches(it.key, file) }?.value
    }

    // ---------- jest ----------

    private fun parseJest(output: String, root: String?): TestRunSummary {
        val drafts = ArrayList<Draft>()
        var passed = 0
        var failed = 0
        var skipped = 0
        var durationMs: Long? = null
        var currentTest: String? = null
        var currentMessage: String? = null
        var currentFile: String? = null
        var currentLine: Int? = null

        fun flush() {
            val test = currentTest ?: return
            drafts += Draft(
                file = currentFile,
                line = currentLine,
                test = test,
                message = currentMessage ?: (test + " failed"),
                severity = "failure",
            )
            currentTest = null
            currentMessage = null
            currentFile = null
            currentLine = null
        }

        for (raw in output.lineSequence()) {
            val line = stripAnsi(raw)
            val trimmed = line.trim()

            val section = JEST_SECTION.matchEntire(line)
            if (section != null) {
                flush()
                val title = section.groupValues[1].trim()
                currentTest = if (title.startsWith("Console")) null else title
                continue
            }

            val testsSummary = JEST_TESTS_SUMMARY.matchEntire(trimmed)
            if (testsSummary != null) {
                flush()
                for (count in JEST_COUNT.findAll(testsSummary.groupValues[1])) {
                    val value = count.groupValues[1].toIntOrNull() ?: 0
                    when (count.groupValues[2]) {
                        "passed" -> passed = value
                        "failed" -> failed = value
                        "skipped", "todo", "pending" -> skipped = value
                    }
                }
                continue
            }

            val time = JEST_TIME.matchEntire(trimmed)
            if (time != null) {
                durationMs = secondsToMillis(time.groupValues[1])
                continue
            }

            if (currentTest == null) continue
            if (currentFile == null) {
                val location = JEST_LOCATION.find(line)
                if (location != null) {
                    val locationFile = location.groupValues[1]
                    if (!locationFile.contains("node_modules")) {
                        currentFile = locationFile
                        currentLine = location.groupValues[2].toIntOrNull()
                    }
                }
            }
            if (currentMessage == null && trimmed.isNotEmpty()) {
                val skip = trimmed.startsWith("at ") ||
                    trimmed.startsWith("●") ||
                    trimmed.startsWith(">") ||
                    JEST_CODE_FRAME.containsMatchIn(line)
                if (!skip) currentMessage = trimmed
            }
        }
        flush()

        return TestRunSummary(TestFramework.JEST, passed, failed, skipped, finalize(drafts, root), durationMs, "")
    }

    // ---------- go ----------

    private fun parseGo(output: String, root: String?): TestRunSummary {
        val drafts = ArrayList<Draft>()
        var passed = 0
        var failed = 0
        var skipped = 0
        var durationMs: Long? = null
        var current: String? = null
        val messaged = HashSet<String>()

        for (raw in output.lineSequence()) {
            val line = stripAnsi(raw)

            val result = GO_TEST_RESULT.find(line)
            if (result != null) {
                val name = result.groupValues[2]
                when (result.groupValues[1]) {
                    "PASS" -> passed++
                    "SKIP" -> skipped++
                    "FAIL" -> {
                        failed++
                        if (!messaged.contains(name)) {
                            drafts += Draft(null, null, name, name + " FAILED", "failure")
                        }
                    }
                }
                current = name
                continue
            }

            val run = GO_RUN.find(line)
            if (run != null) {
                current = run.groupValues[1]
                continue
            }

            val message = GO_MESSAGE.find(line)
            if (message != null) {
                drafts += Draft(
                    file = message.groupValues[1],
                    line = message.groupValues[2].toIntOrNull(),
                    test = current,
                    message = message.groupValues[3],
                    severity = "failure",
                )
                current?.let { messaged.add(it) }
                continue
            }

            val packageResult = GO_PACKAGE_RESULT.find(line)
            if (packageResult != null) {
                durationMs = secondsToMillis(packageResult.groupValues[2])
            }
        }
        return TestRunSummary(TestFramework.GO, passed, failed, skipped, finalize(drafts, root), durationMs, "")
    }

    // ---------- cargo ----------

    private fun parseCargo(output: String, root: String?): TestRunSummary {
        val drafts = ArrayList<Draft>()
        var passed = 0
        var failed = 0
        var skipped = 0
        var currentTest: String? = null
        val lines = output.lineSequence().map { stripAnsi(it) }.toList()

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()

            val result = CARGO_RESULT.find(trimmed)
            if (result != null) {
                passed += result.groupValues[1].toIntOrNull() ?: 0
                failed += result.groupValues[2].toIntOrNull() ?: 0
                skipped += result.groupValues[3].toIntOrNull() ?: 0
                index++
                continue
            }

            val stdoutHeader = CARGO_STDOUT_HEADER.find(line)
            if (stdoutHeader != null) {
                currentTest = stdoutHeader.groupValues[1]
                index++
                continue
            }

            val panic = CARGO_PANIC.find(line)
            if (panic != null) {
                var message = panic.groupValues[2].trim()
                if (message.isEmpty()) {
                    var next = index + 1
                    while (next < lines.size && lines[next].isBlank()) next++
                    if (next < lines.size) message = lines[next].trim()
                }
                val test = panic.groupValues[1].ifBlank { currentTest.orEmpty() }.ifBlank { null }
                drafts += Draft(
                    file = panic.groupValues[3],
                    line = panic.groupValues[4].toIntOrNull(),
                    test = test,
                    message = message.ifBlank { "panicked" },
                    severity = "failure",
                )
            }
            index++
        }
        return TestRunSummary(TestFramework.CARGO, passed, failed, skipped, finalize(drafts, root), null, "")
    }

    // ---------- 公共工具 ----------

    private class Draft(
        var file: String?,
        var line: Int?,
        var test: String?,
        var message: String,
        var severity: String = "error",
    )

    /** 统一做路径相对化、(file,line,message) 去重与 50 条上限 */
    private fun finalize(drafts: List<Draft>, root: String?): List<TestDiagnostic> {
        val seen = HashMap<String, Int>()
        val result = ArrayList<TestDiagnostic>(minOf(drafts.size, MAX_DIAGNOSTICS))
        for (draft in drafts) {
            val file = draft.file?.trim()?.ifEmpty { null }?.let { relativize(it, root) }
            val message = normalizeMessage(draft.message).ifBlank { draft.test ?: "test failed" }
            // 同一条用例可能出现两次：跑动时的 "xxx FAILED" 和末尾的 short test summary。
            // 按用例去重，保留信息量更大的那条。
            val key = draft.test?.let { file.toString() + "|" + it }
                ?: (file.toString() + "|" + draft.line + "|" + message)
            val existing = seen[key]
            if (existing != null) {
                val current = result[existing]
                if (message.length > current.message.length) {
                    result[existing] = TestDiagnostic(file, draft.line, draft.test, message, draft.severity)
                }
                continue
            }
            seen[key] = result.size
            result.add(TestDiagnostic(file, draft.line, draft.test, message, draft.severity))
            if (result.size >= MAX_DIAGNOSTICS) break
        }
        return result
    }

    private fun normalizeMessage(text: String): String {
        val normalized = stripAnsi(text).let { WHITESPACE.replace(it, " ") }.trim()
        return if (normalized.length > MAX_MESSAGE_LENGTH) {
            normalized.take(MAX_MESSAGE_LENGTH).trim()
        } else {
            normalized
        }
    }

    private fun stripAnsi(text: String): String =
        if (text.indexOf('\u001B') >= 0) ANSI_ESCAPE.replace(text, "") else text

    /** 绝对路径在 workspaceRoot 下转相对路径，否则原样保留 */
    private fun relativize(path: String, root: String?): String {
        var result = path.removePrefix("file://").replace('\\', '/')
        while (result.startsWith("./")) result = result.removePrefix("./")
        if (root.isNullOrBlank()) return result
        val base = root.replace('\\', '/').trimEnd('/')
        if (base.isEmpty()) return result
        if (result == base) return result
        val prefix = base + "/"
        if (result.startsWith(prefix)) result = result.substring(prefix.length)
        return result
    }

    private fun pathMatches(a: String, b: String): Boolean {
        if (a == b) return true
        val left = a.replace('\\', '/')
        val right = b.replace('\\', '/')
        return left.endsWith("/" + right) || right.endsWith("/" + left)
    }

    private fun isSourceFile(path: String): Boolean =
        path.endsWith(".kt") || path.endsWith(".java") || path.endsWith(".scala") || path.endsWith(".groovy")

    private fun sameTestClass(left: String, right: String): Boolean {
        val a = left.substringAfterLast('.')
        val b = right.substringAfterLast('.')
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.startsWith(b) || b.startsWith(a)
    }

    private fun secondsToMillis(value: String): Long? =
        value.toDoubleOrNull()?.let { Math.round(it * 1000.0) }

    private fun countOccurrences(text: String, pattern: Regex): Int = pattern.findAll(text).count()

    private fun attrValue(attrs: String, name: String): String {
        for (match in JUNIT_ATTR.findAll(attrs)) {
            if (match.groupValues[1] == name) return unescapeXml(match.groupValues[2])
        }
        return ""
    }

    private fun findStackFrame(text: String, className: String): MatchResult? {
        val frames = JUNIT_STACK.findAll(text).toList()
        if (frames.isEmpty()) return null
        if (className.isNotBlank()) {
            val named = frames.firstOrNull { sameTestClass(it.groupValues[1], className) }
            if (named != null) return named
        }
        return frames.firstOrNull { isSourceFile(it.groupValues[3]) } ?: frames.first()
    }

    private fun unescapeXml(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#10;", " ")
        .replace("&#13;", " ")
        .replace("&amp;", "&")
}

/**
 * 从改动文件推导「只跑相关测试」的候选参数，纯函数便于测试。
 */
object ChangedTestSelector {
    private const val MAX_TARGETS = 20

    /**
     * @param changedPaths 相对仓库根的改动文件路径（已去重）
     * @param existingPaths 仓库里已知存在的文件路径，可为空
     * @return 建议的测试目标，无法推导时返回空列表
     */
    fun gradleTestFilters(changedPaths: List<String>, existingPaths: Set<String> = emptySet()): List<String> {
        val result = LinkedHashSet<String>()
        for (raw in changedPaths) {
            val path = normalizePath(raw)
            if (!path.endsWith(".kt") && !path.endsWith(".java")) continue
            if (path.contains("build/") || path.startsWith("build/")) continue
            if (path.contains("src/test/resources/") || path.contains("src/androidTest/resources/")) continue
            if (path.contains("/resources/")) continue
            val name = path.substringAfterLast('/').substringBeforeLast('.')
            if (name.isEmpty()) continue
            result.add("*" + name + "*")
            if (result.size >= MAX_TARGETS) break
        }
        return result.toList()
    }

    fun pytestTargets(changedPaths: List<String>, existingPaths: Set<String> = emptySet()): List<String> {
        val existing = existingPaths.map { normalizePath(it) }.toSet()
        val result = LinkedHashSet<String>()
        for (raw in changedPaths) {
            val path = normalizePath(raw)
            if (!path.endsWith(".py")) continue
            val name = path.substringAfterLast('/')
            val isTestFile = name.startsWith("test_") ||
                name.endsWith("_test.py") ||
                path.startsWith("tests/") ||
                path.contains("/tests/")
            if (isTestFile) {
                result.add(path)
            } else {
                val target = "tests/test_" + name
                // 常规位置优先；现有文件集合只用于确认该约定文件确实存在
                result.add(if (existing.isEmpty() || existing.contains(target)) target else target)
            }
            if (result.size >= MAX_TARGETS) break
        }
        return result.toList()
    }

    private fun normalizePath(path: String): String {
        var result = path.trim().replace('\\', '/')
        while (result.startsWith("./")) result = result.removePrefix("./")
        return result
    }
}
