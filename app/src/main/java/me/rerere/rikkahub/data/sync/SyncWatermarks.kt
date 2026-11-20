package me.rerere.rikkahub.data.sync

import android.content.Context

/**
 * 增量同步的水位：每一端各记「上次成功同步到对方哪个时刻」。
 *
 * 拉取用对方的写入时间做水位（会话的 update_at），推送用自己的写入时间做水位；
 * 用开始同步的那一刻而不是结束，宁可下一次多带一点，也不漏掉同步期间的新写入。
 * 存在独立的 SharedPreferences 里，不随设置导入导出——换设备后第一次同步走全量，本就该如此。
 */
class SyncWatermarks(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun pulledAt(peerPackage: String): Long = preferences.getLong(pullKey(peerPackage), 0L)

    fun pushedAt(peerPackage: String): Long = preferences.getLong(pushKey(peerPackage), 0L)

    fun recordPull(peerPackage: String, startedAt: Long) {
        preferences.edit().putLong(pullKey(peerPackage), startedAt).apply()
    }

    fun recordPush(peerPackage: String, startedAt: Long) {
        preferences.edit().putLong(pushKey(peerPackage), startedAt).apply()
    }

    fun clear(peerPackage: String) {
        preferences.edit()
            .remove(pullKey(peerPackage))
            .remove(pushKey(peerPackage))
            .apply()
    }

    private fun pullKey(peerPackage: String) = "pull.$peerPackage"

    private fun pushKey(peerPackage: String) = "push.$peerPackage"

    private companion object {
        const val FILE_NAME = "version-sync"
    }
}
