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
 * 启动埋点：把启动走到哪一步、主线程卡在哪一行，落到一个**不用 adb 也能取到**的文件里。
 *
 * 起因是「装完新包卡在启动图标」这种问题在拿不到 logcat 的机器上无法定位：应用起不来，
 * logcat 读不到，崩溃对话框也没有（要么主线程死循环，要么进程被 halt）。
 *
 * 做法：
 * - 启动路径上几个关键点各记一行（Application / Activity / 组合开始 / 第一帧），写进应用自己的
 *   filesDir，同步追加，代价只有几毫秒；
 * - 出事时（主线程卡死、未捕获异常、设置读盘失败）把整份记录捎上栈**导出到手机 Download 目录**，
 *   走 MediaStore，不需要任何存储权限，文件管理器就能看到；
 * - 正常画出第一帧也导出一次，这样「没有文件」本身就是一个结论（说明包根本没跑起来）。
 *
 * 只用在这里，定位完就该删掉。
 */
object StartupTrace {
    private const val TAG = "StartupTrace"
    private const val INTERNAL_NAME = "startup-trace.txt"
    private const val PUBLIC_NAME = "l3hub-startup-trace.txt"

    private val startedAt = SystemClock.uptimeMillis()
    private val lock = Any()
    private val buffer = StringBuilder()
    // 同名阶段只记第一次：这些埋点有的在组合里，重组一次写一次盘会把界面拖垮
    private val seen = HashSet<String>()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var exported = false

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext ?: context
    }

    /** 记一个启动阶段。任何异常都不许往外抛：埋点不能把应用搞挂。 */
    fun stage(name: String, detail: String? = null) {
        synchronized(lock) { if (!seen.add(name)) return }
        val line = buildString {
            append(SystemClock.uptimeMillis() - startedAt)
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
        Log.i(TAG, line)
        runCatching { appendInternal() }
    }

    /** 把整份记录（加上 [extra]）导出到 Download，方便直接用文件管理器取走。只导出一次。 */
    fun export(extra: String? = null) {
        val context = appContext ?: return
        if (exported) return
        exported = true
        val text = buildString {
            synchronized(lock) { append(buffer) }
            if (!extra.isNullOrBlank()) {
                appendLine()
                appendLine("---- 附加信息 ----")
                appendLine(extra)
            }
        }
        Log.e(TAG, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { exportToDownloads(context, text) }
                .onFailure { Log.e(TAG, "exportToDownloads failed", it) }
        }
        // MediaStore 走不通时还有这一份：应用专属外部目录，部分文件管理器能看到
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: return@runCatching
            File(dir, PUBLIC_NAME).writeText(text)
        }.onFailure { Log.e(TAG, "write external files failed", it) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToDownloads(context: Context, text: String) {
        val collection: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        // 同名旧文件先删掉，免得每启动一次就多一个 "(1)"
        runCatching {
            context.contentResolver.delete(
                collection,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(PUBLIC_NAME),
            )
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, PUBLIC_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(collection, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(text.toByteArray())
        }
    }

    private fun appendInternal() {
        val context = appContext ?: return
        val text = synchronized(lock) { buffer.toString() }
        context.openFileOutput(INTERNAL_NAME, Context.MODE_PRIVATE).use { stream ->
            stream.write(text.toByteArray())
        }
    }
}
