package me.rerere.ai.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * 提供商级的请求约束，挂在 [ProviderSetting] 上随提供商一起保存和分享。
 *
 * 这些设置只影响本机发出请求的节奏，不会改变提供商真正的额度；用来避免把有限额的
 * 订阅账号或按量计费的 Key 用到触发风控、限流或超支。
 */
@Serializable
data class ProviderGuard(
    // 每分钟最多发起的请求数，0 表示不限制
    val rpmLimit: Int = 0,
    // 每分钟最多消耗的 token 数（输入按估算、输出按实际用量），0 表示不限制
    val tpmLimit: Int = 0,
    // 自定义滑动时间段限额，可以有多条
    val windows: List<ProviderQuotaWindow> = emptyList(),
    // 定时锁定计划，可以有多条
    val locks: List<ProviderLockSchedule> = emptyList(),
    // 聊天页余额的自动刷新间隔，单位秒；0 表示不自动刷新
    val quotaRefreshSeconds: Int = 120,
    // 用一条 cURL 命令覆写聊天请求
    val requestOverride: RequestOverride = RequestOverride(),
) {
    val hasRateLimit: Boolean
        get() = rpmLimit > 0 || tpmLimit > 0 || windows.any { it.isEnabled }

    /** 需要保留多久的请求流水，用来计算所有时间窗 */
    fun usageRetentionMillis(): Long {
        val longestWindow = windows.maxOfOrNull { it.durationMillis() } ?: 0L
        return maxOf(60_000L, longestWindow)
    }
}

/** 自定义时间段限额的计量单位 */
@Serializable
enum class ProviderQuotaMetric {
    @SerialName("requests")
    REQUESTS,

    @SerialName("tokens")
    TOKENS,

    /** 按模型计费单价估算的费用，货币单位与模型编辑页填写的一致 */
    @SerialName("cost")
    COST,
}

/** 自定义时间段限额的时间单位 */
@Serializable
enum class ProviderQuotaWindowUnit(val millis: Long) {
    @SerialName("minute")
    MINUTE(60_000L),

    @SerialName("hour")
    HOUR(60 * 60_000L),

    @SerialName("day")
    DAY(24 * 60 * 60_000L),

    @SerialName("week")
    WEEK(7 * 24 * 60 * 60_000L),
}

/**
 * 一条自定义时间段限额，例如「每 5 小时最多 40 次请求」或「每天最多 200 万 token」。
 *
 * 按滑动窗口计算：从当前时刻往前推 [amount] 个 [unit]，统计其中的请求次数或 token 用量。
 */
@Serializable
data class ProviderQuotaWindow(
    val id: Uuid = Uuid.random(),
    val amount: Int = 5,
    val unit: ProviderQuotaWindowUnit = ProviderQuotaWindowUnit.HOUR,
    val metric: ProviderQuotaMetric = ProviderQuotaMetric.REQUESTS,
    // REQUESTS / TOKENS 计量时的上限，0 表示这条限额未启用
    val limit: Int = 0,
    // COST 计量时时间窗内允许的花费，0 表示未启用；货币单位与模型计价一致
    val costLimit: Double = 0.0,
) {
    fun durationMillis(): Long = unit.millis * amount.coerceAtLeast(1).toLong()

    val isEnabled: Boolean
        get() = when (metric) {
            ProviderQuotaMetric.COST -> costLimit > 0
            else -> limit > 0
        }
}

/** 锁定计划的重复方式 */
@Serializable
enum class ProviderLockRepeat {
    /** 只在 [ProviderLockSchedule.startDate] 当天触发一次 */
    @SerialName("once")
    ONCE,

    /** 从 [ProviderLockSchedule.startDate] 起每天触发，可再用 repeatUntil 设定截止日期 */
    @SerialName("daily")
    DAILY,

    /** 从 [ProviderLockSchedule.startDate] 起每周在 [ProviderLockSchedule.repeatWeekdays] 列出的星期触发 */
    @SerialName("weekly")
    WEEKLY,
}

/**
 * 一条自动锁定计划：从某个本地日期时刻开始，持续一段时间。
 *
 * 开始日期和时间都用字符串存，格式分别是 `yyyy-MM-dd` 和 `HH:mm`，旧备份缺字段时按默认值处理，
 * 也方便直接阅读配置文件。时长可以跨午夜。
 */
