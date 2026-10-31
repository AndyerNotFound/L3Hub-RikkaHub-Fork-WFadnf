package me.rerere.rikkahub.data.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitIgnoreMatcherTest {

    private val root = "/repo"

    private fun rulesOf(baseDir: String, vararg lines: String): List<GitIgnoreRule> =
        GitIgnoreMatcher.parse(baseDir, lines.joinToString("\n"))

    private fun rootRules(vararg lines: String): List<GitIgnoreRule> = rulesOf(root, *lines)

    private fun ignored(
        rules: List<GitIgnoreRule>,
        path: String,
        isDirectory: Boolean = false,
    ): Boolean = GitIgnoreMatcher.isIgnored(rules, path, isDirectory)

    @Test
    fun `directory only pattern matches directories only`() {
        val rules = rootRules("build/")
        assertTrue(rules.single().directoryOnly)
        assertEquals("build", rules.single().pattern)
        assertFalse(rules.single().anchored)

        assertTrue(ignored(rules, "$root/build", isDirectory = true))
        // 文件不匹配
        assertFalse(ignored(rules, "$root/build", isDirectory = false))
        // 任意层级
        assertTrue(ignored(rules, "$root/src/build", isDirectory = true))
        assertFalse(ignored(rules, "$root/src/build", isDirectory = false))
    }

    @Test
    fun `unanchored pattern matches any level`() {
        val rules = rootRules("build")
        assertTrue(ignored(rules, "$root/build", isDirectory = true))
        assertTrue(ignored(rules, "$root/build", isDirectory = false))
        assertTrue(ignored(rules, "$root/a/build", isDirectory = true))
        assertTrue(ignored(rules, "$root/a/b/build", isDirectory = false))
        // 父目录被忽略 -> 子路径被忽略
        assertTrue(ignored(rules, "$root/a/build/out.txt"))
        // 只是前缀相同不算
        assertFalse(ignored(rules, "$root/a/builds"))
        assertFalse(ignored(rules, "$root/rebuild"))
    }

    @Test
    fun `star extension matches any level`() {
        val rules = rootRules("*.log")
        assertTrue(ignored(rules, "$root/a.log"))
        assertTrue(ignored(rules, "$root/x/y/b.log"))
        assertTrue(ignored(rules, "$root/logs/c.log"))
        assertFalse(ignored(rules, "$root/a.txt"))
        assertFalse(ignored(rules, "$root/a.log.txt"))
    }

    @Test
    fun `leading slash anchors to base dir`() {
        val rules = rootRules("/dist")
        val rule = rules.single()
        assertEquals("dist", rule.pattern)
        assertTrue(rule.anchored)

        assertTrue(ignored(rules, "$root/dist", isDirectory = true))
        assertTrue(ignored(rules, "$root/dist/bundle.js"))
        assertFalse(ignored(rules, "$root/app/dist", isDirectory = true))
        assertFalse(ignored(rules, "$root/app/dist/bundle.js"))
    }

    @Test
    fun `star does not cross directories`() {
        val rules = rootRules("docs/*.md")
        assertTrue(rules.single().anchored)
        assertTrue(ignored(rules, "$root/docs/a.md"))
        assertFalse(ignored(rules, "$root/docs/x/a.md"))
        assertFalse(ignored(rules, "$root/docs/a.txt"))
        assertFalse(ignored(rules, "$root/xdocs/a.md"))
    }

    @Test
    fun `double star matches many levels`() {
        val rules = rootRules("**/generated/**")
        assertTrue(ignored(rules, "$root/generated/a.txt"))
        assertTrue(ignored(rules, "$root/a/b/generated/c/d.txt"))
        assertTrue(ignored(rules, "$root/a/generated/x"))
        // 目录本身不被这条规则忽略（只忽略内容）
        assertFalse(ignored(rules, "$root/a/generated", isDirectory = true))
        assertFalse(ignored(rules, "$root/a/generated.txt"))
    }

    @Test
    fun `negation re-includes file`() {
        val rules = rootRules("*.log", "!keep.log")
        assertEquals(2, rules.size)
        assertTrue(rules[1].negated)
        assertEquals("keep.log", rules[1].pattern)

        assertFalse(ignored(rules, "$root/keep.log"))
        assertFalse(ignored(rules, "$root/a/b/keep.log"))
        assertTrue(ignored(rules, "$root/other.log"))
        assertTrue(ignored(rules, "$root/a/other.log"))
    }

    @Test
    fun `comments blanks trailing spaces and escapes`() {
        val rules = GitIgnoreMatcher.parse(
            root,
            listOf(
                "# 注释行",
                "",
                "   ",
                "foo   ",
                "\\#file",
                "bar\\ ",
            ).joinToString("\n"),
        )

        assertEquals(3, rules.size)
        assertEquals("foo", rules[0].pattern)
        assertEquals("\\#file", rules[1].pattern)
        assertEquals("bar\\ ", rules[2].pattern)

        assertTrue(ignored(rules, "$root/foo"))
        assertTrue(ignored(rules, "$root/a/foo"))
        // \# 转义后是普通文件名
        assertTrue(ignored(rules, "$root/#file"))
        assertFalse(ignored(rules, "$root/file"))
        // 被转义的行尾空格保留
        assertTrue(ignored(rules, "$root/bar "))
        assertFalse(ignored(rules, "$root/bar"))
    }

    @Test
    fun `question mark and character classes`() {
        val rules = rootRules("a?c.txt", "[abc]x.txt", "[!abc]y.txt")

        assertTrue(ignored(rules, "$root/abc.txt"))
        assertTrue(ignored(rules, "$root/axc.txt"))
        // ? 不跨 '/'
        assertFalse(ignored(rules, "$root/a/c.txt"))

        assertTrue(ignored(rules, "$root/ax.txt"))
        assertTrue(ignored(rules, "$root/cx.txt"))
        assertFalse(ignored(rules, "$root/dx.txt"))

        assertTrue(ignored(rules, "$root/dy.txt"))
        assertFalse(ignored(rules, "$root/ay.txt"))
    }

    @Test
    fun `ignored parent ignores children`() {
        val rules = rootRules("node_modules/")
        assertTrue(ignored(rules, "$root/node_modules", isDirectory = true))
        assertTrue(ignored(rules, "$root/node_modules/pkg/index.js", isDirectory = false))
        assertTrue(ignored(rules, "$root/a/node_modules/b/c/d.ts"))
        assertFalse(ignored(rules, "$root/a/node_modules.txt"))
    }

    @Test
    fun `negation cannot rescue file inside ignored directory`() {
        // 目录整体被忽略，取反捞不回里面的文件（git 的限制，实现里靠祖先逐级评估保证）
        val strict = rootRules("build/", "!build/keep.txt")
        assertTrue(ignored(strict, "$root/build/keep.txt"))
        assertTrue(ignored(strict, "$root/build", isDirectory = true))

        // git 里推荐的等价写法：只忽略目录内容，才能取反
        val contentOnly = rootRules("build/*", "!build/keep.txt")
        assertFalse(ignored(contentOnly, "$root/build/keep.txt"))
        assertTrue(ignored(contentOnly, "$root/build/other.txt"))
        assertFalse(ignored(contentOnly, "$root/build", isDirectory = true))
    }

    @Test
    fun `rules relative to a non root base dir`() {
        val rules = rulesOf(
            "$root/app/",
            "build/",
            "/src/*.kt",
        )
        assertEquals("$root/app", rules[0].baseDir)
        assertTrue(rules[1].anchored)

        assertTrue(ignored(rules, "$root/app/build", isDirectory = true))
        // baseDir 之外不受影响
        assertFalse(ignored(rules, "$root/build", isDirectory = true))
        // 非锚定的 build/ 在 baseDir 之下任意层级生效
        assertTrue(ignored(rules, "$root/app/lib/build", isDirectory = true))

        assertTrue(ignored(rules, "$root/app/src/Main.kt"))
        assertFalse(ignored(rules, "$root/app/lib/src/Main.kt"))
    }

    @Test
    fun `nested gitignore rules override root rules`() {
        val rules = rootRules("*.tmp") + rulesOf("$root/app", "!keep.tmp", "build/")

        assertEquals(3, rules.size)
        assertEquals(root, rules[0].baseDir)
        assertEquals("$root/app", rules[1].baseDir)

        // 子目录规则在后面，覆盖根规则
        assertFalse(ignored(rules, "$root/app/keep.tmp"))
        assertTrue(ignored(rules, "$root/other/keep.tmp"))

        assertTrue(ignored(rules, "$root/app/build", isDirectory = true))
        assertFalse(ignored(rules, "$root/build", isDirectory = true))
    }
}
