package me.rerere.rikkahub.data.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedDiffTest {

    private fun success(outcome: ApplyOutcome): ApplyOutcome.Success {
        assertTrue("期望 Success，实际 $outcome", outcome is ApplyOutcome.Success)
        return outcome as ApplyOutcome.Success
    }

    private fun conflict(outcome: ApplyOutcome): ApplyOutcome.Conflict {
        assertTrue("期望 Conflict，实际 $outcome", outcome is ApplyOutcome.Conflict)
        return outcome as ApplyOutcome.Conflict
    }

    @Test
    fun `single file single hunk`() {
        val patch = """
            --- a/src/Main.kt
            +++ b/src/Main.kt
            @@ -1,3 +1,3 @@ fun main
             fun main() {
            -    println("hi")
            +    println("hello")
             }
        """.trimIndent()

        val files = UnifiedDiff.parse(patch)
        assertEquals(1, files.size)
        val file = files.single()
        assertEquals("src/Main.kt", file.oldPath)
        assertEquals("src/Main.kt", file.newPath)
        assertFalse(file.isNewFile)
        assertFalse(file.isDeletedFile)
        assertFalse(file.isBinary)
        val hunk = file.hunks.single()
        assertEquals(1, hunk.oldStart)
        assertEquals(3, hunk.oldCount)
        assertEquals(1, hunk.newStart)
        assertEquals(3, hunk.newCount)
        assertEquals("fun main", hunk.section)
        assertEquals(4, hunk.lines.size)

        val result = success(UnifiedDiff.apply("fun main() {\n    println(\"hi\")\n}\n", file))
        assertEquals("fun main() {\n    println(\"hello\")\n}\n", result.content)
        assertEquals(1, result.added)
        assertEquals(1, result.removed)
    }

    @Test
    fun `multiple hunks in one file`() {
        val original = "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\n"
        val patch = """
            --- a/f.txt
            +++ b/f.txt
            @@ -1,3 +1,3 @@
             one
             two
            -three
            +THREE
            @@ -6,3 +6,3 @@
             six
            -seven
            +SEVEN
             eight
        """.trimIndent()

        val file = UnifiedDiff.parse(patch).single()
        assertEquals(2, file.hunks.size)

        val result = success(UnifiedDiff.apply(original, file))
        assertEquals("one\ntwo\nTHREE\nfour\nfive\nsix\nSEVEN\neight\n", result.content)
        assertEquals(2, result.added)
        assertEquals(2, result.removed)
    }

    @Test
    fun `multiple files in one patch`() {
        val patch = """
            diff --git a/a.txt b/a.txt
            index 1111111..2222222 100644
            --- a/a.txt
            +++ b/a.txt
            @@ -1 +1 @@
            -one
            +ONE
            diff --git a/pkg/B.kt b/pkg/B.kt
            --- a/pkg/B.kt
            +++ b/pkg/B.kt
            @@ -2,2 +2,2 @@
             keep
            -old
            +new
        """.trimIndent()

        val files = UnifiedDiff.parse(patch)
        assertEquals(2, files.size)
        assertEquals("a.txt", files[0].oldPath)
        assertEquals("a.txt", files[0].newPath)
        assertEquals(1, files[0].hunks.size)
        assertEquals("pkg/B.kt", files[1].oldPath)
        assertEquals("pkg/B.kt", files[1].newPath)
        val second = files[1].hunks.single()
        assertEquals(2, second.oldStart)
        assertEquals(2, second.oldCount)

        val result = success(UnifiedDiff.apply("keep\nold\n", files[1]))
        assertEquals("keep\nnew\n", result.content)
    }

    @Test
    fun `new file from dev null`() {
        val patch = """
            diff --git a/new.txt b/new.txt
            new file mode 100644
            index 0000000..1111111
            --- /dev/null
            +++ b/new.txt
            @@ -0,0 +1,2 @@
            +hello
            +world
        """.trimIndent()

        val file = UnifiedDiff.parse(patch).single()
        assertNull(file.oldPath)
        assertEquals("new.txt", file.newPath)
        assertTrue(file.isNewFile)
        assertEquals(0, file.hunks.single().oldStart)

        val result = success(UnifiedDiff.apply("", file))
        assertEquals("hello\nworld\n", result.content)
        assertEquals(2, result.added)
        assertEquals(0, result.removed)
    }

    @Test
    fun `deleted file`() {
        val patch = """
            diff --git a/old.txt b/old.txt
            deleted file mode 100644
            index 1111111..0000000
            --- a/old.txt
            +++ /dev/null
            @@ -1,2 +0,0 @@
            -hello
            -world
        """.trimIndent()

        val file = UnifiedDiff.parse(patch).single()
        assertEquals("old.txt", file.oldPath)
        assertNull(file.newPath)
        assertTrue(file.isDeletedFile)

        val result = success(UnifiedDiff.apply("hello\nworld\n", file))
        assertEquals("", result.content)
        assertEquals(2, result.removed)
    }

    @Test
    fun `wrong line numbers are fixed by searching`() {
        val original = "a\nb\nc\nd\ne\nf\nalpha\nbeta\ngamma\nomega\n"
        val laterPatch = """
            --- a/f.txt
            +++ b/f.txt
            @@ -5,3 +5,3 @@
             alpha
            -beta
            +BETA
             gamma
        """.trimIndent()

        val later = success(UnifiedDiff.apply(original, UnifiedDiff.parse(laterPatch).single()))
        assertEquals("a\nb\nc\nd\ne\nf\nalpha\nBETA\ngamma\nomega\n", later.content)

        val shiftedPatch = """
            --- a/f.txt
            +++ b/f.txt
            @@ -4,3 +4,3 @@
             alpha
            -beta
            +BETA
             gamma
        """.trimIndent()

        val shifted = success(UnifiedDiff.apply("alpha\nbeta\ngamma\nx\ny\nz\n", UnifiedDiff.parse(shiftedPatch).single()))
        assertEquals("alpha\nBETA\ngamma\nx\ny\nz\n", shifted.content)
    }

    @Test
    fun `missing context reports conflict`() {
        val original = "line1\nline2\nline3\nline4\nline5\nline6\n"
        val patch = """
            --- a/f.txt
            +++ b/f.txt
            @@ -1,2 +1,2 @@
             line1
            -line2
            +LINE2
            @@ -5,2 +5,2 @@
             nope
            -line6
            +LINE6
        """.trimIndent()

        val file = UnifiedDiff.parse(patch).single()
        val failure = conflict(UnifiedDiff.apply(original, file))
        assertEquals("f.txt", failure.path)
        assertEquals(1, failure.hunkIndex)
        assertEquals(5, failure.line)
        assertEquals("context not found", failure.reason)
    }

    @Test
    fun `duplicated context is ambiguous`() {
        val original = "same\ntarget\ntail\ngap\nsame\ntarget\ntail\n"
        val patch = """
            --- a/f.txt
            +++ b/f.txt
            @@ -4,2 +4,2 @@
             same
            -target
            +TARGET
        """.trimIndent()

        val failure = conflict(UnifiedDiff.apply(original, UnifiedDiff.parse(patch).single()))
        assertEquals(0, failure.hunkIndex)
        assertEquals(4, failure.line)
        assertEquals("ambiguous context", failure.reason)
    }

    @Test
    fun `crlf is preserved`() {
        val original = "a\r\nb\r\nc\r\n"
        val patch = listOf(
            "--- a/f.txt",
            "+++ b/f.txt",
            "@@ -1,3 +1,3 @@",
            " a",
            "-b",
            "+B",
            " c",
        ).joinToString("\n")

        val file = UnifiedDiff.parse(patch).single()
        val result = success(UnifiedDiff.apply(original, file))
        assertEquals("a\r\nB\r\nc\r\n", result.content)
        assertFalse(result.content.replace("\r\n", "").contains("\n"))

        // CRLF 的补丁也能解析
        val crlfFile = UnifiedDiff.parse(patch.replace("\n", "\r\n")).single()
        assertEquals(4, crlfFile.hunks.single().lines.size)
        assertEquals("a\r\nB\r\nc\r\n", success(UnifiedDiff.apply(original, crlfFile)).content)
    }

    @Test
    fun `missing trailing newline is preserved when the last line is untouched`() {
        val original = "x\ny\nz"
        val patch = """
            --- a/f.txt
            +++ b/f.txt
            @@ -1,2 +1,2 @@
            -x
            +X
             y
        """.trimIndent()

        val result = success(UnifiedDiff.apply(original, UnifiedDiff.parse(patch).single()))
        assertEquals("X\ny\nz", result.content)
    }

    @Test
    fun `no newline marker is parsed`() {
        val patch = """
            --- a/f.txt
            +++ b/f.txt
            @@ -1 +1 @@
            -a
            \ No newline at end of file
            +b
            \ No newline at end of file
        """.trimIndent()

        val file = UnifiedDiff.parse(patch).single()
        val hunk = file.hunks.single()
        assertEquals(1, hunk.oldCount)
        assertEquals(1, hunk.newCount)
        assertEquals(1, hunk.oldStart)
        assertEquals(1, hunk.newStart)
        assertEquals(4, hunk.lines.size)
        assertEquals("\\ No newline at end of file", hunk.lines[1])

        val result = success(UnifiedDiff.apply("a", file))
        assertEquals("b", result.content)
    }

    @Test
    fun `code fence and prose around the patch are ignored`() {
        val patch = listOf(
            "Sure, here is the patch:",
            "",
            "```diff",
            "--- a/a.txt",
            "+++ b/a.txt",
            "@@ -1 +1 @@",
            "-old",
            "+new",
            "```",
            "",
            "Applied as requested.",
        ).joinToString("\n")

        val files = UnifiedDiff.parse(patch)
        assertEquals(1, files.size)
        assertEquals(1, files.single().hunks.size)
        assertEquals("new\n", success(UnifiedDiff.apply("old\n", files.single())).content)
    }

    @Test
    fun `wrong hunk counts are tolerated`() {
        val patch = """
            --- a/f.txt
            +++ b/f.txt
            @@ -1,5 +1,5 @@
             alpha
            -beta
            +BETA
        """.trimIndent()

        val file = UnifiedDiff.parse(patch).single()
        val hunk = file.hunks.single()
        assertEquals(5, hunk.oldCount)
        assertEquals(3, hunk.lines.size)
        assertEquals("alpha\nBETA\n", success(UnifiedDiff.apply("alpha\nbeta\n", file)).content)
    }

    @Test
    fun `overlapping hunks are rejected`() {
        val original = "a\nb\nc\nd\ne\nf\n"
        val patch = """
            --- a/f.txt
            +++ b/f.txt
            @@ -1,5 +1,5 @@
             a
             b
             c
             d
            -e
            +E
            @@ -3,1 +3,1 @@
            -c
            +C
        """.trimIndent()

        val failure = conflict(UnifiedDiff.apply(original, UnifiedDiff.parse(patch).single()))
        assertEquals(1, failure.hunkIndex)
        assertEquals(3, failure.line)
        assertEquals("hunk overlaps previous hunk", failure.reason)
    }

    @Test
    fun `binary diff is flagged and skipped`() {
        val gitBinary = """
            diff --git a/logo.png b/logo.png
            index 1111111..2222222 100644
            GIT binary patch
            literal 8
            LcmZQzU|?Vs00RI3

        """.trimIndent()

        val files = UnifiedDiff.parse(gitBinary)
        assertEquals(1, files.size)
        assertTrue(files.single().isBinary)
        assertTrue(files.single().hunks.isEmpty())
        assertEquals("logo.png", files.single().newPath)

        val legacy = UnifiedDiff.parse("Binary files a/logo.png and b/logo.png differ")
        assertTrue(legacy.single().isBinary)
        assertTrue(UnifiedDiff.parse("just some text").isEmpty())
    }

    @Test
    fun `empty hunk list is a no-op`() {
        val file = FileDiff(
            oldPath = "f.txt",
            newPath = "f.txt",
            hunks = emptyList(),
            isNewFile = false,
            isDeletedFile = false,
            isBinary = false,
        )

        val result = success(UnifiedDiff.apply("unchanged\n", file))
        assertEquals("unchanged\n", result.content)
        assertEquals(0, result.added)
        assertEquals(0, result.removed)
    }
}
