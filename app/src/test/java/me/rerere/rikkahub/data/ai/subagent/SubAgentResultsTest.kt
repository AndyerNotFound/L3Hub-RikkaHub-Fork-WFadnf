package me.rerere.rikkahub.data.ai.subagent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubAgentResultsTest {
    @Test
    fun `single result keeps the header and the answer`() {
        val text = formatSubAgentResults(listOf("子代理「X」已完成\n\n答案"))
        assertTrue(text.startsWith("以下 1 个后台子代理任务已完成"))
        assertTrue(text.endsWith("## 1\n子代理「X」已完成\n\n答案"))
    }

    @Test
    fun `several results merge into one message in completion order`() {
        val text = formatSubAgentResults(listOf("第一份", "第二份"))
        assertTrue(text.contains("以下 2 个后台子代理任务已完成"))
        assertTrue(text.indexOf("## 1") < text.indexOf("第一份"))
        assertTrue(text.indexOf("第一份") < text.indexOf("## 2"))
        assertTrue(text.indexOf("## 2") < text.indexOf("第二份"))
    }

    @Test
    fun `notice body never claims to be user input`() {
        val text = formatSubAgentResults(listOf("结果"))
        assertFalse(text.contains("用户输入"))
    }

    @Test
    fun `each result is trimmed but inner blank lines survive`() {
        val text = formatSubAgentResults(listOf("  第一段\n\n第二段  "))
        assertTrue(text.endsWith("## 1\n第一段\n\n第二段"))
    }
}
