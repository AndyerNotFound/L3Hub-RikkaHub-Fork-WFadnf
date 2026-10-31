package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.TodoStatus
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoToolsTest {

    @Test
    fun `prompt lists every item with a status mark`() {
        val todos = listOf(
            TodoItem("读代码", TodoStatus.COMPLETED),
            TodoItem("改实现", TodoStatus.IN_PROGRESS),
            TodoItem("跑测试", TodoStatus.PENDING),
        )

        val prompt = buildTodoPrompt(todos)

        assertTrue(prompt.contains("- [x] 读代码"))
        assertTrue(prompt.contains("- [>] 改实现"))
        assertTrue(prompt.contains("- [ ] 跑测试"))
        assertTrue(prompt.contains("todo_write"))
    }

    @Test
    fun `todo item round trips through json with snake case status`() {
        val todos = listOf(
            TodoItem("a", TodoStatus.IN_PROGRESS),
            TodoItem("b", TodoStatus.COMPLETED),
        )

        val json = JsonInstant.encodeToString(todos)
        val decoded = JsonInstant.decodeFromString<List<TodoItem>>(json)

        assertEquals(todos, decoded)
        assertTrue(json.contains("in_progress"))
        assertTrue(json.contains("completed"))
    }
}
