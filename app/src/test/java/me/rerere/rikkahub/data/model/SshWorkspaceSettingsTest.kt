package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SshWorkspaceSettingsTest {
    private val settings = SshWorkspaceSettings(
        host = "example.com",
        username = "root",
        remoteDir = "/root/projects",
        encryptedPassword = "pw:ok",
        encryptedPrivateKey = "key:ok",
        encryptedPassphrase = "pass:ok",
    )

    @Test
    fun `keeps secrets that can be decrypted`() {
        assertEquals(settings, settings.dropUndecryptableSecrets { true })
    }

    @Test
    fun `drops every secret that cannot be decrypted`() {
        val cleaned = settings.dropUndecryptableSecrets { false }
        assertNull(cleaned.encryptedPassword)
        assertNull(cleaned.encryptedPrivateKey)
        assertNull(cleaned.encryptedPassphrase)
    }

    @Test
    fun `keeps usable secrets and drops the rest`() {
        val cleaned = settings.dropUndecryptableSecrets { it == "pw:ok" }
        assertEquals("pw:ok", cleaned.encryptedPassword)
        assertNull(cleaned.encryptedPrivateKey)
        assertNull(cleaned.encryptedPassphrase)
    }

    @Test
    fun `keeps connection settings untouched`() {
        val cleaned = settings.dropUndecryptableSecrets { false }
        assertEquals("example.com", cleaned.host)
        assertEquals("root", cleaned.username)
        assertEquals("/root/projects", cleaned.remoteDir)
    }
}
