package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool

const val ASK_USER_TOOL_NAME = "ask_user"

internal fun buildAskUserTool(): Tool = Tool(
    name = ASK_USER_TOOL_NAME,
    description = """
        Ask the user one or more questions when you need clarification, additional information, or confirmation.
        Each question may carry a short header, a list of suggested options (each with a label and optional description) and a multi_select flag.
        If you recommend one option, put it first and append "(Recommended)" to its label.
        The user may also type a free-text answer (custom) for every question, including single and multi selection questions.
        Answers are returned as a JSON object: {"answers":[{"id":"<question id>","selected":["<option label>"],"custom":"<free text>"}]}.
        An answer item with empty selected and no custom means the user skipped that question: continue without it, do not ask it again, and do not treat the skip as permission.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("questions", buildJsonObject {
                    put("type", "array")
                    put("description", "List of questions to ask the user")
                    put("items", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("id", buildJsonObject {
                                put("type", "string")
                                put("description", "Stable id for this question; echoed in the answer")
                            })
                            put("question", buildJsonObject {
                                put("type", "string")
                                put("description", "The question text to display to the user")
                            })
                            put("header", buildJsonObject {
                                put("type", "string")
                                put("description", "Optional short heading for the question, such as \"Confirm\" or \"Choose Mode\"")
                            })
                            put("options", buildJsonObject {
                                put("type", "array")
                                put(
                                    "description",
                                    "Optional choices to show the user. If you recommend one, put it first and append \"(Recommended)\" to that label"
                                )
                                put("items", buildJsonObject {
                                    put("type", "object")
                                    put("properties", buildJsonObject {
                                        put("label", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Short user-facing option label")
                                        })
                                        put("description", buildJsonObject {
                                            put("type", "string")
                                            put("description", "One sentence explaining the tradeoff or impact")
                                        })
                                    })
                                    put("required", buildJsonArray {
                                        add("label")
                                    })
                                })
                            })
                            put("multi_select", buildJsonObject {
                                put("type", "boolean")
                                put("description", "Whether the user may select more than one option. Defaults to false")
                            })
                        })
                        put("required", buildJsonArray {
                            add("id")
                            add("question")
                        })
                    })
                })
            },
            required = listOf("questions")
        )
    },
    needsApproval = { true },
    execute = {
        error("ask_user tool should be handled by HITL flow")
    }
)
