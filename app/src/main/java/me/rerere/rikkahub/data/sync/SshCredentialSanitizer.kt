package me.rerere.rikkahub.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.model.SshWorkspaceSettings
import me.rerere.rikkahub.data.model.dropUndecryptableSecrets
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SecretCipher

/**
 * 恢复备份后清掉解不开的 SSH 凭据。
 *
 * 凭据密文由 Android Keystore 里不可导出的密钥加密, 换设备、重装或换包名 (如从原版恢复到这个 fork)
 * 之后都解不开。留着这些密文, 界面会显示"已保存, 留空保持不变", 保存和连接却都报解密失败;
 * 直接清掉, 界面就会如实显示"未填写", 用户重新输入一次即可。
 */
object SshCredentialSanitizer {
    private const val QUERY = "SELECT id, ssh_config FROM workspaces WHERE ssh_config IS NOT NULL"
    private const val UPDATE = "UPDATE workspaces SET ssh_config = ? WHERE id = ?"

    fun dropUnreadable(db: SupportSQLiteDatabase) {
        val updates = buildList {
            db.query(QUERY).use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow("id")
                val configIndex = cursor.getColumnIndexOrThrow("ssh_config")
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(configIndex) ?: continue
                    val settings = runCatching {
                        JsonInstant.decodeFromString<SshWorkspaceSettings>(raw)
                    }.getOrNull() ?: continue
                    val cleaned = settings.dropUndecryptableSecrets(SecretCipher::canDecrypt)
                    if (cleaned != settings) {
                        add(cursor.getString(idIndex) to JsonInstant.encodeToString(cleaned))
                    }
                }
            }
        }
        updates.forEach { (id, config) -> db.execSQL(UPDATE, arrayOf(config, id)) }
    }
}
