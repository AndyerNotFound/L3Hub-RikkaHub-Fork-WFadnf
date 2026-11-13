package me.rerere.ai.provider

import java.math.BigDecimal

/** 余额文本里的一笔金额，[currency] 已归一化成大写代码，接口只给数字时为空 */
data class BalanceAmount(
    val currency: String?,
    val amount: BigDecimal,
)

/** 余额预警值：数字加可选单位，例如 10、CNY 10、$10、10 USD */
data class BalanceThreshold(
    val amount: BigDecimal,
    val currency: String?,
)

// 金额前面可以带货币代码或符号，后面可以带货币代码；两边挡住单词里的数字
private val AMOUNT_PATTERN = Regex(
    """(?<![A-Za-z])(?:(?<prefix>US\$|[A-Z]{3}|[$€£¥￥])\s*)?""" +
        """(?<amount>[-+]?\d+(?:\.\d+)?)""" +
        """(?:\s*(?<suffix>[A-Z]{3}))?(?![A-Za-z])""",
    RegexOption.IGNORE_CASE,
)

/** 从一段文本里依次取出金额，解析不出的片段跳过 */
fun parseBalanceAmounts(text: String): List<BalanceAmount> =
    AMOUNT_PATTERN.findAll(text).mapNotNull { match ->
        val amount = match.groups["amount"]?.value?.toBigDecimalOrNull() ?: return@mapNotNull null
        val currency = match.groups["prefix"]?.value ?: match.groups["suffix"]?.value
        BalanceAmount(normalizeCurrency(currency), amount)
    }.toList()

/** 解析余额预警值，没有数字时返回 null */
fun parseBalanceThreshold(text: String): BalanceThreshold? {
    val amount = parseBalanceAmounts(text).firstOrNull() ?: return null
    return BalanceThreshold(amount.amount, amount.currency)
}

/**
 * 余额是否已达到预警值。
 *
 * 预警值带单位时只比同币种的余额；余额本身没有币种（接口只给数字）时按同一笔算。
 * 预警值不带单位时比余额里的第一笔，多币种余额以第一笔为主币种。
 */
fun isBalanceLow(balance: String, threshold: String): Boolean {
    val warning = parseBalanceThreshold(threshold) ?: return false
    val amounts = parseBalanceAmounts(balance)
    val target = when {
        warning.currency != null -> amounts.firstOrNull { it.currency == warning.currency }
            ?: amounts.singleOrNull()?.takeIf { it.currency == null }

        else -> amounts.firstOrNull()
    } ?: return false
    return target.amount <= warning.amount
}

private fun normalizeCurrency(currency: String?): String? {
    val code = currency?.uppercase() ?: return null
    return when (code) {
        "¥", "￥", "RMB", "CNY" -> "CNY"
        "\$", "US\$", "USD" -> "USD"
        "€", "EUR" -> "EUR"
        "£", "GBP" -> "GBP"
        else -> code
    }
}