@Serializable
data class ProviderLockSchedule(
    val id: Uuid = Uuid.random(),
    val enabled: Boolean = true,
    // 本地日期 yyyy-MM-dd
    val startDate: String = "",
    // 本地时间 HH:mm
    val startTime: String = "23:00",
    val durationMinutes: Int = 60,
    val repeat: ProviderLockRepeat = ProviderLockRepeat.ONCE,
    // 仅 DAILY / WEEKLY 生效的截止日期 yyyy-MM-dd；空串表示一直重复
    val repeatUntil: String = "",
    // 仅 WEEKLY 生效的星期，ISO-8601：1 = 周一 … 7 = 周日，可多选
    val repeatWeekdays: List<Int> = emptyList(),
    // 旧字段：repeatWeekdays 为空时按单选读取；0 表示沿用 startDate 的星期
    val repeatWeekday: Int = 0,
) {
    fun parsedStart(): LocalDateTime? = runCatching {
        LocalDateTime.of(LocalDate.parse(startDate), LocalTime.parse(startTime))
    }.getOrNull()

    fun parsedRepeatUntil(): LocalDate? =
        repeatUntil.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun summaryDurationMinutes(): Int = durationMinutes.coerceAtLeast(1)

    /** WEEKLY 生效的星期；没有多选记录时回退到旧的单选字段，再缺省沿用开始日期那一天 */
    fun weekdays(startDate: LocalDate): List<DayOfWeek> {
        val days = repeatWeekdays.filter { it in 1..7 }.distinct().sorted()
        if (days.isNotEmpty()) return days.map { DayOfWeek.of(it) }
        val fallback = repeatWeekday.takeIf { it in 1..7 } ?: startDate.dayOfWeek.value
        return listOf(DayOfWeek.of(fallback))
    }
}

/**
 * 锁定状态。
 *
 * [lockedUntil] 不为空表示当前处于锁定中；[nextStart] 是下一次将开始锁定的时刻。
 */
data class ProviderLockState(
    val lockedUntil: Instant? = null,
    val nextStart: Instant? = null,
) {
    val isLocked: Boolean get() = lockedUntil != null
}

/** 当前处于哪一段锁定窗口，返回开始和结束时刻 */
fun ProviderLockSchedule.activeWindowAt(
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<Instant, Instant>? {
    if (!enabled) return null
    val start = parsedStart() ?: return null
    val until = parsedRepeatUntil()
    val nowMillis = now.toEpochMilliseconds()
    val today = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone).toLocalDate()
    val durationMillis = summaryDurationMinutes().toLong() * 60_000L
    // 锁定可能跨过午夜（每周重复的时长甚至可能超过一天），往前多找几天
    val lookbackDays = ((durationMillis + 86_400_000L - 1) / 86_400_000L).toInt()

    for (offset in 0..lookbackDays) {
        val date = today.minusDays(offset.toLong())
        if (!occursOn(date, start, until)) continue
        val startMillis = LocalDateTime.of(date, start.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        if (nowMillis >= startMillis && nowMillis < startMillis + durationMillis) {
            return Instant.fromEpochMilliseconds(startMillis) to
                Instant.fromEpochMilliseconds(startMillis + durationMillis)
        }
    }
    return null
}

/** 下一次开始锁定的时刻；一次性计划已经过去时返回 null */
fun ProviderLockSchedule.nextStartAfter(
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): Instant? {
    if (!enabled) return null
    val start = parsedStart() ?: return null
    val nowMillis = now.toEpochMilliseconds()
    var date = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone).toLocalDate()

    if (repeat == ProviderLockRepeat.ONCE) {
        if (date > start.toLocalDate()) return null
        val candidate = start.atZone(zone).toInstant().toEpochMilli()
        return if (candidate > nowMillis) Instant.fromEpochMilliseconds(candidate) else null
    }

    if (date < start.toLocalDate()) date = start.toLocalDate()
    val until = parsedRepeatUntil()
    var guard = 0
    while (guard++ < 400) {
        if (until != null && date > until) return null
        if (occursOn(date, start, until)) {
            val candidate = date.atTime(start.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
            if (candidate > nowMillis) return Instant.fromEpochMilliseconds(candidate)
        }
        date = date.plusDays(1)
    }
    return null
}

private fun ProviderLockSchedule.occursOn(
    date: LocalDate,
    start: LocalDateTime,
    repeatUntil: LocalDate?,
): Boolean {
    val startDate = start.toLocalDate()
    if (repeat == ProviderLockRepeat.ONCE) return date == startDate
    if (date < startDate) return false
    if (repeatUntil != null && date > repeatUntil) return false
    return when (repeat) {
        ProviderLockRepeat.WEEKLY -> date.dayOfWeek in weekdays(startDate)
        else -> true
    }
}

/** 所有锁定计划合并后的状态：锁定取最晚结束，下一次开始取最早 */
@OptIn(ExperimentalTime::class)
fun ProviderGuard.lockState(
    now: Instant = Clock.System.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): ProviderLockState {
    var lockedUntil: Instant? = null
    var nextStart: Instant? = null
    locks.forEach { schedule ->
        val window = schedule.activeWindowAt(now, zone)
        if (window != null) {
            val end = window.second
            if (lockedUntil == null || end > lockedUntil!!) lockedUntil = end
        } else {
            val start = schedule.nextStartAfter(now, zone)
            if (start != null && (nextStart == null || start < nextStart!!)) nextStart = start
        }
    }
    return ProviderLockState(lockedUntil = lockedUntil, nextStart = nextStart)
}
