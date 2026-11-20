package com.l3hub.syncproto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BundleFormatTest {
    @Test
    fun `a base layer and deltas stack into one chain`() {
        val dir = tempDir()
        val bundle = File(dir, "backup.l3b")
        BundleFormat.append(bundle, baseLayer(), payload(File(dir, "base.zip"), "rikka_hub.db", "base"))
        BundleFormat.append(
            bundle,
            deltaLayer(files = listOf(BundleFormat.Entry("upload/a.png", 3)), deleted = listOf("upload/old.png")),
            payload(File(dir, "d1.zip"), "sync_delta.db", "one"),
        )
        BundleFormat.append(
            bundle,
            deltaLayer(),
            payload(File(dir, "d2.zip"), "sync_delta.db", "two"),
        )

        val index = BundleFormat.readIndex(bundle)
        assertEquals(3, index.refs.size)
        assertEquals(index.validBytes, bundle.length())
        assertEquals(3, index.chain.size)
        assertTrue(index.layers.first().isBase)
        assertEquals(2, index.chain.count { !it.layer.isBase })
    }

    @Test
    fun `a half written tail is ignored and truncated before the next append`() {
        val dir = tempDir()
        val bundle = File(dir, "backup.l3b")
        BundleFormat.append(bundle, baseLayer(), payload(File(dir, "base.zip"), "rikka_hub.db", "base"))
        val complete = bundle.length()
        // 模拟写到一半断电：多出半截记录
        bundle.appendBytes(byteArrayOf(0x4C, 0x33, 0x42, 0x31, 0x10, 0x00))

        val index = BundleFormat.readIndex(bundle)
        assertEquals(1, index.refs.size)
        assertEquals(complete, index.validBytes)

        BundleFormat.append(bundle, deltaLayer(), payload(File(dir, "d1.zip"), "sync_delta.db", "one"))
        assertEquals(2, BundleFormat.readIndex(bundle).refs.size)
    }

    @Test
    fun `payload survives a round trip`() {
        val dir = tempDir()
        val bundle = File(dir, "backup.l3b")
        val base = payload(File(dir, "base.zip"), "rikka_hub.db", "base-bytes")
        BundleFormat.append(bundle, baseLayer(), base)
        val ref = BundleFormat.readIndex(bundle).refs.single()
        val extracted = File(dir, "extracted.zip")
        BundleFormat.extractPayload(bundle, ref, extracted)
        assertTrue(extracted.readBytes().contentEquals(base.readBytes()))
    }

    @Test
    fun `chain files follow later layers and deletions`() {
        val dir = tempDir()
        val bundle = File(dir, "backup.l3b")
        BundleFormat.append(
            bundle,
            baseLayer(files = listOf(
                BundleFormat.Entry("upload/a.png", 1),
                BundleFormat.Entry("upload/b.png", 2),
                BundleFormat.Entry("upload/c.png", 3),
            )),
            payload(File(dir, "base.zip"), "rikka_hub.db", "base"),
        )
        BundleFormat.append(
            bundle,
            deltaLayer(
                files = listOf(BundleFormat.Entry("upload/b.png", 20), BundleFormat.Entry("upload/d.png", 4)),
                deleted = listOf("upload/a.png"),
            ),
            payload(File(dir, "d1.zip"), "sync_delta.db", "one"),
        )
        val summary = BundleFormat.summarize(BundleFormat.readIndex(bundle).chain)
        assertEquals(mapOf("upload/b.png" to 20L, "upload/c.png" to 3L, "upload/d.png" to 4L), summary!!.files)
        assertEquals(setOf("upload/a.png"), BundleFormat.chainDeletions(BundleFormat.readIndex(bundle).chain))
    }

    @Test
    fun `a bare layer record has no file magic`() {
        val dir = tempDir()
        val layer = File(dir, "backup.l3b.d001")
        BundleFormat.append(layer, deltaLayer(), payload(File(dir, "d1.zip"), "sync_delta.db", "one"), withMagic = false)
        assertFalse(BundleFormat.isBundle(layer))
        assertEquals(1, BundleFormat.readIndex(layer, allowMissingMagic = true).refs.size)
    }

    @Test
    fun `a plain zip is not a bundle`() {
        val dir = tempDir()
        val zip = payload(File(dir, "plain.zip"), "rikka_hub.db", "x")
        assertFalse(BundleFormat.isBundle(zip))
        assertTrue(BundleFormat.readIndex(zip).refs.isEmpty())
    }

    private fun baseLayer(files: List<BundleFormat.Entry> = emptyList()) = BundleFormat.Layer(
        id = "base",
        kind = BundleFormat.KIND_BASE,
        createdAt = 1_000L,
        schemaVersion = 38,
        deviceId = "device-a",
        hasDatabase = true,
        files = files,
    )

    private fun deltaLayer(
        files: List<BundleFormat.Entry> = emptyList(),
        deleted: List<String> = emptyList(),
    ) = BundleFormat.Layer(
        id = "delta",
        kind = BundleFormat.KIND_DELTA,
        createdAt = 2_000L,
        since = 1_000L,
        schemaVersion = 38,
        deviceId = "device-a",
        hasDatabase = true,
        files = files,
        deletedFiles = deleted,
    )

    private fun payload(file: File, name: String, text: String): File {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.toByteArray())
            zip.closeEntry()
        }
        return file
    }

    private fun tempDir(): File = File.createTempFile("bundle-test", "").let { file ->
        file.delete()
        file.mkdirs()
        file
    }
}
