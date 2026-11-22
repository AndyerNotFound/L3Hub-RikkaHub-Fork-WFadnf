package me.rerere.rikkahub.nav

import me.rerere.rikkahub.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NavStackTest {

    private val chat = Screen.Chat(id = "chat-1")
    private val setting = Screen.Setting
    private val providers = Screen.SettingProvider
    private val models = Screen.SettingModels

    @Test
    fun `push appends a new page`() {
        assertEquals(listOf(chat, setting), NavStack.push(listOf(chat), setting))
    }

    @Test
    fun `single top ignores a repeated press`() {
        val stack = listOf(chat, setting)
        assertEquals(stack, NavStack.push(stack, setting, singleTop = true))
    }

    @Test
    fun `single top still pushes a different page`() {
        val stack = listOf(chat, setting)
        assertEquals(listOf(chat, setting, models), NavStack.push(stack, models, singleTop = true))
    }

    @Test
    fun `pop up to keeps the target and appends`() {
        val stack = listOf(chat, setting, providers)
        assertEquals(
            listOf(chat, setting, models),
            NavStack.popUpTo(stack, target = setting, inclusive = false, screen = models, singleTop = true),
        )
    }

    @Test
    fun `inclusive pop up to drops the target as well`() {
        val stack = listOf(chat, setting, providers)
        assertEquals(
            listOf(chat, setting),
            NavStack.popUpTo(stack, target = setting, inclusive = true, screen = setting, singleTop = true),
        )
    }

    // 目标不在栈里时按"只入栈"处理，不悄悄退化成裁剪到某个近似位置
    @Test
    fun `missing pop up target only pushes`() {
        val stack = listOf(chat)
        assertEquals(
            listOf(chat, models),
            NavStack.popUpTo(stack, target = setting, inclusive = false, screen = models, singleTop = false),
        )
    }

    @Test
    fun `pop up target found more than once uses the topmost one`() {
        val stack = listOf(chat, setting, providers, setting, models)
        assertEquals(
            listOf(chat, setting, providers, setting, providers),
            NavStack.popUpTo(stack, target = setting, inclusive = false, screen = providers, singleTop = false),
        )
    }

    @Test
    fun `replace all leaves a single page`() {
        assertEquals(listOf(chat), NavStack.replaceAll(chat))
    }

    @Test
    fun `pop never empties the stack`() {
        val single = listOf(chat)
        assertSame(single, NavStack.pop(single))
        assertEquals(listOf(chat), NavStack.pop(listOf(chat, setting)))
    }
}
