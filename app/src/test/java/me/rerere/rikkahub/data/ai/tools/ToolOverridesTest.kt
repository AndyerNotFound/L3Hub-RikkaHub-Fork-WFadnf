package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.model.ToolOverride
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolOverridesTest {
    private val defaultSchema = InputSchema.Obj(
        properties = buildJsonObject { put("path", buildJsonObject { put("type", "string") }) },
        required = listOf("path"),
    )

    private fun tool() = Tool(
        name = "workspace_read_file",
        description = "default description",
        parameters = { defaultSchema },
        systemPrompt = { _, _ -> "default instructions" },
        execute = { emptyList() },
    )

    @Test
    fun `override replaces description instructions and parameters`() {
        val schema = """{"type":"object","properties":{"text":{"type":"string"}}}"""
        val override = ToolOverride(
            name = "workspace_read_file",
            description = "custom description",
            instructions = "custom instructions",
            parameters = schema,
        )

        val result = applyToolOverrides(listOf(tool()), listOf(override)).single()

        assertEquals("custom description", result.description)
        assertEquals("custom instructions", result.systemPrompt(Model(), emptyList()))
        val expected = decodeToolOverrideParameters(schema)
        assertEquals(expected, result.parameters())
    }

    @Test
    fun `missing parts keep the default values`() {
        val result = applyToolOverrides(
            tools = listOf(tool()),
            overrides = listOf(ToolOverride(name = "workspace_read_file", description = "custom")),
        ).single()

        assertEquals("custom", result.description)
        assertEquals("default instructions", result.systemPrompt(Model(), emptyList()))
        assertEquals(defaultSchema, result.parameters())
    }

    @Test
    fun `invalid schema falls back to the default`() {
        val result = applyToolOverrides(
            tools = listOf(tool()),
            overrides = listOf(
                ToolOverride(name = "workspace_read_file", parameters = """{"properties":{}}""")
            ),
        ).single()

        assertEquals(defaultSchema, result.parameters())
    }

    @Test
    fun `overrides for other tools are ignored`() {
        val result = applyToolOverrides(
            tools = listOf(tool()),
            overrides = listOf(ToolOverride(name = "other_tool", description = "custom")),
        ).single()

        assertEquals("default description", result.description)
    }

    @Test
    fun `schema text round trips through the editor format`() {
        val encoded = encodeToolOverrideParameters(defaultSchema)
        val decoded = decodeToolOverrideParameters(encoded)

        assertEquals(defaultSchema, decoded)
    }

    @Test
    fun `blank optional fields remain overridden`() {
        val result = applyToolOverrides(
            tools = listOf(tool()),
            overrides = listOf(
                ToolOverride(
                    name = "workspace_read_file",
                    description = "",
                    instructions = "",
                )
            ),
        ).single()

        assertEquals("", result.description)
        assertEquals("", result.systemPrompt(Model(), emptyList()))
        assertEquals(defaultSchema, result.parameters())
    }
}
