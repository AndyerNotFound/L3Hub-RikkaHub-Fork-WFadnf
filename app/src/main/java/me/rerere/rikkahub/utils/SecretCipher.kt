package me.rerere.rikkahub.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore 中不可导出的 AES 密钥加解密少量敏感字符串 (如 SSH 密码、私钥)。
 *
 * 密文和密钥绑定在本机: 卸载重装或换设备恢复备份后无法解密, 调用方需提示用户重新填写。
 */
object SecretCipher {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "rikkahub_secret_cipher"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val VERSION_PREFIX = "v1:"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    @Volatile
    private var cachedKey: SecretKey? = null

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return VERSION_PREFIX + Base64.getEncoder().encodeToString(cipher.iv + ciphertext)
    }

    fun decrypt(value: String): String {
        require(value.startsWith(VERSION_PREFIX)) { "Unsupported secret format" }
        val bytes = Base64.getDecoder().decode(value.removePrefix(VERSION_PREFIX))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
        return cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).toString(Charsets.UTF_8)
    }

    /** 密文能否用本机的密钥解开; 换设备或换包名恢复备份后旧密文解不开 */
    fun canDecrypt(value: String): Boolean = runCatching { decrypt(value) }.isSuccess

    /**
     * Keystore 取密钥是一次 binder 往返, 之前每解一个字段就重取一次 (SSH 工作区一次文件操作要解 1~3 个字段)。
     * 句柄本身不可导出, 缓存它不扩大暴露面; 密钥在系统里被换掉时下一次加解密自然会失败, 与不缓存时一致。
     */
    private fun secretKey(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            return loadOrCreateKey().also { cachedKey = it }
        }
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }
}
