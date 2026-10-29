package me.rerere.rikkahub.ui.pages.chat.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCommandsTest {
    @Test
    fun `parses command name and arguments`() {
        assertEquals(TerminalCommand("model", "gpt 5"), parseTerminalCommand("  /MODEL gpt 5 "))
        assertEquals(TerminalCommand("help", ""), parseTerminalCommand("/help"))
    }

    @Test
    fun `ignores plain text and lone slash`() {
        assertNull(parseTerminalCommand("hello"))
        assertNull(parseTerminalCommand("/"))
        assertNull(parseTerminalCommand("   "))
    }

    @Test
    fun `suggests matching commands without arguments`() {
        val suggestions = terminalCommandSuggestions("/co")
        assertEquals(listOf("copy", "compress"), suggestions.map { it.name })
        assertTrue(terminalCommandSuggestions("/copy 3").isEmpty())
    }

    @Test
    fun `help lists every command`() {
        val help = terminalHelpText()
        TERMINAL_COMMANDS.forEach { command ->
            assertTrue(command.usage, help.contains(command.usage))
        }
    }
}
