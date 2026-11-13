package me.rerere.rikkahub.utils

import org.apache.commons.text.StringEscapeUtils
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun String.urlEncode(): String {
    return URLEncoder.encode(this, "UTF-8")
}

fun String.urlDecode(): String {
    return URLDecoder.decode(this, "UTF-8")
}

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Encode(): String {
    return Base64.encode(this.toByteArray())
}

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Decode(): String {
    return String(Base64.decode(this))
}

fun String.escapeHtml(): String {
    return StringEscapeUtils.escapeHtml4(this)
}

fun String.unescapeHtml(): String {
    return StringEscapeUtils.unescapeHtml4(this)
}

fun Number.toFixed(digits: Int = 0) = "%.${digits}f".format(this)

fun String.applyPlaceholders(
    vararg placeholders: Pair<String, String>,
): String {
    var result = this
    for ((placeholder, replacement) in placeholders) {
        result = result.replace("{$placeholder}", replacement)
    }
    return result
}

fun Long.fileSizeToString(): String {
    if (this < 1024) return "$this B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val precision = when {
        value >= 100 -> 0
        value >= 10 -> 1
        else -> 2
    }
    return "%.${precision}f %s".format(value, units[unitIndex])
}

/**
 * 把数量换算到紧凑量级：十进制按 1000 进位（K/M/B/T），二进制按 1024 进位（Ki/Mi/Gi/Ti）。
 *
 * 不足最小量级时返回 null。按一位小数判断进位，1048575 不会显示成 1024.0Ki。
 */
fun compactMagnitude(count: Long, binary: Boolean): Pair<Double, String>? {
    val step = if (binary) 1024.0 else 1000.0
    val units = if (binary) BINARY_UNITS else DECIMAL_UNITS
    val absValue = count.coerceAtLeast(0)
    if (absValue < step) return null

    var value = absValue / step
    var unitIndex = 0
    while (value >= step && unitIndex < units.lastIndex) {
        value /= step
        unitIndex++
    }
    // 四舍五入到一位小数后碰到下一级时提前进位，避免 999.9K 这种越界读数
    if (unitIndex < units.lastIndex && kotlin.math.round(value * 10) / 10 >= step) {
        value /= step
        unitIndex++
    }
    return value to units[unitIndex]
}

private val DECIMAL_UNITS = listOf("K", "M", "B", "T")
private val BINARY_UNITS = listOf("Ki", "Mi", "Gi", "Ti")

/**
 * 紧凑的数量显示。
 *
 * [binary] 为 false 时按十进制换算（K/M/B，1000 进制），为 true 时按二进制换算（Ki/Mi/Gi，1024 进制）。
 * 整数值不带小数，其余保留一位。
 */
fun Int.formatNumber(binary: Boolean): String = toLong().formatNumber(binary)

fun Long.formatNumber(binary: Boolean): String {
    val absValue = kotlin.math.abs(this)
    // abs(Long.MIN_VALUE) 仍是负数，退回原值
    if (absValue < 0) return this.toString()
    val sign = if (this < 0) "-" else ""
    val (value, unit) = compactMagnitude(absValue, binary) ?: return this.toString()
    val number = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toFixed(1)
    }
    return "$sign$number$unit"
}

/** 费用保留足够的小数位，小额请求也能看出差别 */
fun Double.formatCost(): String {
    if (this <= 0) return "0"
    val scale = when {
        this >= 1 -> 2
        this >= 0.01 -> 4
        this >= 0.0001 -> 6
        else -> 8
    }
    return java.math.BigDecimal.valueOf(this)
        .setScale(scale, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

fun Float.toFixed(digits: Int = 0) = "%.${digits}f".format(this)
fun Double.toFixed(digits: Int = 0) = "%.${digits}f".format(this)

/**
 * 提取字符串中所有引号内的内容
 * 支持多种引号类型：英文双引号 "..."、英文单引号 '...'、中文双引号 "..."、中文单引号 '...'、直角引号「…」、白直角引号『…』
 * @return 所有引号内内容的列表
 */
fun String.extractQuotedContent(): List<String> {
    val result = mutableListOf<String>()
    // 匹配多种引号类型
    val patterns = listOf(
        "\u201C([^\u201D]*?)\u201D",  // 中文双引号
        "\u2018([^\u2019]*?)\u2019",  // 中文单引号
        """"([^"]*?)"""",  // 英文双引号
        """'([^']*?)'""",  // 英文单引号
        """「([^」]*?)」""",           // 直角引号
        """『([^』]*?)』""",           // 白直角引号
    )
    for (pattern in patterns) {
        val regex = Regex(pattern)
        regex.findAll(this).forEach { matchResult ->
            val content = matchResult.groupValues[1]
            if (content.isNotBlank()) {
                result.add(content)
            }
        }
    }
    return result
}

/**
 * 提取字符串中所有引号内的内容并合并为一个字符串
 * @param separator 分隔符，默认为换行
 * @return 合并后的字符串，如果没有引号内容则返回 null
 */
fun String.extractQuotedContentAsText(separator: String = "\n"): String? {
    val contents = extractQuotedContent()
    return if (contents.isNotEmpty()) {
        contents.joinToString(separator)
    } else {
        null
    }
}

/**
 * 移除字符串中所有括号内的内容
 * 支持英文括号 (...) 和中文括号（...）
 * @return 移除括号内容后的字符串，如果全被移除则返回 null
 */
fun String.removeBracketedContent(): String? {
    val pattern = """\([^)]*?\)|（[^）]*?）""".toRegex()
    val result = pattern.replace(this, "").trim()
    return result.ifBlank { null }
}
