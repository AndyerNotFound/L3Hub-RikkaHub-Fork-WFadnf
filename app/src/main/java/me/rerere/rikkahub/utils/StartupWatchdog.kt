package me.rerere.rikkahub.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 启动自查：进程起来以后盯住主线程，第一帧画出来之前主线程要是长时间不动，
 * 就把主线程当前的栈写进 [CrashHandler]，然后结束进程。
 *
 * 下一次启动 `RouteActivity` 看到「上次崩过」会进 SafeMode，那段栈会直接显示在屏幕上，
 * 也能一键复制——拿不到 logcat 的机器上，这是唯一能把「卡在启动图标」变成一行具体代码的办法。
 *
 * 只在第一帧之前工作：装完应用的首次 dexopt、数据库迁移这类「界面已经画出来、只是还在忙」
 * 的情况不会被算成卡死；超过 [MAX_WATCH_MS] 无论有没有画帧都自行收工。
 */
object StartupWatchdog {
    private const val TAG = "StartupWatchdog"

    /** 主线程多久没处理过消息算卡死。首帧前重启一遍组合不该到两位数秒。 */
    private const val STALL_THRESHOLD_MS = 12_000L

    /** 只盯启动这一段，超时就收工，别在之后的长任务上误报。 */
    private const val MAX_WATCH_MS = 60_000L

    /** 心跳间隔：主线程每处理一次消息就刷新时间戳。 */
    private const val HEARTBEAT_MS = 200L

    private val running = AtomicBoolean(false)

    @Volatile
    private var lastBeat = 0L

    @Volatile
    private var startedAt = 0L

    /** 在 `Application.attachBaseContext` 里最早调用，越早越能盖住启动路径。 */
    fun start(context: Context) {
        if (!running.compareAndSet(false, true)) return
        val mainLooper = Looper.getMainLooper()
        if (mainLooper == null) {
            running.set(false)
            return
        }
        val appContext = context.applicationContext ?: context
        startedAt = SystemClock.uptimeMillis()
        lastBeat = startedAt

        val handler = Handler(mainLooper)
        val beat = object : Runnable {
            override fun run() {
                lastBeat = SystemClock.uptimeMillis()
                if (running.get()) handler.postDelayed(this, HEARTBEAT_MS)
            }
        }
        handler.post(beat)

        Thread({
            while (running.get()) {
                Thread.sleep(1_000L)
                val now = SystemClock.uptimeMillis()
                if (now - startedAt > MAX_WATCH_MS) break
                val stalled = now - lastBeat
                if (stalled < STALL_THRESHOLD_MS) continue
                val stack = buildString {
                    appendLine("启动卡死：主线程 ${stalled}ms 没有处理过任何消息")
                    appendLine("（首帧尚未画出；下面是主线程此刻的调用栈）")
                    mainLooper.thread.stackTrace.forEach { appendLine("    at $it") }
                }
                Log.e(TAG, stack)
                CrashHandler.markStartupStall(appContext, stack)
                Runtime.getRuntime().halt(1)
            }
            running.set(false)
        }, "startup-watchdog").apply { isDaemon = true }.start()
    }

    /** 第一帧画出来就收工，之后的长任务一律不管。 */
    fun disarm() {
        running.set(false)
    }

    /** 在宿主 Activity 的 window 上挂一次性监听：第一次绘制前撤掉自查。 */
    fun disarmOnFirstDraw(root: View) {
        val observer = root.viewTreeObserver
        if (!observer.isAlive) {
            disarm()
            return
        }
        observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (observer.isAlive) observer.removeOnPreDrawListener(this)
                disarm()
                return true
            }
        })
    }
}
