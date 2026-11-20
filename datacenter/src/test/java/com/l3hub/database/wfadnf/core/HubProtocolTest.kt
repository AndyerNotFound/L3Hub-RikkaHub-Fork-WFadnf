package com.l3hub.database.wfadnf.core

import com.l3hub.syncproto.L3HubSyncProtocol
import com.l3hub.syncproto.PeerManifest
import com.l3hub.syncproto.PeerSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 线格式一旦漂移，数据中心就会对着老构建读出空清单；这里把键名与 JSON 形状钉住。 */
class HubProtocolTest {
    @Test
    fun `manifest json from the app decodes into the hub model`() {
        val json = """
            {
              "protocol": 2,
              "schemaVersion": 38,
              "capturedAt": 1700000000000,
              "conversations": [{"id": "c1", "updatedAt": 1700000000001}],
              "tombstones": [{"kind": "conversation", "targetId": "c0", "deletedAt": 1700000000002}],
              "files": [{"path": "upload/a.png", "size": 42}]
            }
        """.trimIndent()
        val manifest = HubJson.decodeFromString<PeerManifest>(json)
        assertEquals(2, manifest.protocol)
        assertEquals(38, manifest.schemaVersion)
        assertEquals("c1", manifest.conversations.single().id)
        assertEquals("c0", manifest.tombstones.single().targetId)
        assertEquals(42L, manifest.files.single().size)
    }

    @Test
    fun `schema json survives a round trip`() {
        val schema = PeerSchema(
            protocol = 2,
            schemaVersion = 38,
            identityHash = "abc",
            pageSize = 4096,
            databaseBytes = 8192,
            counts = mapOf("ConversationEntity" to 3L),
            objects = listOf(PeerSchema.SchemaObject("table", "ConversationEntity", "CREATE TABLE x(id TEXT)")),
        )
        val decoded = HubJson.decodeFromString<PeerSchema>(HubJson.encodeToString(schema))
        assertEquals(schema, decoded)
    }

    @Test
    fun `protocol keys stay stable`() {
        assertEquals("com.rikkahub.l3hub.wfadnf.sync", L3HubSyncProtocol.authority("com.rikkahub.l3hub.wfadnf"))
        assertEquals("content://p.sync/export/t", L3HubSyncProtocol.exportUri("p", "t"))
        assertEquals("schema", L3HubSyncProtocol.METHOD_SCHEMA)
        assertFalse(L3HubSyncProtocol.DATA_CENTER_PACKAGE in L3HubSyncProtocol.BUILD_PACKAGES)
    }

    @Test
    fun `archive paths must stay inside the mirror`() {
        assertTrue(ArchiveTool.isSafeRelative("upload/2026/a.png"))
        assertFalse(ArchiveTool.isSafeRelative("../etc/passwd"))
        assertFalse(ArchiveTool.isSafeRelative("/data/data/x"))
        assertFalse(ArchiveTool.isSafeRelative("upload/../../x"))
        assertFalse(ArchiveTool.isSafeRelative("upload\\x.txt"))
        assertFalse(ArchiveTool.isSafeRelative(""))
    }
}
