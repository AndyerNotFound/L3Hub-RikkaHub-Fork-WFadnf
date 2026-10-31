package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.workspace.ChangedTestSelector
import me.rerere.rikkahub.data.workspace.TestFramework
import me.rerere.rikkahub.data.workspace.TestOutputParser
import me.rerere.rikkahub.data.workspace.WorkspaceGit
import me.rerere.rikkahub.data.workspace.shQuote

const val WORKSPACE_TEST_TOOL_NAME = "workspace_test"

private const val TEST_TIMEOUT_DEFAULT_SECONDS = 600L
private const val TEST_TIMEOUT_MAX_SECONDS = 1800L

/**
 * 跑测试并把输出解析成结构化 diagnostics。
 * 与 workspace_shell 的区别：结果里有 文件:行:错误，聊天页可以点开对应文件；
 * only_changed=true 时用 git 改动推导要跑的测试，不用每次全量。
 */
internal fun createTestTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
): Tool = Tool(
    name = WORKSPACE_TEST_TOOL_NAME,
    description = """
        Run the project's tests in the bound workspace and return structured diagnostics (file, line, test name, message) instead of raw stdout.
        Give the command you would normally run (./gradlew test, pytest -q, npm test, go test ./..., cargo test); the framework is detected from it.
        Set only_changed=true to run just the tests related to the files changed in git (Kotlin/Java: --tests filters, pytest: matching test files).
        Prefer this over workspace_shell when you need to know which tests failed and where.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "Test command, e.g. ./gradlew :app:testDebugUnitTest --offline")
                })
                put("cwd", buildJsonObject {
                    put("type", "string")
                    put("description", "Working directory relative to the workspace files root. Defaults to the repo root.")
                })
                put("timeout", buildJsonObject {
                    put("type", "integer")
                    put(
                        "description",
                        "Timeout in seconds. Defaults to $TEST_TIMEOUT_DEFAULT_SECONDS, max $TEST_TIMEOUT_MAX_SECONDS.",
                    )
                })
                put("framework", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        listOf("auto", "gradle", "pytest", "jest", "go", "cargo").forEach {
                            add(kotlinx.serialization.json.JsonPrimitive(it))
                        }
                    })
                })
                put("only_changed", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Only run tests related to files changed in git. Defaults to false.")
                })
                put("paths", buildJsonObject {
                    put("type", "array")
                    put("description", "Override the changed-file list used by only_changed")
                    put("items", buildJsonObject { put("type", "string") })
                })
            },
            required = listOf("command"),
        )
    },
    needsApproval = { params ->
        val target = params.jsonObject.resolveWorkspaceOrNull(targets)
        target == null || target.needsApproval(WORKSPACE_TEST_TOOL_NAME)
    },
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        val baseCommand = params.str("command") ?: error("command is required")
        val cwd = params.str("cwd").orEmpty()
        val timeoutSeconds = params.str("timeout")?.toLongOrNull()
            ?.coerceIn(1L, TEST_TIMEOUT_MAX_SECONDS)
            ?: TEST_TIMEOUT_DEFAULT_SECONDS
        val requestedFramework = params.str("framework")?.uppercase()?.let { name ->
            runCatching { TestFramework.valueOf(name) }.getOrDefault(TestFramework.AUTO)
        } ?: TestFramework.AUTO
        val onlyChanged = params["only_changed"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val explicitPaths = (params["paths"] as? JsonArray)
            ?.mapNotNull { element -> element.jsonPrimitive.contentOrNull }
            .orEmpty()

        val git = WorkspaceGit(workspaceRepository)
        val framework = if (requestedFramework == TestFramework.AUTO) {
            TestOutputParser.detectFramework(baseCommand)
        } else {
            requestedFramework
        }

        var command = baseCommand
        var changedFiles: List<String> = emptyList()
        if (onlyChanged) {
            changedFiles = explicitPaths.ifEmpty {
                runCatching { git.status(target.id, cwd).files.map { it.path } }.getOrDefault(emptyList())
            }
            // 递归列目录代价太高，这里只按命名规则推导；目标文件不存在时命令会自己报错
            val existing = emptySet<String>()
            val extra = when (framework) {
                TestFramework.GRADLE -> ChangedTestSelector.gradleTestFilters(changedFiles, existing)
                    .joinToString("") { " --tests ${it.shQuote()}" }

                TestFramework.PYTEST -> ChangedTestSelector.pytestTargets(changedFiles, existing)
                    .joinToString(" ") { it.shQuote() }

                else -> ""
            }
            if (extra.isBlank()) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("ok", false)
                            put("error", "only_changed is not supported for this command or no changed files were found")
                            put("framework", framework.name.lowercase())
                            put("changedFiles", buildJsonArray {
                                changedFiles.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                            })
                            put("hint", "Run the full command with only_changed=false instead.")
                        }.toString()
                    )
                )
            }
            command = baseCommand + extra
        }

        val result = workspaceRepository.executeCommand(
            id = target.id,
            command = command,
            cwd = cwd,
            timeoutMillis = timeoutSeconds * 1000,
        )
        val output = buildString {
            append(result.stdout)
            if (result.stderr.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(result.stderr)
            }
        }
        val root = workspaceRepository.layout(target.id)?.workspaceDir
        val summary = TestOutputParser.parse(framework, output, root)
        val payload = buildJsonObject {
            put("ok", result.exitCode == 0 && !result.timedOut && summary.failed == 0)
            put("exitCode", result.exitCode)
            put("timedOut", result.timedOut)
            put("framework", summary.framework.name.lowercase())
            put("command", command)
            put("passed", summary.passed)
            put("failed", summary.failed)
            put("skipped", summary.skipped)
            summary.durationMs?.let { put("durationMs", it) }
            put("diagnostics", buildJsonArray {
                summary.diagnostics.forEach { diagnostic ->
                    add(buildJsonObject {
                        diagnostic.file?.let { put("file", it) }
                        diagnostic.line?.let { put("line", it) }
                        diagnostic.test?.let { put("test", it) }
                        put("message", diagnostic.message)
                        put("severity", diagnostic.severity)
                    })
                }
            })
            put("output", summary.rawTail)
        }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)

private fun kotlinx.serialization.json.JsonObject.str(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull
