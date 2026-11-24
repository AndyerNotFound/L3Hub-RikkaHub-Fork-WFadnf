package me.rerere.rikkahub.data.model

import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceToolModeTest {
    // 旧版 tool_approvals 只存布尔值: true 表示需要确认, false 表示直接执行
    @Test
    fun `legacy boolean values map to approval and auto`() {
        val modes = decodeWorkspaceToolModes("""{"workspace_shell":true,"workspace_write_file":false}""")
        assertEquals(WorkspaceToolMode.APPROVAL, modes["workspace_shell"])
        assertEquals(WorkspaceToolMode.AUTO, modes["workspace_write_file"])
    }

    @Test
    fun `mixed legacy booleans and enum names decode independently`() {
        val modes = decodeWorkspaceToolModes(
            """
            {
              "workspace_read_file": true,
              "workspace_list": false,
              "workspace_glob": "DISABLED",
              "workspace_grep": "APPROVAL",
              "workspace_repo_map": "AUTO"
            }
            """.trimIndent(),
        )
        assertEquals(WorkspaceToolMode.APPROVAL, modes["workspace_read_file"])
        assertEquals(WorkspaceToolMode.AUTO, modes["workspace_list"])
        assertEquals(WorkspaceToolMode.DISABLED, modes["workspace_glob"])
        assertEquals(WorkspaceToolMode.APPROVAL, modes["workspace_grep"])
        assertEquals(WorkspaceToolMode.AUTO, modes["workspace_repo_map"])
    }

    @Test
    fun `missing modes fall back to the legacy default table`() {
        // 与旧版 WorkspaceToolDefaultApprovals 逐项对齐: false -> AUTO, true -> APPROVAL
        val legacyDefaults = mapOf(
            "workspace_read_file" to WorkspaceToolMode.AUTO,
            "workspace_list" to WorkspaceToolMode.AUTO,
            "workspace_glob" to WorkspaceToolMode.AUTO,
            "workspace_grep" to WorkspaceToolMode.AUTO,
            "workspace_repo_map" to WorkspaceToolMode.AUTO,
            "workspace_write_file" to WorkspaceToolMode.AUTO,
            "workspace_edit_file" to WorkspaceToolMode.AUTO,
            "workspace_apply_patch" to WorkspaceToolMode.AUTO,
            "workspace_shell" to WorkspaceToolMode.APPROVAL,
            "workspace_git" to WorkspaceToolMode.APPROVAL,
            "workspace_test" to WorkspaceToolMode.APPROVAL,
        )
        assertEquals(legacyDefaults, WorkspaceToolDefaults)

        val empty = decodeWorkspaceToolModes("{}")
        assertTrue(empty.isEmpty())
        legacyDefaults.forEach { (tool, mode) -> assertEquals(tool, mode, empty.toolMode(tool)) }
        assertEquals(WorkspaceToolMode.DISABLED, empty.toolMode("workspace_unknown_tool"))
    }

    @Test
    fun `stored overrides win over the default table`() {
        val modes = decodeWorkspaceToolModes("""{"workspace_shell":"AUTO","workspace_read_file":"DISABLED"}""")
        assertEquals(WorkspaceToolMode.AUTO, modes.toolMode("workspace_shell"))
        assertEquals(WorkspaceToolMode.DISABLED, modes.toolMode("workspace_read_file"))
        // 未写入的工具仍然读默认值
        assertEquals(WorkspaceToolMode.APPROVAL, modes.toolMode("workspace_git"))
    }

    @Test
    fun `enum modes and other tools survive a round trip`() {
        val modes = mapOf(
            "workspace_read_file" to WorkspaceToolMode.DISABLED,
            "workspace_shell" to WorkspaceToolMode.AUTO,
            "workspace_git" to WorkspaceToolMode.APPROVAL,
            "workspace_future_tool" to WorkspaceToolMode.AUTO,
        )
        val encoded = JsonInstant.encodeToString<Map<String, WorkspaceToolMode>>(modes)
        // 重新编码只写枚举名, 不会再退回旧版的布尔值
        assertFalse(encoded.contains("true"))
        assertFalse(encoded.contains("false"))
        assertEquals(modes, decodeWorkspaceToolModes(encoded))
        WorkspaceToolMode.entries.forEach { mode ->
            val single = mapOf("workspace_shell" to mode)
            val roundTrip = JsonInstant.encodeToString<Map<String, WorkspaceToolMode>>(single)
            assertEquals(single, decodeWorkspaceToolModes(roundTrip))
        }
    }

    @Test
    fun `malformed json fails closed for every known tool`() {
        listOf(
            "",
            "   ",
            "null",
            "[]",
            "[true]",
            "42",
            "\"APPROVAL\"",
            "{\"workspace_shell\":",
            "{'workspace_shell': true}",
        ).forEach { raw ->
            val modes = decodeWorkspaceToolModes(raw)
            WorkspaceToolDefaults.keys.forEach { tool ->
                assertEquals("raw=<$raw> tool=$tool", WorkspaceToolMode.DISABLED, modes.toolMode(tool))
            }
        }
    }

    @Test
    fun `unrecognized values fail closed instead of re-enabling a tool`() {
        val modes = decodeWorkspaceToolModes(
            """
            {
              "workspace_read_file": "FULL",
              "workspace_write_file": "auto",
              "workspace_edit_file": 1,
              "workspace_apply_patch": null,
              "workspace_shell": {"mode": "AUTO"},
              "workspace_git": ["AUTO"],
              "workspace_test": ""
            }
            """.trimIndent(),
        )
        listOf(
            "workspace_read_file",
            "workspace_write_file",
            "workspace_edit_file",
            "workspace_apply_patch",
            "workspace_shell",
            "workspace_git",
            "workspace_test",
        ).forEach { tool ->
            assertEquals(tool, WorkspaceToolMode.DISABLED, modes.toolMode(tool))
        }
    }

    @Test
    fun `three clicks wrap back to the starting mode`() {
        assertEquals(WorkspaceToolMode.APPROVAL, WorkspaceToolMode.DISABLED.next())
        assertEquals(WorkspaceToolMode.AUTO, WorkspaceToolMode.APPROVAL.next())
        assertEquals(WorkspaceToolMode.DISABLED, WorkspaceToolMode.AUTO.next())
        WorkspaceToolMode.entries.forEach { start ->
            var mode = start
            repeat(3) { mode = mode.next() }
            assertEquals(start, mode)
        }
    }

    @Test
    fun `three clicks on a decoded tool keep the stored mode unchanged`() {
        // 与 WorkspaceRepository.cycleToolMode 相同的推进方式: 先读 toolMode 再 next
        val stored = decodeWorkspaceToolModes("""{"workspace_shell":false}""")
        val start = stored.toolMode("workspace_shell")
        assertEquals(WorkspaceToolMode.AUTO, start)
        var current = stored
        repeat(3) {
            current = current + ("workspace_shell" to current.toolMode("workspace_shell").next())
        }
        assertEquals(start, current.toolMode("workspace_shell"))
        assertEquals(WorkspaceToolMode.APPROVAL, current.toolMode("workspace_git"))
    }
}
