package me.rerere.rikkahub.data.sync.bridge

import me.rerere.rikkahub.data.sync.RestoreItem
import me.rerere.rikkahub.data.sync.RestoreMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** 两端靠枚举名传数据类别与恢复方式，改名或漏写会让同步静默丢内容，这里把线格式钉住。 */
class SyncBridgeTest {
    @Test
    fun `restore items survive the wire format`() {
        val items = setOf(RestoreItem.CHAT_RECORDS, RestoreItem.FILES)
        assertEquals(items, SyncBridge.decodeItems(SyncBridge.encodeItems(items)))
    }

    @Test
    fun `all restore items round trip`() {
        val items = RestoreItem.entries.toSet()
        assertEquals(items, SyncBridge.decodeItems(SyncBridge.encodeItems(items)))
    }

    @Test
    fun `unknown item names are ignored`() {
        assertEquals(
            setOf(RestoreItem.SETTINGS),
            SyncBridge.decodeItems(listOf("SETTINGS", "REMOVED_IN_A_LATER_BUILD")),
        )
    }

    @Test
    fun `missing mode falls back to merge`() {
        assertEquals(RestoreMode.MERGE, SyncBridge.decodeMode(null))
        assertEquals(RestoreMode.MERGE, SyncBridge.decodeMode("nonsense"))
        assertEquals(RestoreMode.OVERWRITE, SyncBridge.decodeMode(RestoreMode.OVERWRITE.name))
    }

    @Test
    fun `peer packages are the release and preview builds`() {
        assertEquals(
            listOf("com.rikkahub.l3hub.wfadnf", "com.rikkahub.l3hub.wfadnf.debug"),
            SyncBridge.PACKAGES,
        )
    }
}
