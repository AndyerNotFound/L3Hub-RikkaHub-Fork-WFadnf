package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.ExecutionPermission
import me.rerere.rikkahub.data.model.WorkspaceToolMode
import me.rerere.workspace.WorkspaceLayout
import me.rerere.workspace.WorkspaceShellStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceToolsTest {
    private val layout = WorkspaceLayout(
        workspaceDir = "/root/projects",
        writableRoots = listOf("/root/projects"),
    )

    private val targets = listOf(
        WorkspaceTarget(
            id = "651003f8-112e-45ec-bd47-16f4407be0be",
            name = "\"蓝孩\"赠送的服务器",
            readyLayout = layout,
            toolModes = emptyMap(),
            supportsFileWrite = true,
        ),
        WorkspaceTarget(
            id = "fbec1ebe-9980-49cf-8891-d79a0b2c3a64",
            name = "Termux WorkSpace",
            readyLayout = layout,
            toolModes = emptyMap(),
            supportsFileWrite = true,
        ),
    )

    private fun args(workspace: String? = null) = buildJsonObject {
        if (workspace != null) put("workspace", workspace)
    }

    private fun select(workspace: String?) = scope().resolve(args(workspace))

    @Test
    fun `omitted workspace uses the first target`() {
        assertEquals(targets.first(), select(null))
        assertEquals(targets.first(), select("  "))
    }

    @Test
    fun `exact id and name match`() {
        assertEquals(targets[1], select("fbec1ebe-9980-49cf-8891-d79a0b2c3a64"))
        assertEquals(targets[1], select("Termux WorkSpace"))
        assertEquals(targets.first(), select("\"蓝孩\"赠送的服务器"))
    }

    @Test
    fun `name with dropped or full width quotes still matches`() {
        assertEquals(targets.first(), select("蓝孩\"赠送的服务器"))
        assertEquals(targets.first(), select("“蓝孩”赠送的服务器"))
        assertEquals(targets.first(), select(" '蓝孩‘赠送的服务器 "))
    }

    @Test
    fun `matching ignores case and extra spaces`() {
        assertEquals(targets[1], select("termux   workspace"))
    }

    @Test
    fun `unique id prefix matches`() {
        assertEquals(targets[1], select("fbec1ebe"))
    }

    @Test
    fun `unknown workspace reports available ids`() {
        val error = runCatching { select("不存在的工区") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("651003f8-112e-45ec-bd47-16f4407be0be"))
        assertTrue(error.message!!.contains("fbec1ebe-9980-49cf-8891-d79a0b2c3a64"))
        assertTrue(error.message!!.contains("Use the workspace ID"))
    }

    @Test
    fun `unknown workspace does not ask for approval`() {
        assertFalse(scope().needsApproval(args("不存在")))
    }

    @Test
    fun `a single workspace still validates an explicit selector`() {
        val single = scope(bound = listOf(targets.first()))
        assertEquals(targets.first(), single.resolve(args()))
        assertEquals(targets.first(), single.resolve(args(targets.first().id)))
        assertThrows(IllegalStateException::class.java) { single.resolve(args(targets.last().id)) }
    }

    @Test
    fun `only enabled targets are published and disabled selections are rejected`() {
        val first = targets.first().copy(toolModes = mapOf(WORKSPACE_SHELL_TOOL_NAME to WorkspaceToolMode.DISABLED))
        val second = targets.last()
        val scope = scope(bound = listOf(first, second))

        assertEquals(listOf(second), scope.enabled)
        assertFalse(scope.workspaceNote().contains(first.name))
        assertEquals(listOf("command", "workspace"), scope.required("command"))
        for (selector in listOf(first.id, first.name, first.id.take(8), null)) {
            val error = assertThrows(IllegalStateException::class.java) { scope.resolve(args(selector)) }
            assertTrue(error.message.orEmpty().contains("disabled or unavailable"))
        }
        assertEquals(second, scope.resolve(args(second.id)))
    }

    @Test
    fun `disabled targets are not removed before resolving ambiguous names`() {
        val first = targets.first().copy(toolModes = mapOf(WORKSPACE_SHELL_TOOL_NAME to WorkspaceToolMode.DISABLED))
        val second = targets.last().copy(name = first.name)
        val scope = scope(bound = listOf(first, second))
        assertThrows(IllegalStateException::class.java) { scope.resolve(args(first.name)) }
        assertEquals(second, scope.resolve(args(second.id)))
    }

    @Test
    fun `unavailable default is not redirected to the remaining workspace`() {
        val second = targets.last()
        val scope = scope(bound = listOf(second))
        assertThrows(IllegalStateException::class.java) { scope.resolve(args()) }
        assertEquals(second, scope.resolve(args(second.id)))
    }

    @Test
    fun `an unready workspace is still included in name and prefix resolution`() {
        val first = targets.first().copy(readyLayout = null)
        val nameMatchingPrefix = targets.last().copy(name = first.id.take(8))
        val scope = scope(bound = listOf(first, nameMatchingPrefix))
        assertEquals(listOf(nameMatchingPrefix), scope.enabled)
        val unready = assertThrows(IllegalStateException::class.java) { scope.resolve(args(first.name)) }
        assertTrue(unready.message.orEmpty().contains(first.id))
        val ambiguous = assertThrows(IllegalStateException::class.java) { scope.resolve(args(nameMatchingPrefix.name)) }
        assertTrue(ambiguous.message.orEmpty().contains("Ambiguous workspace"))
        assertEquals(nameMatchingPrefix, scope.resolve(args(nameMatchingPrefix.id)))
    }

    @Test
    fun `closing a tool blocks an already queued invocation without changing its target`() = runTest {
        val first = targets.first()
        val second = targets.last()
        val requested = mutableListOf<String>()
        var persisted = WorkspaceEntity(
            id = first.id,
            name = first.name,
            root = first.id,
            shellStatus = WorkspaceShellStatus.READY.name,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val advertised = scope(loadWorkspace = { id ->
            requested.add(id)
            if (id == first.id) persisted else null
        })
        assertEquals(first, advertised.resolveForExecution(args()))

        persisted = persisted.copy(toolApprovals = """{"workspace_shell":"DISABLED"}""")
        val failure = runCatching { advertised.resolveForExecution(args()) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("disabled or unavailable"))
        assertEquals(listOf(first.id, first.id), requested)

        val resumed = scope(bound = listOf(first.copy(toolModes = persisted.toolModes()), second))
        assertThrows(IllegalStateException::class.java) { resumed.resolve(args()) }
    }

    @Test
    fun `all disabled prevents publication even in full access`() {
        val bound = targets.map { target ->
            target.copy(
                executionPermission = ExecutionPermission.FULL,
                toolModes = mapOf(WORKSPACE_SHELL_TOOL_NAME to WorkspaceToolMode.DISABLED),
            )
        }
        val scope = scope(bound = bound)
        assertTrue(scope.enabled.isEmpty())
        assertFalse(scope.needsApproval(args()))
    }

    private fun scope(
        tool: String = WORKSPACE_SHELL_TOOL_NAME,
        bound: List<WorkspaceTarget> = targets,
        loadWorkspace: suspend (String) -> WorkspaceEntity? = { null },
    ) = WorkspaceToolTargets(tool, bound, targets.first().id, loadWorkspace)

    private fun withPermission(permission: ExecutionPermission) =
        targets.first().copy(executionPermission = permission)

    @Test
    fun `approval tier applies defaults including read only shell and git actions`() {
        val target = withPermission(ExecutionPermission.APPROVAL)
        assertTrue(target.needsApproval("workspace_shell"))
        assertTrue(scope(tool = WORKSPACE_GIT_TOOL_NAME).needsApproval(buildJsonObject { put("action", "status") }))
        assertFalse(target.needsApproval("workspace_write_file"))
        assertTrue(target.requiresShellApproval("ls -la"))
        assertTrue(target.requiresShellApproval("npm install"))
    }

    @Test
    fun `automatic shell commands still require confirmation when dangerous`() {
        val target = withPermission(ExecutionPermission.APPROVAL)
            .copy(toolModes = mapOf(WORKSPACE_SHELL_TOOL_NAME to WorkspaceToolMode.AUTO))
        assertFalse(target.requiresShellApproval("ls -la"))
        assertFalse(target.requiresShellApproval("npm install"))
        assertTrue(target.requiresShellApproval("sudo reboot"))
    }

    @Test
    fun `approval tier honors write overrides and protects writes outside writable roots`() {
        val target = withPermission(ExecutionPermission.APPROVAL)
        assertFalse(target.requiresWriteApproval("workspace_write_file", outsideWritableRoots = false))
        assertTrue(target.requiresWriteApproval("workspace_write_file", outsideWritableRoots = true))
        val withOverride = target.copy(toolModes = mapOf("workspace_write_file" to WorkspaceToolMode.APPROVAL))
        assertTrue(withOverride.requiresWriteApproval("workspace_write_file", outsideWritableRoots = false))
    }

    @Test
    fun `read only tier leaves shell restrictions to the execution check`() {
        val target = withPermission(ExecutionPermission.READ_ONLY)
        assertFalse(target.allowsTool("workspace_edit_file"))
        assertFalse(target.allowsTool(WORKSPACE_TEST_TOOL_NAME))
        assertTrue(target.allowsTool(WORKSPACE_GIT_TOOL_NAME))
        assertTrue(target.requiresWriteApproval("workspace_edit_file", outsideWritableRoots = false))
        assertFalse(target.requiresShellApproval("ls -la"))
        assertFalse(target.requiresShellApproval("npm install"))
    }

    @Test
    fun `full tier never asks`() {
        val target = withPermission(ExecutionPermission.FULL)
        assertFalse(target.requiresWriteApproval("workspace_write_file", outsideWritableRoots = true))
        assertFalse(target.requiresShellApproval("sudo rm -rf /"))
        assertFalse(target.needsApproval("workspace_git"))
    }
}
