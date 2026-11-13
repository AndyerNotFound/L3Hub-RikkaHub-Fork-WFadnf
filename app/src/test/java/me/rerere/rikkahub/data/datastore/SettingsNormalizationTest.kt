package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsNormalizationTest {
    @Test
    fun `allowed sub-agents drop missing, unmarked and self ids`() {
        val dispatcherId = Uuid.random()
        val target = Assistant(id = Uuid.random(), name = "target", isSubAgent = true)
        val unmarked = Assistant(id = Uuid.random(), name = "unmarked")
        val dispatcher = Assistant(
            id = dispatcherId,
            name = "dispatcher",
            isSubAgent = true,
            allowedSubAgentIds = setOf(target.id, unmarked.id, Uuid.random(), dispatcherId),
        )

        val normalized = Settings(assistants = listOf(dispatcher, target, unmarked))
            .withoutInvalidReferences()
            .assistants
            .first { it.id == dispatcherId }

        assertEquals(setOf(target.id), normalized.allowedSubAgentIds)
    }

    @Test
    fun `deleted built-in providers stay deleted after normalization`() {
        val target = DEFAULT_PROVIDERS.first()
        val settings = Settings(providers = emptyList())
            .removeProvider(target.id)
            .normalized()

        assertTrue(settings.providers.none { it.id == target.id })
        assertEquals(setOf(target.id.toString()), settings.deletedBuiltInProviderIds)
    }

    @Test
    fun `restoring a built-in provider clears its deletion record`() {
        val target = DEFAULT_PROVIDERS.first()
        val restored = Settings(providers = emptyList())
            .removeProvider(target.id)
            .restoreProvider(target)
            .normalized()

        assertTrue(restored.providers.any { it.id == target.id })
        assertEquals(emptySet<String>(), restored.deletedBuiltInProviderIds)
    }

    @Test
    fun `a valid allowlist is left untouched`() {
        val first = Assistant(id = Uuid.random(), name = "first", isSubAgent = true)
        val second = Assistant(id = Uuid.random(), name = "second", isSubAgent = true)
        val dispatcher = Assistant(
            id = Uuid.random(),
            name = "dispatcher",
            allowedSubAgentIds = setOf(first.id, second.id),
        )

        val normalized = Settings(assistants = listOf(dispatcher, first, second))
            .withoutInvalidReferences()
            .assistants
            .first { it.id == dispatcher.id }

        assertEquals(setOf(first.id, second.id), normalized.allowedSubAgentIds)
    }
}
