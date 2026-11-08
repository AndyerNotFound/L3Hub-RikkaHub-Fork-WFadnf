package me.rerere.rikkahub.data.datastore

import android.content.Context
import androidx.core.content.edit
import me.rerere.common.android.Logging

/**
 * 请求日志的开关和体积上限。
 *
 * 存在独立的 SharedPreferences 里，不进设置备份：日志本身只在内存里活一次进程，备份这两个开关没有意义。
 * 冷启动时由 [me.rerere.rikkahub.RikkaHubApp] 灌进 [Logging]，网络层和日志页只认 Logging 里的内存值，
 * 免得每次写日志都要读一次盘。
 */
class LogPreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var requestLoggingEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, Logging.DEFAULT_REQUEST_LOGGING_ENABLED)
        set(value) = preferences.edit { putBoolean(KEY_ENABLED, value) }

    var maxBytes: Long
        get() = preferences.getLong(KEY_MAX_BYTES, Logging.DEFAULT_MAX_LOG_BYTES)
            .coerceAtLeast(Logging.MIN_MAX_LOG_BYTES)
        set(value) = preferences.edit { putLong(KEY_MAX_BYTES, value.coerceAtLeast(Logging.MIN_MAX_LOG_BYTES)) }

    private companion object {
        const val FILE_NAME = "rikkahub.request_log"
        const val KEY_ENABLED = "enabled"
        const val KEY_MAX_BYTES = "max_bytes"
    }
}
