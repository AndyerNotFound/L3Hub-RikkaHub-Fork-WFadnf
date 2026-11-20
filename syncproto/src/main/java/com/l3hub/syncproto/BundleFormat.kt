package com.l3hub.syncproto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * `.l3b`：可追加的分层备份包。
 *
 * ```
 * 文件 := "L3HUBBND1" record*
 * record := "L3B1" | headerLen u32 | header JSON | payloadLen u64 | payload
 * payload := 与旧备份 zip 同构的一段（rikka_hub.db / sync_delta.db / settings.json / upload/…）
 * ```
 *
 * 第一层是 base（整库 + 全部附件 + 设置），之后每次备份只追加一个 delta 层：只含水位之后变化的
 * 会话、删除墓碑、变化的附件。追加是纯字节追加，前面写好的记录一个字节都不用改；读取恢复时按顺序
 * 把 delta 打进 base 的副本，得到一份完整的库。
 *
 * 远端（WebDAV / S3）不能原地追加，所以上传时把 base 与各层当成独立对象：
 * `xxx.l3b`（含文件头）+ `xxx.l3b.d001`（裸记录）。恢复时按顺序拼成一个文件即可，格式本身不做区分。
 *
 * 尾部半截记录（写到一半断电、上传中断）在读取时丢弃，下一次追加前按 [Index.validBytes] 截断。
 */
object BundleFormat {
    const val SUFFIX = "l3b"
    const val FORMAT_VERSION = 1

    const val KIND_BASE = "base"
    const val KIND_DELTA = "delta"

    /** 层里 payload 的固定条目名，与旧备份 zip 一致 */
    const val ENTRY_DATABASE = "rikka_hub.db"
    const val ENTRY_DELTA = "sync_delta.db"
    const val ENTRY_SETTINGS = "settings.json"

    private const val FILE_MAGIC = "L3HUBBND1"
    private const val RECORD_MAGIC = "L3B1"
    private const val RECORD_FIXED = 4 + 4 + 8
    private const val MAX_HEADER = 1 shl 20

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** 一个层：只描述这一层带了什么，不描述整条链的累计状态 */
    @Serializable
    data class Layer(
        val format: Int = FORMAT_VERSION,
        val id: String,
        val kind: String = KIND_DELTA,
        val createdAt: Long = 0L,
        /** 本层从哪个时刻切出；base 层为 0 */
        val since: Long = 0L,
        val schemaVersion: Int = 0,
        /** 生成这一层的设备；不是本机的链不能接着追加 */
        val deviceId: String = "",
        /** 本层是否带了一份完整的库（base）或增量库（delta）；只带附件的层为 false */
        val hasDatabase: Boolean = false,
        val payloadBytes: Long = 0L,
        /** 本层是否带 settings.json，及其内容的 SHA-256（前 16 位十六进制） */
        val settingsHash: String? = null,
        val counts: Map<String, Long> = emptyMap(),
        val files: List<Entry> = emptyList(),
        val deletedFiles: List<String> = emptyList(),
        val note: String = "",
    ) {
        val isBase: Boolean get() = kind == KIND_BASE
    }

    @Serializable
    data class Entry(val path: String, val size: Long = 0L)

    /** 一个记录在文件里的位置：[offset] 为记录头起点，[payloadOffset] 为 payload 起点 */
    data class Ref(
        val layer: Layer,
        val offset: Long,
        val payloadOffset: Long,
        val bytes: Long,
    ) {
        val payloadBytes: Long get() = layer.payloadBytes
    }

    data class Index(
        val refs: List<Ref>,
        val validBytes: Long,
        val hasMagic: Boolean,
    ) {
        val layers: List<Layer> get() = refs.map { it.layer }

        /** 最后一条链：从最后一个 base 层到末尾。没有 base 时为空 */
        val chain: List<Ref>
            get() {
                val start = refs.indexOfLast { it.layer.isBase }
                return if (start < 0) emptyList() else refs.subList(start, refs.size)
            }

        val last: Ref? get() = refs.lastOrNull()
    }

    /**
     * 一条链的累计状态：下一层要接着谁的设备、从哪个水位切、链上已经有哪些附件。
     * base 层的值决定链的归属与结构版本，后面各层只更新水位、附件与设置指纹。
     */
    data class ChainSummary(
        val deviceId: String,
        val schemaVersion: Int,
        val hasDatabase: Boolean,
        val layerCount: Int,
        val lastCreatedAt: Long,
        val files: Map<String, Long>,
        val settingsHash: String?,
    )

