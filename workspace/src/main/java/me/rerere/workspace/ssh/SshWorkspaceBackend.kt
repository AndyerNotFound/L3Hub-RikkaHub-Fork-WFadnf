package me.rerere.workspace.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import me.rerere.workspace.WorkspaceBackend
import me.rerere.workspace.WorkspaceBackgroundCommand
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceConfig
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceLayout
import me.rerere.workspace.WorkspacePaths
import me.rerere.workspace.WorkspaceStorageArea
import me.rerere.workspace.fileTooLargeCause
import me.rerere.workspace.readBoundedWorkspaceFile
import me.rerere.workspace.shellQuote
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 远程 SSH 主机上的工作区: 文件走 SFTP, 命令走 exec channel。
 *
 * 存储区映射: [WorkspaceStorageArea.FILES] 为远程工作目录, [WorkspaceStorageArea.LINUX] 为远程根目录。
 */
class SshWorkspaceBackend(
    private val pool: SshConnectionPool,
    private val id: String,
    private val config: SshConnectionConfig,
    private val workspaceConfig: WorkspaceConfig = WorkspaceConfig(),
) : WorkspaceBackend {
    override val layout = layoutOf(config.remoteDir)

    private val workspaceDir = layout.workspaceDir

    override fun shell(): String = pool.use(id, config, retry = true) { it.shell }

    override fun listFiles(area: WorkspaceStorageArea, path: String): List<WorkspaceFileEntry> {
        val dir = layout.absolutePath(area, path)
        // ls 内部会先 stat 目标目录, 这里不再多 stat 一次
        val entries = try {
            withSftp(area, retry = true) { sftp ->
                sftp.ls(dir.sftpQuote()).filter { it.filename != "." && it.filename != ".." }
            }
        } catch (e: SftpException) {
            throw e.wrap(path)
        }
        val linkedDirs = linkedDirectories(area, dir, entries)
        return entries
            .map { entry ->
                val child = dir.child(entry.filename)
                // 指向目录的软链按目录展示, 和本地文件浏览器的行为一致
                val isDirectory = entry.attrs.isDir || entry.filename in linkedDirs
                entry.attrs.toEntry(relativePath(area, child), entry.filename, isDirectory)
            }
            .sortedWith(compareBy<WorkspaceFileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .take(workspaceConfig.maxListEntries)
    }

    /**
     * 软链接要 stat 才知道指向的是不是目录。目录里链接多时 (如 /usr/bin) 逐个 stat 就是成百上千次往返,
     * 一条 shell 命令能一次问完; 服务器不允许 exec 或输出被截断时再退回逐个 stat。
     */
    private fun linkedDirectories(
        area: WorkspaceStorageArea,
        dir: String,
        entries: List<ChannelSftp.LsEntry>,
    ): Set<String> {
        val links = entries.filter { it.attrs.isLink && !it.attrs.isDir }.map { it.filename }
        if (links.isEmpty()) return emptySet()
        scanDirectories(dir)?.let { found -> return links.filterTo(mutableSetOf()) { it in found } }
        return withSftp(area, retry = true) { sftp ->
            links.filterTo(mutableSetOf()) { name -> sftp.statOrNull(dir.child(name))?.isDir == true }
        }
    }

    /** 一次 exec 列出目录下的子目录名; 不支持 shell 或输出被截断时返回 null */
    private fun scanDirectories(dir: String): Set<String>? {
        val command = "cd ${dir.shellQuote()} 2>/dev/null || exit 1; " +
            "for f in * .[!.]*; do [ -d \"\$f\" ] && printf '%s\\0' \"\$f\"; done"
        val result = runCatching { pool.use(id, config, retry = true) { it.exec(command, SCAN_TIMEOUT_MS) } }
            .getOrNull() ?: return null
        if (result.exitCode != 0 || result.truncated) return null
        return result.stdout.split('\u0000').filter { it.isNotEmpty() }.toSet()
    }

    override fun readText(path: String): String {
        val file = layout.absolutePath(WorkspaceStorageArea.FILES, path)
        return withSftp(WorkspaceStorageArea.FILES, retry = true) { sftp ->
            val size = sftp.requireFile(file, path).size
            require(size <= workspaceConfig.maxReadBytes) { "File is too large to read: $size bytes" }
            // 不用 get 返回的流: 它关闭时要读完未到的回应, 读取被打断后会再次卡住
            ByteArrayOutputStream().also { sftp.get(file.sftpQuote(), it) }.toString(Charsets.UTF_8.name())
        }
    }

    override fun writeText(path: String, text: String, overwrite: Boolean): WorkspaceFileEntry {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= workspaceConfig.maxWriteBytes) {
            "Content is too large to write: ${bytes.size} bytes"
        }
        val file = layout.absolutePath(WorkspaceStorageArea.FILES, path)
        return withSftp(WorkspaceStorageArea.FILES, retry = false) { sftp ->
            sftp.writeBytes(file, bytes, overwrite, path)
                .toEntry(relativePath(WorkspaceStorageArea.FILES, file), file.fileName())
        }
    }

    override fun importFile(
        area: WorkspaceStorageArea,
        destinationPath: String,
        fileName: String,
        inputStream: InputStream,
    ): WorkspaceFileEntry {
        val targetPath = if (destinationPath.isBlank()) fileName else "$destinationPath/$fileName"
        val file = layout.absolutePath(area, targetPath)
        return inputStream.use { input ->
            withSftp(area, retry = false) { sftp ->
                sftp.mkdirs(file.parentPath())
                val target = if (sftp.statOrNull(file) == null) file else sftp.resolveConflict(file)
                sftp.put(input, target.sftpQuote(), ChannelSftp.OVERWRITE)
                sftp.requireStat(target, targetPath).toEntry(relativePath(area, target), target.fileName())
            }
        }
    }

    override fun fileSize(area: WorkspaceStorageArea, path: String): Long {
        val file = layout.absolutePath(area, path)
        return withSftp(area, retry = true) { it.requireFile(file, path).size }
    }

    override fun exportFile(area: WorkspaceStorageArea, path: String, outputStream: OutputStream) {
        val file = layout.absolutePath(area, path)
        withSftp(area, retry = false) { sftp -> sftp.export(file, path, outputStream) }
    }

    override fun deleteFile(area: WorkspaceStorageArea, path: String, recursive: Boolean): Boolean {
        require(path.isNotBlank() && path != ".") { "Refusing to delete workspace root" }
        val target = layout.absolutePath(area, path)
        require(target != layout.rootOf(area)) { "Refusing to delete workspace root" }
        return withSftp(area, retry = false) { sftp ->
            // 用 lstat: 删除指向目录的软链时只删链接本身
            val attrs = try {
                sftp.lstat(target.sftpQuote())
            } catch (e: SftpException) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return@withSftp false
                throw e.wrap(path)
            }
            if (attrs.isDir && !attrs.isLink) {
                require(recursive) { "Directory delete requires recursive = true" }
                sftp.deleteRecursively(target)
            } else {
                sftp.rm(target.sftpQuote())
            }
            true
        }
    }

    override fun absoluteFileSize(path: String): Long =
        withAbsoluteFile(path, retry = true) { _, _, _, attrs -> attrs.size }

    override fun exportAbsoluteFile(path: String, outputStream: OutputStream) =
        withAbsoluteFile(path, retry = false) { _, sftp, file, _ ->
            outputStream.use { sftp.get(file.sftpQuote(), it) }
        }

    override fun readAbsoluteFile(path: String, maxBytes: Int): ByteArray =
        withAbsoluteFile(path, retry = false) { connection, sftp, file, attrs ->
            readBoundedWorkspaceFile(path, attrs.size, maxBytes) { output ->
                try {
                    sftp.get(file.sftpQuote(), output)
                } catch (e: Exception) {
                    // The transfer stopped mid-stream, so the channel must not be reused.
                    val limitError = e.fileTooLargeCause() ?: throw e
                    connection.invalidateSftp()
                    throw limitError
                }
            }
        }

    private fun <T> withAbsoluteFile(
        path: String,
        retry: Boolean,
        block: (SshConnection, ChannelSftp, String, SftpATTRS) -> T,
    ): T {
        val file = requireAbsolute(path)
        return pool.use(id, config, retry) { connection ->
            connection.sftp { sftp -> block(connection, sftp, file, sftp.requireFile(file, path)) }
        }
    }

    override fun writeAbsoluteText(path: String, text: String, overwrite: Boolean): WorkspaceFileEntry {
        val file = requireAbsolute(path)
        return pool.use(id, config, retry = false) { connection ->
            connection.sftp { sftp ->
                sftp.writeBytes(file, text.toByteArray(Charsets.UTF_8), overwrite, path)
                    .toEntry(file, file.fileName())
            }
        }
    }

    override fun localFile(area: WorkspaceStorageArea, path: String): File? = null

    override fun localAbsoluteFile(path: String): File? = null

    override fun executeCommand(
        command: String,
        cwd: String,
        timeoutMillis: Long,
        stdin: ByteArray?,
    ): WorkspaceCommandResult {
        val workingDir = layout.absolutePath(WorkspaceStorageArea.FILES, cwd)
        return pool.use(id, config, retry = false) { connection ->
            connection.ensureDir(workspaceDir)
            // 远程登录 shell 不一定是 POSIX shell, 统一交给探测到的 shell 执行;
            // 命令通过位置参数传入, eval "$2" 对命令文本只求值一次
            val remoteCommand = listOf(
                "env",
                "CI=true",
                "NO_COLOR=1",
                "PAGER=cat",
                connection.shell,
                "-l",
                "-c",
                "cd -- \"\$1\" && eval \"\$2\"",
                "rikkahub",
                workingDir,
                command,
            ).joinToString(" ") { it.shellQuote() }
            connection.exec(remoteCommand, timeoutMillis, stdin)
        }
    }

    override fun startBackgroundCommand(command: String, cwd: String, taskId: String): WorkspaceBackgroundCommand {
        val workingDir = layout.absolutePath(WorkspaceStorageArea.FILES, cwd)
        val logFile = "${SshBackgroundCommand.BACKGROUND_DIR}/$taskId.log"
        val pidFile = "${SshBackgroundCommand.BACKGROUND_DIR}/$taskId.pid"
        val statusFile = "${SshBackgroundCommand.BACKGROUND_DIR}/$taskId.status"
        val shell = pool.use(id, config, retry = false) { connection ->
            connection.ensureDir(workspaceDir)
            // 命令经位置参数传入, 由登录 shell 执行, 与 executeCommand 保持一致;
            // 内层 sh 也用探测到的 shell, 避免命令里的 bash 特性在回退的 /bin/sh 上失效
            val remoteCommand = listOf(
                "env", "CI=true", "NO_COLOR=1", "PAGER=cat",
                connection.shell, "-l", "-c", BACKGROUND_START_SCRIPT,
                "rikkahub", workingDir, command, logFile, statusFile, pidFile, connection.shell,
            ).joinToString(" ") { it.shellQuote() }
            val result = connection.exec(remoteCommand, START_TIMEOUT_MS)
            if (result.exitCode != 0) {
                error("Failed to start the background command: ${result.stderr.ifBlank { result.stdout }}")
            }
            connection.shell
        }
        return SshBackgroundCommand(pool, id, config, shell, logFile, pidFile, statusFile)
    }

    /** 打开交互式终端: 在工作目录下启动用户的登录 shell */
    fun openShell(columns: Int, rows: Int): SshShell =
        pool.openShell(id, config) { connection, onClose ->
            connection.ensureDir(workspaceDir)
            // 外层交给探测到的 POSIX shell 解析, 再 exec 用户自己的登录 shell ($SHELL 缺失时退回探测结果)
            val remoteCommand = listOf(
                connection.shell,
                "-c",
                "cd -- \"\$1\"; exec \"\${SHELL:-\$0}\" -l",
                connection.shell,
                workspaceDir,
            ).joinToString(" ") { it.shellQuote() }
            connection.openShell(remoteCommand, columns, rows, onClose)
        }

    /** 文件区操作前确保远程工作目录存在, 和本地工作区自动创建目录的行为一致 */
    private fun <T> withSftp(area: WorkspaceStorageArea, retry: Boolean, block: (ChannelSftp) -> T): T =
        pool.use(id, config, retry) { connection ->
            if (area == WorkspaceStorageArea.FILES) connection.ensureDir(workspaceDir)
            connection.sftp(block)
        }

    private fun relativePath(area: WorkspaceStorageArea, path: String): String =
        WorkspacePaths.relativeTo(layout.rootOf(area), path) ?: path

    private fun requireAbsolute(path: String): String =
        WorkspacePaths.normalize(path) ?: throw IllegalArgumentException("Path must be an absolute path: $path")

    companion object {
        private const val SCAN_TIMEOUT_MS = 10_000L
        private const val START_TIMEOUT_MS = 15_000L

        /**
         * 后台任务的启动脚本。位置参数: \$1=工作目录, \$2=命令, \$3=日志, \$4=状态文件, \$5=pid 文件, \$6=内层 shell。
         *
         * 先建目录并预创建日志文件 (起不来要当场失败, 不能等子进程去开重定向); 有 setsid 就脱离会话,
         * 没有就退回 nohup。后台子 shell 先把自身的 $$ 写进 pid 文件 (setsid fork 过时外层 \$! 可能不是组长,
         * 内层的 $$ 一定是真正的会话/进程组组长), 再在子 shell 里 eval 命令
         * (命令里的 exit 只会结束这个子 shell, 包装进程不会一起退出), 输出写日志,
         * 退出码先写临时状态文件再 rename, 这样等待方读到的状态文件一定是完整内容。
         */
        private const val BACKGROUND_START_SCRIPT = """cd -- "${'$'}1" || exit 1
mkdir -p "${'$'}{5%/*}" 2>/dev/null || exit 1
: > "${'$'}3" 2>/dev/null || { printf '%s\n' 'cannot create log file' >&2; exit 1; }
if command -v setsid >/dev/null 2>&1; then
  setsid "${'$'}6" -c 'printf "%s\n" "$$" > "${'$'}4"; ( eval "${'$'}1" ) > "${'$'}2" 2>&1; printf "%s\n" "${'$'}?" > "${'$'}3.tmp" && mv "${'$'}3.tmp" "${'$'}3"' bg "${'$'}2" "${'$'}3" "${'$'}4" "${'$'}5" </dev/null >/dev/null 2>&1 &
else
  nohup "${'$'}6" -c 'printf "%s\n" "$$" > "${'$'}4"; ( eval "${'$'}1" ) > "${'$'}2" 2>&1; printf "%s\n" "${'$'}?" > "${'$'}3.tmp" && mv "${'$'}3.tmp" "${'$'}3"' bg "${'$'}2" "${'$'}3" "${'$'}4" "${'$'}5" </dev/null >/dev/null 2>&1 &
fi
printf '%s\n' "${'$'}!" > "${'$'}5"
"""

        /** 不需要连接即可得到的目录约定, 供界面展示路径使用 */
        fun layoutOf(remoteDir: String): WorkspaceLayout {
            val workspaceDir = WorkspacePaths.normalize(remoteDir)
                ?: throw IllegalArgumentException("Remote directory must be an absolute path: $remoteDir")
            return WorkspaceLayout(
                workspaceDir = workspaceDir,
                writableRoots = listOf(workspaceDir, "/tmp"),
            )
        }
    }

    private fun ChannelSftp.requireStat(path: String, displayPath: String): SftpATTRS =
        try {
            statOrNull(path)
        } catch (e: SftpException) {
            throw e.wrap(displayPath)
        } ?: throw IllegalArgumentException("Path does not exist: $displayPath")

    private fun ChannelSftp.requireFile(path: String, displayPath: String): SftpATTRS {
        val attrs = try {
            statOrNull(path)
        } catch (e: SftpException) {
            throw e.wrap(displayPath)
        } ?: throw IllegalArgumentException("File does not exist: $displayPath")
        require(attrs.isReg) { "Path is not a file: $displayPath" }
        return attrs
    }

    private fun ChannelSftp.export(path: String, displayPath: String, outputStream: OutputStream) {
        requireFile(path, displayPath)
        outputStream.use { get(path.sftpQuote(), it) }
    }

    private fun ChannelSftp.writeBytes(
        path: String,
        bytes: ByteArray,
        overwrite: Boolean,
        displayPath: String,
    ): SftpATTRS {
        val existing = statOrNull(path)
        require(existing == null || overwrite) { "File already exists: $displayPath" }
        require(existing == null || !existing.isDir) { "Path is not a file: $displayPath" }
        mkdirs(path.parentPath())
        put(ByteArrayInputStream(bytes), path.sftpQuote(), ChannelSftp.OVERWRITE)
        return requireStat(path, displayPath)
    }

    private fun ChannelSftp.resolveConflict(path: String): String {
        val parent = path.parentPath()
        val name = path.fileName()
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isNotEmpty() && stem != name) ".$it" else "" }
        var n = 1
        var candidate: String
        do {
            candidate = parent.child("$stem ($n)$ext")
            n++
        } while (statOrNull(candidate) != null)
        return candidate
    }

    private fun ChannelSftp.deleteRecursively(dir: String) {
        ls(dir.sftpQuote())
            .filter { it.filename != "." && it.filename != ".." }
            .forEach { entry ->
                val child = dir.child(entry.filename)
                if (entry.attrs.isDir && !entry.attrs.isLink) {
                    deleteRecursively(child)
                } else {
                    rm(child.sftpQuote())
                }
            }
        rmdir(dir.sftpQuote())
    }

    private fun SftpATTRS.toEntry(path: String, name: String, isDirectory: Boolean = isDir) = WorkspaceFileEntry(
        path = path,
        name = name,
        isDirectory = isDirectory,
        sizeBytes = if (isReg) size else 0L,
        // SFTP v3 的 mtime 是无符号 32 位秒数
        updatedAt = (mTime.toLong() and 0xFFFFFFFFL) * 1_000L,
    )

    private fun SftpException.wrap(displayPath: String): IOException = when (id) {
        ChannelSftp.SSH_FX_PERMISSION_DENIED -> IOException("Permission denied: $displayPath", this)
        else -> IOException("SFTP error on $displayPath: $message", this)
    }
}

private fun String.child(name: String): String = if (this == "/") "/$name" else "$this/$name"

private fun String.parentPath(): String = substringBeforeLast('/').ifEmpty { "/" }

private fun String.fileName(): String = substringAfterLast('/').ifEmpty { "/" }
