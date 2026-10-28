package me.rerere.rikkahub.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * 应用内语言选择。
 *
 * 选择存在 SharedPreferences 而不是 DataStore：Activity / Application 的 attachBaseContext
 * 在 Koin 与 DataStore 就绪之前执行，必须同步读到。空字符串表示跟随系统。
 */
object AppLanguage {
    private const val PREFS = "app_language"
    private const val KEY_TAG = "tag"

    /** 与 res/values-* 目录一一对应 */
    val supportedTags = listOf("en", "zh-CN", "zh-TW", "ja", "ko", "ru", "de", "ar")

    fun currentTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, "").orEmpty()

    fun setTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, tag)
            .apply()
    }

    /** 按当前选择包一层带 locale 的 Context；跟随系统时原样返回 */
    fun wrap(context: Context): Context {
        val tag = currentTag(context)
        if (tag.isBlank()) return context
        val locale = Locale.forLanguageTag(tag)
        if (locale.language.isBlank()) return context
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(configuration)
    }

    /** 语言自称，不用翻译 */
    fun displayName(tag: String): String = when (tag) {
        "en" -> "English"
        "zh-CN" -> "简体中文"
        "zh-TW" -> "繁體中文"
        "ja" -> "日本語"
        "ko" -> "한국어"
        "ru" -> "Русский"
        "de" -> "Deutsch"
        "ar" -> "العربية"
        else -> tag
    }
}
