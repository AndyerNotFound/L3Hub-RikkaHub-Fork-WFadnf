package me.rerere.rikkahub.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File

/**
 * 启动埋点：把「启动走到哪一步 / 主线程卡在哪一行」落到一个**不用 adb 也能取到**的文件里。
 *
 * 场景是「装完新包打不开」：应用起不来就读不到 logcat，主线程死循环时也弹不出任何东西，
 * 只能靠状态自己说话。
 *
 * - 启动路径上几个关键点各记一行，同名只记第一次（这些点有的在组合里，重组不能每帧写盘）；
 * - 每记一行就重写一遍手机 Download 目录里那份（MediaStore，不需要存储权限）：
 *   进程不管是卡死、被 halt 还是被系统杀掉，文件里都停在最后走到的那一步；
 * - 有栈的时候（主线程卡死、未捕获异常、设置读盘失败）把栈一起写进去。
 *
 * 定位完就该连同 StartupWatchdog 一起删掉。
 */
object StartupTrace {
    private const val TAG = "StartupTrace"
    private const val INTERNAL_NAME = "startup-trace.txt"
    private const val PUBLIC_NAME = "l3hub-startup-trace.txt"

    /** 单测里 android.os.SystemClock 也是空实现，会抛；埋点自己不能因此把业务带崩。 */
    private fun now(): Long = runCatching { SystemClock.uptimeMillis() }.getOrDefault(0L)

    private val startedAt = now()
    private val lock = Any()
    private val buffer = StringBuilder()
    private val seen = HashSet<String>()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var publicUri: Uri? = null

    /**
     * 越早越好：`attachBaseContext` 里先拿 base context 顶上，之后拿到 Application 再换过来。
     * base context 在 super.attachBaseContext 之前就已经能写应用私有目录了。
     */
    fun init(context: Context) {
        val application = context.applicationContext
        if (application != null) {
            appContext = application
        } else if (appContext == null) {
            appContext = context
        }
    }

    /** 记一个启动阶段。任何异常都不许往外抛：埋点不能把应用搞挂。 */
    fun stage(name: String, detail: String? = null) {
        // 同名只记第一次：这些点有的在组合里，重组一次写一次盘会把界面拖垮
        synchronized(lock) { if (!seen.add(name)) return }
        val line = buildString {
            append(now() - startedAt)
            append("ms [")
            append(Thread.currentThread().name)
            append("] ")
            append(name)
            if (detail != null) {
                append(": ")
                append(detail)
            }
        }
        synchronized(lock) { buffer.appendLine(line) }
        // 埋点不许把业务带崩：单元测试里 android.util.Log 不是真实实现，会抛
        runCatching { Log.i(TAG, line) }
        runCatching { writeInternal() }
        runCatching { writePublic() }
    }

    /** 把栈之类的附加信息挂到记录末尾，再落一次盘。 */
    fun export(extra: String? = null) {
        if (!extra.isNullOrBlank()) {
            synchronized(lock) {
                buffer.appendLine()
                buffer.appendLine("---- 附加信息 ----")
                buffer.appendLine(extra)
            }
        }
        runCatching { writeInternal() }
        runCatching { writePublic() }
    }

    private fun writeInternal() {
        val context = appContext ?: return
        val text = synchronized(lock) { buffer.toString() }
        context.openFileOutput(INTERNAL_NAME, Context.MODE_PRIVATE).use { stream ->
            stream.write(text.toByteArray())
        }
    }

    private fun writePublic() {
        val context = appContext ?: return
        val text = synchronized(lock) { buffer.toString() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = publicUri ?: insertPublic(context)?.also { publicUri = it }
            if (uri != null) {
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: context.contentResolver.openOutputStream(uri)
                stream?.use { it.write(text.toByteArray()) }
                return
            }
        }
        // MediaStore 走不通时还有这一份：应用专属外部目录，部分文件管理器能看到
        val dir = context.getExternalFilesDir(null) ?: return
        File(dir, PUBLIC_NAME).writeText(text)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertPublic(context: Context): Uri? {
        // 同名旧文件先删掉，免得每启动一次就多一个 "(1)"
        runCatching {
            context.contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(PUBLIC_NAME),
            )
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, PUBLIC_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }
}
