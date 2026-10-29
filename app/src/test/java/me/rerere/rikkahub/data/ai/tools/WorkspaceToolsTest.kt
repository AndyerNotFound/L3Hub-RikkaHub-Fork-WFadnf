package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.workspace.WorkspaceLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            layout = layout,
            approvalOverrides = emptyMap(),
            supportsFileWrite = true,
        ),
        WorkspaceTarget(
            id = "fbec1ebe-9980-49cf-8891-d79a0b2c3a64",
            name = "Termux WorkSpace",
            layout = layout,
            approvalOverrides = emptyMap(),
            supportsFileWrite = true,
        ),
    )

    private fun select(workspace: String?) = buildJsonObject {
        if (workspace != null) put("workspace", workspace)
    }.resolveWorkspace(targets)

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
    fun `approval path never throws on unknown workspace`() {
        val json = buildJsonObject { put("workspace", "不存在") }
        assertNull(json.resolveWorkspaceOrNull(targets))
    }

    @Test
    fun `single workspace ignores the selector`() {
        val single = listOf(targets.first())
        val json = buildJsonObject { put("workspace", "随便写") }
        assertEquals(targets.first(), json.resolveWorkspace(single))
    }
}
