package me.rerere.workspace

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceReadLimitTest {
    @Test
    fun `allows a file exactly at the limit`() {
        val expected = byteArrayOf(1, 2, 3)
        val bytes = readBoundedWorkspaceFile("/notes.txt", fileSize = 3, maxBytes = 3) { output ->
            output.write(1)
            output.write(byteArrayOf(2, 3))
        }
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun `rejects an oversized file before reading`() {
        var readStarted = false
        assertThrows(WorkspaceFileTooLargeException::class.java) {
            readBoundedWorkspaceFile("/notes.txt", fileSize = 4, maxBytes = 3) { readStarted = true }
        }
        assertFalse(readStarted)
    }

    @Test
    fun `the limit error is found inside a jsch wrapper chain`() {
        val limitError = WorkspaceFileTooLargeException("/notes.txt", 4)
        val wrapped = SftpException(ChannelSftp.SSH_FX_FAILURE, "write failed", limitError)

        assertSame(limitError, wrapped.fileTooLargeCause())
        assertSame(limitError, IllegalStateException("outer", wrapped).fileTooLargeCause())
        assertNull(SftpException(ChannelSftp.SSH_FX_FAILURE, "unrelated").fileTooLargeCause())
    }

    @Test
    fun `enforces the limit even when a file grows`() {
        assertThrows(WorkspaceFileTooLargeException::class.java) {
            readBoundedWorkspaceFile("/notes.txt", fileSize = 2, maxBytes = 3) { output ->
                output.write(byteArrayOf(1, 2))
                output.write(byteArrayOf(3, 4))
            }
        }
    }
}
