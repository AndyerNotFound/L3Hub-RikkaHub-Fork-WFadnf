package me.rerere.ui.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class WebViewContentCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `stores large content outside navigation state`() {
        val content = "<html>${"preview".repeat(50_000)}</html>"

        val id = WebViewContentCache.store(temporaryFolder.root, content)

        assertEquals(64, id.length)
        assertEquals(content, WebViewContentCache.load(temporaryFolder.root, id))
    }

    @Test
    fun `reuses the same cache entry for identical content`() {
        val content = "<html>preview</html>"

        val firstId = WebViewContentCache.store(temporaryFolder.root, content)
        val secondId = WebViewContentCache.store(temporaryFolder.root, content)

        assertEquals(firstId, secondId)
        assertEquals(1, temporaryFolder.root.resolve("webview_content").listFiles()?.size)
    }

    @Test
    fun `rejects invalid cache ids`() {
        assertNull(WebViewContentCache.load(temporaryFolder.root, "../content"))
    }

    // 离线消息预览存的是原始文本，执行用的 HTML 预览存的是可渲染文档：两边目录不同，谁也不能替另一边交付
    @Test
    fun `offline message and html previews never read each other`() {
        val content = "<html>same bytes, different directory</html>"
        val htmlId = WebViewContentCache.store(temporaryFolder.root, content)
        val messageId = WebViewContentCache.storeOfflineMessage(temporaryFolder.root, content)

        // id 只由内容决定，两个缓存里是同一个名字
        assertEquals(htmlId, messageId)
        assertEquals(content, WebViewContentCache.load(temporaryFolder.root, htmlId))
        assertEquals(content, WebViewContentCache.loadOfflineMessage(temporaryFolder.root, messageId))
    }

    @Test
    fun `each cache keeps its own directory`() {
        WebViewContentCache.store(temporaryFolder.root, "<html>preview</html>")
        WebViewContentCache.storeOfflineMessage(temporaryFolder.root, "message")

        val htmlDirectory = File(temporaryFolder.root, "webview_content")
        val messageDirectory = File(temporaryFolder.root, "offline_message_preview")

        assertTrue(htmlDirectory.isDirectory)
        assertTrue(messageDirectory.isDirectory)
        assertEquals(1, htmlDirectory.listFiles()?.size)
        assertEquals(1, messageDirectory.listFiles()?.size)
        // 各自装着各自的内容，不是把同一条写了两遍
        assertEquals("<html>preview</html>", htmlDirectory.listFiles()!!.single().readText())
        assertEquals("message", messageDirectory.listFiles()!!.single().readText())
    }

    @Test
    fun `html entry is invisible to the offline message cache`() {
        val id = WebViewContentCache.store(temporaryFolder.root, "<html>html only</html>")

        assertNull(WebViewContentCache.loadOfflineMessage(temporaryFolder.root, id))
    }

    @Test
    fun `offline message entry is invisible to the html cache`() {
        val id = WebViewContentCache.storeOfflineMessage(temporaryFolder.root, "message only")

        assertNull(WebViewContentCache.load(temporaryFolder.root, id))
    }

    @Test
    fun `offline message round trip keeps text and reuses its entry`() {
        val source = "第一段\n\n第二段"

        val firstId = WebViewContentCache.storeOfflineMessage(temporaryFolder.root, source)
        val secondId = WebViewContentCache.storeOfflineMessage(temporaryFolder.root, source)

        assertEquals(firstId, secondId)
        assertEquals(64, firstId.length)
        assertEquals(source, WebViewContentCache.loadOfflineMessage(temporaryFolder.root, firstId))
        assertEquals(1, File(temporaryFolder.root, "offline_message_preview").listFiles()?.size)
    }

    // 写入走临时文件再改名，留下的临时文件会让条目数对不上
    @Test
    fun `offline message store leaves no temporary files behind`() {
        repeat(3) { index -> WebViewContentCache.storeOfflineMessage(temporaryFolder.root, "message $index") }

        val names = File(temporaryFolder.root, "offline_message_preview").listFiles().orEmpty().map { it.name }
        assertEquals(3, names.size)
        assertTrue(names.all { it.length == 64 && it.all { char -> char in '0'..'9' || char in 'a'..'f' } })
    }

    @Test
    fun `offline message rejects ids that are not sha256 hex`() {
        val id = WebViewContentCache.storeOfflineMessage(temporaryFolder.root, "message")

        assertNull(WebViewContentCache.loadOfflineMessage(temporaryFolder.root, "../$id"))
        assertNull(WebViewContentCache.loadOfflineMessage(temporaryFolder.root, "A".repeat(64)))
        assertNull(WebViewContentCache.loadOfflineMessage(temporaryFolder.root, id.dropLast(1)))
        assertNull(WebViewContentCache.loadOfflineMessage(temporaryFolder.root, "$id "))
        assertNull(WebViewContentCache.loadOfflineMessage(temporaryFolder.root, ""))
    }

    // 单条上限 800_004 字节：字符数与字节数都卡这个数，超了直接拒收而不是悄悄截断
    @Test
    fun `offline message refuses oversized sources`() {
        assertThrows(IllegalArgumentException::class.java) {
            WebViewContentCache.storeOfflineMessage(temporaryFolder.root, "a".repeat(800_005))
        }
        assertTrue(File(temporaryFolder.root, "offline_message_preview").listFiles().isNullOrEmpty())

        val atLimit = "a".repeat(800_004)
        val id = WebViewContentCache.storeOfflineMessage(temporaryFolder.root, atLimit)
        assertEquals(800_004L, File(File(temporaryFolder.root, "offline_message_preview"), id).length())
    }

    // 超过单条上限的条目（比如旧版本留下的）读的时候整条作废，不返回半截文本
    @Test
    fun `offline message ignores an entry larger than the entry limit`() {
        val directory = File(temporaryFolder.root, "offline_message_preview").apply { mkdirs() }
        val oversized = File(directory, "0".repeat(64))
        oversized.writeBytes(ByteArray(800_005) { 'a'.code.toByte() })

        assertNull(WebViewContentCache.loadOfflineMessage(temporaryFolder.root, oversized.name))
    }

    @Test
    fun `offline message older than seven days reads as missing`() {
        val id = WebViewContentCache.storeOfflineMessage(temporaryFolder.root, "stale")
        val file = File(File(temporaryFolder.root, "offline_message_preview"), id)
        file.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8))

        assertNull(WebViewContentCache.loadOfflineMessage(temporaryFolder.root, id))
        assertTrue(file.isFile)
    }

    @Test
    fun `offline message load refreshes the entry so it survives`() {
        val id = WebViewContentCache.storeOfflineMessage(temporaryFolder.root, "keep me")
        val file = File(File(temporaryFolder.root, "offline_message_preview"), id)
        val aged = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(6)
        file.setLastModified(aged)

        assertEquals("keep me", WebViewContentCache.loadOfflineMessage(temporaryFolder.root, id))
        assertTrue(file.lastModified() > aged)
    }

    @Test
    fun `storing an offline message sweeps expired siblings`() {
        val staleId = WebViewContentCache.storeOfflineMessage(temporaryFolder.root, "stale")
        val directory = File(temporaryFolder.root, "offline_message_preview")
        File(directory, staleId).setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8))

        WebViewContentCache.storeOfflineMessage(temporaryFolder.root, "fresh")

        assertFalse(File(directory, staleId).exists())
        assertEquals(1, directory.listFiles()?.size)
    }

    @Test
    fun `storing html content sweeps expired previews`() {
        val staleId = WebViewContentCache.store(temporaryFolder.root, "<html>stale</html>")
        val directory = File(temporaryFolder.root, "webview_content")
        File(directory, staleId).setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8))

        WebViewContentCache.store(temporaryFolder.root, "<html>fresh</html>")

        assertFalse(File(directory, staleId).exists())
        assertEquals(1, directory.listFiles()?.size)
    }

    // 条目上限 128：新条目算一个，其余按最近使用时间留 127 个，多出来的删掉
    @Test
    fun `offline message cache keeps at most 128 entries`() {
        repeat(130) { index -> WebViewContentCache.storeOfflineMessage(temporaryFolder.root, "message $index") }

        assertEquals(128, File(temporaryFolder.root, "offline_message_preview").listFiles()?.size)
    }

    // 总量上限 16 MiB：单条 800_000 字节时只装得下 20 条，超出字节预算的按最近使用时间淘汰
    @Test
    fun `offline message cache keeps total size within 16 MiB`() {
        val payload = "a".repeat(800_000)
        repeat(21) { index ->
            WebViewContentCache.storeOfflineMessage(temporaryFolder.root, payload + index)
        }

        val files = File(temporaryFolder.root, "offline_message_preview").listFiles().orEmpty()
        assertEquals(20, files.size)
        assertTrue(files.sumOf { it.length() } <= 16L * 1024 * 1024)
    }
}
