package me.rerere.rikkahub.ui.components.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户消息源码保护：目标是“粘贴的源码不要被 Markdown 吃掉”，
 * 而不是把原文改写成别的东西。这里只验证保护行为本身。
 */
class UserMessageSourcePreserverTest {
    @Test
    fun `empty input is untouched`() {
        assertEquals("", preprocessMarkdownForDisplay("", MarkdownRenderMode.USER_MESSAGE))
    }

    @Test
    fun `c plus plus paste is fenced without escaping its include`() {
        val source = """
            #include <iostream>
            int main() { return 0; }
        """.trimIndent()
        val output = preprocessMarkdownForDisplay(source, MarkdownRenderMode.USER_MESSAGE)

        assertEquals("``` plaintext\n$source\n```", output)
    }

    @Test
    fun `java assignment is fenced without escaping its generic type`() {
        val source = "List<String> names = new ArrayList<>();"
        val output = preprocessMarkdownForDisplay(source, MarkdownRenderMode.USER_MESSAGE)

        assertEquals("``` plaintext\n$source\n```", output)
    }

    @Test
    fun `generic type in prose is escaped instead of fenced`() {
        val source = "使用 List<String> 保存名字。"
        val output = preprocessMarkdownForDisplay(source, MarkdownRenderMode.USER_MESSAGE)

        assertEquals("使用 List&lt;String&gt; 保存名字。", output)
    }

    @Test
    fun `supported markdown html is left alone`() {
        val source = "普通 <strong>粗体</strong> 和 <br/> 换行"
        val output = preprocessMarkdownForDisplay(source, MarkdownRenderMode.USER_MESSAGE)

        assertTrue(output.contains("<strong>"))
        assertTrue(output.contains("<br/>"))
    }

    @Test
    fun `fenced code keeps its content byte for byte`() {
        val source = """
            下面是一段 C++：

            ```cpp
            #include <vector>

            > \(literal\)
            std::vector<int> v;
            ```
        """.trimIndent()
        val output = preprocessMarkdownForDisplay(source, MarkdownRenderMode.USER_MESSAGE)

        assertEquals(source, output)
    }

    @Test
    fun `standard mode does not escape source`() {
        val source = "List<String> names"
        assertEquals(source, preprocessMarkdownForDisplay(source, MarkdownRenderMode.STANDARD))
    }

    @Test
    fun `latex delimiters are normalized after protection`() {
        val source = "行内 \\(a + b\\) 公式"
        val output = preprocessMarkdownForDisplay(source, MarkdownRenderMode.USER_MESSAGE)

        assertTrue(output.contains("\$a + b\$"))
    }

    @Test
    fun `plain prose is not modified`() {
        val source = "这是一段普通的中文说明，没有代码也没有公式。"
        assertEquals(source, preprocessMarkdownForDisplay(source, MarkdownRenderMode.USER_MESSAGE))
    }
}
