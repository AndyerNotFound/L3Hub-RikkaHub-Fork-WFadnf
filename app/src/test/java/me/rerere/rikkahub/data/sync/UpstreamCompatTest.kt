package me.rerere.rikkahub.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpstreamCompatTest {
    private fun downgrade(json: String) =
        Json.parseToJsonElement(UpstreamCompat.downgradeSettings(json)).jsonObject

    private val settings = """
        {
          "themeId": "minimal",
          "backgroundEffectType": "off",
          "providers": [
            {
              "type": "openai",
              "models": [
                {"type": "CHAT", "id": "chat"},
                {"type": "OCR", "id": "ocr"}
              ]
            },
            {"type": "gguf", "models": [{"type": "CHAT", "id": "local"}]}
          ],
          "assistants": [
            {
              "reasoningLevel": "ultra",
              "name": "a",
              "localTools": [{"type": "time_info"}, {"type": "sub_agent"}],
              "avatar": {"type": "me.rerere.rikkahub.data.model.Avatar.Video", "url": "/a.mp4"}
            },
            {
              "reasoningLevel": "auto",
              "name": "b",
              "localTools": [{"type": "sub_agent"}],
              "avatar": {"type": "me.rerere.rikkahub.data.model.Avatar.Image", "url": "/a.png"}
            }
          ],
          "someFutureForkKey": "kept-as-is"
        }
    """.trimIndent()

    @Test
    fun `drops providers the original build cannot deserialize`() {
        val providers = downgrade(settings)["providers"]!!.jsonArray
        assertEquals(1, providers.size)
        assertEquals("openai", providers[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `drops ocr models and keeps the rest`() {
        val models = downgrade(settings)["providers"]!!.jsonArray[0].jsonObject["models"]!!.jsonArray
        assertEquals(1, models.size)
        assertEquals("chat", models[0].jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `maps enum values the original build does not have`() {
        val json = downgrade(
            settings.replace("\"ultra\"", "\"minimal\"")
        )
        assertEquals("low", json["assistants"]!!.jsonArray[0].jsonObject["reasoningLevel"]!!.jsonPrimitive.content)
        assertEquals("blur", json["backgroundEffectType"]!!.jsonPrimitive.content)
    }

    @Test
    fun `drops local tools the original does not know`() {
        val assistants = downgrade(settings)["assistants"]!!.jsonArray
        val first = assistants[0].jsonObject["localTools"]!!.jsonArray
        assertEquals(1, first.size)
        assertEquals("time_info", first[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(0, assistants[1].jsonObject["localTools"]!!.jsonArray.size)
    }

    @Test
    fun `replaces video avatars with the empty one`() {
        val assistants = downgrade(settings)["assistants"]!!.jsonArray
        assertEquals(
            "me.rerere.rikkahub.data.model.Avatar.Dummy",
            assistants[0].jsonObject["avatar"]!!.jsonObject["type"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "me.rerere.rikkahub.data.model.Avatar.Image",
            assistants[1].jsonObject["avatar"]!!.jsonObject["type"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `keeps unknown fork keys for the original to ignore`() {
        assertEquals("kept-as-is", downgrade(settings)["someFutureForkKey"]!!.jsonPrimitive.content)
        assertEquals("minimal", downgrade(settings)["themeId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `rewrites attachment paths to the original files dir`() {
        val rewritten = UpstreamCompat.downgradeSettings(
            """{"background":"/data/user/0/com.rikkahub.l3hub.wfadnf/files/upload/a.png"}"""
        )
        assertTrue(rewritten, rewritten.contains("/data/user/0/me.rerere.rikkahub/files/upload/a.png"))
        assertFalse(rewritten, rewritten.contains("com.rikkahub.l3hub.wfadnf"))
    }

    @Test
    fun `leaves other apps paths alone`() {
        val text = """{"tool":"/data/data/com.termux/files/usr/bin/bash"}"""
        assertEquals(text, UpstreamCompat.downgradeSettings(text))
    }
}
