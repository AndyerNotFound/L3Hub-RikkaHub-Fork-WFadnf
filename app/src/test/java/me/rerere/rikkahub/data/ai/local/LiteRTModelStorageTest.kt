package me.rerere.rikkahub.data.ai.local

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import me.rerere.rikkahub.data.ai.local.LiteRTModelStorage.ImportResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRTModelStorageTest {

    private val tempDirs = mutableListOf<File>()

    private fun newStorage(): LiteRTModelStorage {
        val dir = Files.createTempDirectory("litert-models").toFile()
        tempDirs += dir
        return LiteRTModelStorage(dir)
    }

    @After
    fun tearDown() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun bytes(seed: Int, size: Int = 8192) = ByteArray(size) { index ->
        ((index * 31 + seed) % 251).toByte()
    }

    private fun importModel(
        storage: LiteRTModelStorage,
        name: String?,
        content: ByteArray,
    ): ImportResult = storage.importModel(
        source = ByteArrayInputStream(content),
        displayName = name,
        contentSize = content.size.toLong(),
    )

    @Test
    fun `import stores the model and lists it`() {
        val storage = newStorage()
        val content = bytes(seed = 1)

        val result = importModel(storage, "qwen3-0.6b.litertlm", content)

        assertTrue(result is ImportResult.Imported)
        val model = (result as ImportResult.Imported).model
        val listed = storage.list().single()
        assertEquals(model.path, listed.path)
        assertEquals("qwen3-0.6b", listed.displayName)
        assertEquals(content.size.toLong(), listed.sizeBytes)
        assertTrue(File(model.path).isFile)
    }

    @Test
    fun `importing the same model again reuses the file instead of copying it`() {
        val storage = newStorage()
        val content = bytes(seed = 2)

        val first = importModel(storage, "qwen3-0.6b.litertlm", content) as ImportResult.Imported
        val second = importModel(storage, "qwen3-0.6b.litertlm", content)

        assertTrue(second is ImportResult.Duplicate)
        assertEquals(first.model.path, (second as ImportResult.Duplicate).model.path)
        assertEquals(1, storage.list().size)
        assertEquals(content.size.toLong(), storage.totalSizeBytes())
    }

    @Test
    fun `identical content under another name is deduplicated too`() {
        val storage = newStorage()
        val content = bytes(seed = 3)

        val first = importModel(storage, "model.litertlm", content) as ImportResult.Imported
        val second = importModel(storage, "model-copy.litertlm", content)

        assertTrue(second is ImportResult.Duplicate)
        assertEquals(first.model.path, (second as ImportResult.Duplicate).model.path)
        assertEquals(1, storage.list().size)
    }

    @Test
    fun `different content with the same name keeps both files`() {
        val storage = newStorage()
        val first = bytes(seed = 4)
        val second = first.copyOf().also { it[0] = (it[0] + 1).toByte() }

        importModel(storage, "qwen.litertlm", first)
        val result = importModel(storage, "qwen.litertlm", second)

        assertTrue(result is ImportResult.Imported)
        val names = storage.list().map { it.file.name }.sorted()
        assertEquals(listOf("qwen (2).litertlm", "qwen.litertlm"), names)
        assertEquals((first.size + second.size).toLong(), storage.totalSizeBytes())
    }

    @Test
    fun `delete removes the file from the library`() {
        val storage = newStorage()
        val imported = importModel(storage, "model.litertlm", bytes(seed = 5)) as ImportResult.Imported

        assertTrue(storage.delete(storage.list().single()))

        assertTrue(storage.list().isEmpty())
        assertEquals(0L, storage.totalSizeBytes())
        assertTrue(!File(imported.model.path).exists())
    }

    @Test
    fun `temporary import files are not listed as models`() {
        val dir = Files.createTempDirectory("litert-models").toFile()
        tempDirs += dir
        File(dir, ".importing-1.tmp").writeBytes(ByteArray(16))

        val storage = LiteRTModelStorage(dir)

        assertTrue(storage.list().isEmpty())
        assertEquals(0L, storage.totalSizeBytes())
    }

    @Test
    fun `empty files are rejected and leave no residue`() {
        val storage = newStorage()

        assertThrows(IllegalArgumentException::class.java) {
            importModel(storage, "empty.litertlm", ByteArray(0))
        }

        assertTrue(storage.list().isEmpty())
    }

    @Test
    fun `blank display name falls back to the default file name`() {
        val storage = newStorage()

        importModel(storage, "   ", bytes(seed = 6))

        assertEquals("model", storage.list().single().displayName)
    }
}