    fun summarize(chain: List<Ref>): ChainSummary? {
        val base = chain.firstOrNull { it.layer.isBase } ?: return null
        return ChainSummary(
            deviceId = base.layer.deviceId,
            schemaVersion = base.layer.schemaVersion,
            // 链首带不带数据库决定这条链的恢复能力；后面的层只是接着它写
            hasDatabase = base.layer.hasDatabase,
            layerCount = chain.size,
            lastCreatedAt = chain.last().layer.createdAt,
            files = chainFiles(chain),
            // 后面各层没带设置时沿用前面的那份
            settingsHash = chain.mapNotNull { it.layer.settingsHash }.lastOrNull(),
        )
    }

    fun isBundle(file: File): Boolean {
        if (!file.isFile || file.length() < FILE_MAGIC.length) return false
        val head = ByteArray(FILE_MAGIC.length)
        FileInputStream(file).use { input ->
            var read = 0
            while (read < head.size) {
                val count = input.read(head, read, head.size - read)
                if (count < 0) return false
                read += count
            }
        }
        return head.contentEquals(FILE_MAGIC.toByteArray(Charsets.US_ASCII))
    }

    /**
     * 读出文件里所有完整记录。
     *
     * [allowMissingMagic] 用于单独的一层对象（`xxx.l3b.d001`）：它只有记录，没有文件头。
     */
    fun readIndex(file: File, allowMissingMagic: Boolean = false): Index {
        if (!file.isFile || file.length() == 0L) return Index(emptyList(), 0L, false)
        val refs = mutableListOf<Ref>()
        FileInputStream(file).use { raw ->
            val input = BufferedInputStream(raw, 64 * 1024)
            val magic = ByteArray(FILE_MAGIC.length)
            val hasMagic = readFully(input, magic) && magic.contentEquals(FILE_MAGIC.toByteArray(Charsets.US_ASCII))
            if (!hasMagic && !allowMissingMagic) return Index(emptyList(), 0L, false)
            var position = if (hasMagic) FILE_MAGIC.length.toLong() else 0L
            while (true) {
                val start = position
                val record = ByteArray(RECORD_MAGIC.length)
                if (!readFully(input, record)) break
                if (!record.contentEquals(RECORD_MAGIC.toByteArray(Charsets.US_ASCII))) break
                val headerLength = readInt(input) ?: break
                if (headerLength <= 0 || headerLength > MAX_HEADER) break
                val headerBytes = ByteArray(headerLength)
                if (!readFully(input, headerBytes)) break
                val layer = runCatching {
                    json.decodeFromString<Layer>(headerBytes.toString(Charsets.UTF_8))
                }.getOrNull() ?: break
                val payload = readLong(input) ?: break
                if (payload < 0 || payload > file.length()) break
                if (!skipFully(input, payload)) break
                val bytes = RECORD_FIXED + headerLength + payload
                refs += Ref(
                    layer = layer.copy(payloadBytes = payload),
                    offset = start,
                    payloadOffset = start + RECORD_FIXED + headerLength,
                    bytes = bytes,
                )
                position = start + bytes
            }
            return Index(refs = refs, validBytes = position, hasMagic = hasMagic)
        }
    }

    /**
     * 追加一层。[target] 不存在时写入文件头；已存在时只用最后一条完整记录之后的字节。
     * 返回真正落盘的层（payloadBytes 以实际写出为准）。
     */
    fun append(target: File, layer: Layer, payload: File, withMagic: Boolean = true): Layer {
        require(payload.isFile) { "bundle payload is missing" }
        val existing = if (target.isFile && target.length() > 0L) readIndex(target) else null
        if (existing != null && !existing.hasMagic && existing.refs.isEmpty()) {
            error("Not a backup bundle: ${target.name}")
        }
        if (!withMagic && ((existing?.refs?.isEmpty() == false) || target.length() > 0L)) {
            error("A bare layer record must start in an empty file")
        }
        if (existing != null && existing.hasMagic && target.length() != existing.validBytes) {
            truncate(target, existing.validBytes)
        }
        val stored = layer.copy(payloadBytes = payload.length())
        val header = json.encodeToString(stored).toByteArray(Charsets.UTF_8)
        check(header.size <= MAX_HEADER) { "bundle layer header is too large" }
        target.parentFile?.let { check(it.isDirectory || it.mkdirs()) { "cannot create ${it.path}" } }
        FileOutputStream(target, true).use { raw ->
            val output = BufferedOutputStream(raw, 64 * 1024)
            if (existing == null && withMagic) standardCopy(FILE_MAGIC, output)
            standardCopy(RECORD_MAGIC, output)
            writeInt(output, header.size)
            output.write(header)
            writeLong(output, stored.payloadBytes)
            payload.inputStream().use { it.copyTo(output) }
            output.flush()
            raw.fd.sync()
        }
        return stored
    }

