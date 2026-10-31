package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCommandPolicyTest {

    @Test
    fun `read only commands run without approval`() {
        listOf(
            "ls -la",
            "pwd",
            "git status",
            "git diff --stat HEAD",
            "git log --oneline -5",
            "git show HEAD:app/build.gradle.kts",
            "grep -rn TODO app/src",
            "rg -n \"fun main\" --glob '*.kt'",
            "cat build.gradle.kts | head -40",
            "find . -name '*.kt' -type f",
            "wc -l README.md",
            "sort -u names.txt",
            "jq '.name' package.json",
            "sha256sum app/build.gradle.kts",
            "ls; pwd",
        ).forEach { command ->
            assertTrue(command, WorkspaceCommandPolicy.isReadOnly(command))
        }
    }

    @Test
    fun `mutating or executing commands still need approval`() {
        listOf(
            "rm -rf /",
            "ls; rm -rf /",
            "ls && rm -rf /",
            "cat \$(whoami)",
            "cat `id`",
            "echo hi > file",
            "echo hi >> file",
            "find . -delete",
            "find . -exec rm {} \\;",
            "sed -i s/a/b/ file",
            "git commit -m x",
            "git checkout main",
            "git -c alias.p=!sh p",
            "rg --pre ./evil x",
            "sort -o out in",
            "sort --compress-program=sh -u in",
            "sed -n '1,40p' README.md",
            "sed -n '1e id' README.md",
            "git diff --ext-diff",
            "uniq in out",
            "bash -c 'ls'",
            "python3 -c 'print(1)'",
            "ls | xargs rm",
            "cd /tmp",
            "sudo ls",
        ).forEach { command ->
            assertFalse(command, WorkspaceCommandPolicy.isReadOnly(command))
        }
    }

    @Test
    fun `double quoted dollar still counts as expansion`() {
        assertFalse(WorkspaceCommandPolicy.isReadOnly("echo \"\$(id)\""))
        assertTrue(WorkspaceCommandPolicy.isReadOnly("echo 'a > b'"))
    }
}
