package me.rerere.workspace.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpProgressMonitor
import me.rerere.workspace.WorkspaceBackgroundCommand
import me.rerere.workspace.shellQuote
import java.io.ByteArrayOutputStream

/**
 * 远程 SSH 主机上的后台命令。
 *
 * 启动时把命令放进 setsid（回退 nohup）的子 shell 里脱离会话运行：stdout/stderr 写日志文件，
 * 退出码写状态文件（先写临时文件再 rename，保证读到的一定是完整内容），进程号写 pid 文件，
 * 启动 exec 立即返回。之后：
 *
 * - [waitFor] 用一条阻塞的 exec 在远程轮询：状态文件出现就读退出码，进程消失且无状态文件则 -1；
 * - [outputSize] / [readOutput] 走 SFTP 读日志文件（按 offset 取区间，不整文件进内存）；
 * - [kill] 先按进程组 TERM，宽限期后 KILL；进程组不存在（nohup 回退路径）时退回
 *   pkill 子进程 + 单进程。
 */
internal class SshBackgroundCommand(
    private val pool: SshConnectionPool,
    private val workspaceId: String,
    private val config: SshConnectionConfig,
    private val shell: String,
    private val logFile: String,
    private val pidFile: String,
    private val statusFile: String,
) : WorkspaceBackgroundCommand {
    override val logPath: String get() = logFile

    override fun waitFor(): Int {
        val result = try {
            pool.use(workspaceId, config, retry = true) { connection ->
                connection.exec(waitCommand(), WAIT_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            null
        }
        // exec 正常结束时 stdout 就是退出码; 连接断开时退回读状态文件
        result?.stdout?.trim()?.toIntOrNull() ?: readStatusFile() ?: -1
    }

    override fun outputSize(): Long = pool.use(workspaceId, config, retry = true) { connection ->
        connection.sftp { sftp -> sftp.statOrNull(logFile)?.size ?: 0L }
    }

    override fun readOutput(offset: Long, maxBytes: Int): ByteArray {
        require(offset >= 0) { "offset must not be negative" }
        require(maxBytes > 0) { "maxBytes must be positive" }
        return pool.use(workspaceId, config, retry = true) { connection ->
            connection.sftp { sftp -> readLogRange(sftp, offset, maxBytes) }
        }
    }

    override fun kill() {
        pool.use(workspaceId, config, retry = false) { connection ->
            connection.exec(killCommand(), KILL_TIMEOUT_MS)
        }
    }

    private fun readLogRange(sftp: ChannelSftp, offset: Long, maxBytes: Int): ByteArray {
        val attrs = sftp.statOrNull(logFile) ?: return ByteArray(0)
        if (offset >= attrs.size) return ByteArray(0)
        val length = minOf(maxBytes.toLong(), attrs.size - offset).toInt()
        val out = ByteArrayOutputStream(length)
        // 只有 RESUME 模式才让 skip 生效（读从 offset 开始）；monitor.count 返回 false 时
        // JSch 会停掉后续读取，缓冲区最多多出一个包，最后按 length 截断
        val monitor = object : SftpProgressMonitor {
            private var transferred = 0L
            override fun init(op: Int, src: String?, dest: String?, max: Long) = Unit
            override fun count(count: Long): Boolean {
                transferred += count
                return transferred < length
            }
            override fun end() = Unit
        }
        sftp.get(logFile.sftpQuote(), out, monitor, ChannelSftp.RESUME, offset)
        return out.toByteArray().copyOf(minOf(out.size(), length))
    }

    private fun readStatusFile(): Int? = try {
        pool.use(workspaceId, config, retry = true) { connection ->
            connection.sftp { sftp ->
                ByteArrayOutputStream().use { out ->
                    sftp.get(statusFile.sftpQuote(), out)
                    out.toString(Charsets.UTF_8.name()).trim().toIntOrNull()
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun waitCommand(): String = remoteCommand(WAIT_SCRIPT, statusFile, pidFile)

    private fun killCommand(): String = remoteCommand(KILL_SCRIPT, pidFile)

    private fun remoteCommand(script: String, vararg args: String): String =
        listOf(
            "env", "CI=true", "NO_COLOR=1", "PAGER=cat",
            shell, "-l", "-c", script,
            "rikkahub", *args,
        ).joinToString(" ") { it.shellQuote() }

    companion object {
        /** 远程存放后台命令日志 / pid / 状态文件的目录 */
        const val BACKGROUND_DIR = "/tmp/rikkahub/background"

        /**
         * 阻塞式等待: 状态文件先出现就读退出码; 进程连续 3 秒失联且没有状态文件时报 -1。
         * pid 从文件读 (不能把文件路径当 pid 用); 容忍 3 次是因为启动瞬间 pid 文件可能还是
         * setsid 未 fork 前的旧值, 内层 shell 随后会用自己的 \$\$ 覆盖成真正的进程组组长。
         */
        private const val WAIT_SCRIPT = """miss=0
while :; do
  if [ -f "${'$'}1" ]; then cat "${'$'}1"; exit 0; fi
  pid=${'$'}(cat "${'$'}2" 2>/dev/null)
  case ${'$'}pid in
    ""|*[!0-9]*) sleep 1; continue ;;
  esac
  if kill -0 "${'$'}pid" 2>/dev/null; then
    miss=0
  else
    miss=${'$'}((miss + 1))
    if [ "${'$'}miss" -ge 3 ]; then printf '%s\n' -1; exit 0; fi
  fi
  sleep 1
done"""

        /**
         * 终止: 进程组 TERM, 3 秒后 KILL。组杀不带 `--`: dash 的 kill 把 `-- -pid` 报成
         * "Illegal number", 而 `kill -TERM -pid` 在 dash/bash/GNU 都能用; busybox 的 kill
         * 不认负 pid, 退回 pkill -g 按进程组匹配; 都没有时再逐层 pkill -P + 单进程。
         */
        private const val KILL_SCRIPT = """pid=${'$'}(cat "${'$'}1" 2>/dev/null) || exit 0
case ${'$'}pid in ""|*[!0-9]*) exit 0 ;; esac
kill -TERM -"${'$'}pid" 2>/dev/null || kill -TERM -- -"${'$'}pid" 2>/dev/null || pkill -TERM -g "${'$'}pid" 2>/dev/null || {
  pkill -TERM -P "${'$'}pid" 2>/dev/null
  kill -TERM "${'$'}pid" 2>/dev/null
}
sleep 3
kill -KILL -"${'$'}pid" 2>/dev/null || kill -KILL -- -"${'$'}pid" 2>/dev/null || pkill -KILL -g "${'$'}pid" 2>/dev/null || {
  pkill -KILL -P "${'$'}pid" 2>/dev/null
  kill -KILL "${'$'}pid" 2>/dev/null
}"""

        /** 后台任务最长等约 24.8 天; 超过这个上限的任务本来也随应用进程一起结束 */
        private const val WAIT_TIMEOUT_MS = Int.MAX_VALUE.toLong()
        private const val KILL_TIMEOUT_MS = 15_000L
    }
}