    /** 按 [Ref] 把一段记录原样复制成独立对象（远端的一层） */
    fun copyRecord(source: File, ref: Ref, destination: File) {
        destination.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
        RandomAccessFile(source, "r").use { input ->
            input.seek(ref.offset)
            FileOutputStream(destination).use { raw ->
                val output = BufferedOutputStream(raw, 64 * 1024)
                val buffer = ByteArray(64 * 1024)
                var remaining = ref.bytes
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                    if (read < 0) error("bundle record ended early")
                    output.write(buffer, 0, read)
                    remaining -= read
                }
                output.flush()
                raw.fd.sync()
            }
        }
    }

    /** 把一层的 payload 解出来；调用方负责读写与关闭 */
    fun extractPayload(source: File, ref: Ref, destination: File) {
        destination.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
        RandomAccessFile(source, "r").use { input ->
            input.seek(ref.payloadOffset)
            FileOutputStream(destination).use { raw ->
                val output = BufferedOutputStream(raw, 64 * 1024)
                val buffer = ByteArray(64 * 1024)
                var remaining = ref.payloadBytes
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                    if (read < 0) error("bundle payload ended early")
                    output.write(buffer, 0, read)
                    remaining -= read
                }
                output.flush()
                raw.fd.sync()
            }
        }
    }

    /**
     * 一条链上的附件全貌：后层覆盖前层，`deletedFiles` 把再前面的同名文件抹掉。
     * 同一层里删除先于添加，这样「删掉又加回来」在层内也能表达。
     */
    fun chainFiles(chain: List<Ref>): Map<String, Long> {
        val files = LinkedHashMap<String, Long>()
        for (ref in chain) {
            for (path in ref.layer.deletedFiles) files.remove(path)
            for (entry in ref.layer.files) files[entry.path] = entry.size
        }
        return files
    }

    fun chainDeletions(chain: List<Ref>): Set<String> = chain.flatMapTo(LinkedHashSet()) { it.layer.deletedFiles }

    /** 拼接若干文件（base + 各层）成一个完整包，用于远端分层对象的恢复 */
    fun concat(sources: List<File>, destination: File) {
        destination.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
        FileOutputStream(destination).use { raw ->
            val output = BufferedOutputStream(raw, 64 * 1024)
            for (source in sources) source.inputStream().use { it.copyTo(output) }
            output.flush()
            raw.fd.sync()
        }
    }

    fun truncate(file: File, length: Long) {
        RandomAccessFile(file, "rw").use { it.setLength(length) }
    }

    private fun standardCopy(text: String, output: OutputStream) {
        output.write(text.toByteArray(Charsets.US_ASCII))
    }

    private fun readFully(input: java.io.InputStream, target: ByteArray): Boolean {
        var read = 0
        while (read < target.size) {
            val count = input.read(target, read, target.size - read)
            if (count < 0) return false
            read += count
        }
        return true
    }

    private fun readInt(input: java.io.InputStream): Int? {
        val bytes = ByteArray(4)
        if (!readFully(input, bytes)) return null
        return (bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 24)
    }

    private fun readLong(input: java.io.InputStream): Long? {
        var value = 0L
        for (shift in 0 until 8) {
            val byte = input.read()
            if (byte < 0) return null
            value = value or ((byte.toLong() and 0xFF) shl (shift * 8))
        }
        return value
    }

    private fun writeInt(output: OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 24) and 0xFF)
    }

    private fun writeLong(output: OutputStream, value: Long) {
        for (shift in 0 until 8) output.write(((value ushr (shift * 8)) and 0xFF).toInt())
    }

    private fun skipFully(input: java.io.InputStream, count: Long): Boolean {
        var remaining = count
        val scratch = ByteArray(64 * 1024)
        while (remaining > 0) {
            val read = input.read(scratch, 0, minOf(remaining, scratch.size.toLong()).toInt())
            if (read < 0) return false
            remaining -= read
        }
        return true
    }
}
