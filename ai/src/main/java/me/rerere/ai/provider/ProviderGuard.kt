package me.rerere.ai.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

    /**
     * 计算所有窗口所需的流水起点。首条请求窗口要从设定时刻起读回历史，否则找不到周期起点；
     * 太久以前的部分按 [LONGEST_LOOKBACK_MILLIS] 截断，避免一次读回所有历史。
     */
    fun usageSinceMillis(nowMillis: Long): Long {
        val span = maxOf(60_000L, windows.maxOfOrNull { it.retentionMillis() } ?: 0L)
        val since = windows
            .filter { it.mode == ProviderQuotaWindowMode.FIRST_REQUEST && it.effectiveAt > 0 }
            .minOfOrNull { it.effectiveAt }
            ?.let { minOf(it, nowMillis - span) }
            ?: (nowMillis - span)
        return maxOf(since, nowMillis - LONGEST_LOOKBACK_MILLIS)
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

    /**
     * 一个月：长度按日历计算，[millis] 只是 30 天的估值，
     * 只用在不需要精确边界的场合。
     */
    @SerialName("month")
    MONTH(30 * 24 * 60 * 60_000L),
}

/** 首条请求窗口最多回溯这么久的历史，再久就按截断后的第一条流水起算 */
private const val LONGEST_LOOKBACK_MILLIS = 366L * 24 * 60 * 60 * 1000

/** 没有指定起算日期时的网格原点；周从周一算起 */
private val NATURAL_ANCHOR = LocalDate.of(1970, 1, 1)
private val NATURAL_WEEK_ANCHOR = LocalDate.of(1969, 12, 29)

/** 时间段限额的窗口算法 */
@Serializable
enum class ProviderQuotaWindowMode {
    /** 滑动窗口：从当前时刻往前推一个窗口长度，用量随时间逐条释放 */
    @SerialName("rolling")
    ROLLING,

    /** 固定窗口：按起算时刻对齐，每到一个窗口长度整段重置 */
    @SerialName("fixed")
    FIXED,

    /** 首条请求窗口：设定后第一条请求起算，归零后由下一条请求重新起算 */
    @SerialName("first_request")
    FIRST_REQUEST,
}

/**
 * 当前周期的统计口径。
 *
 * [fromMillis] 为周期起点（用量从这里开始计入），null 表示当前没有进行中的周期；
 * [fromExclusive] 为 true 时起点那一毫秒不算（滑动窗口的旧口径）；
 * [resetAtMillis] 是下一次重置时刻，滑动窗口没有确定的重置点。
 */
data class ProviderQuotaPeriod(
    val fromMillis: Long?,
    val fromExclusive: Boolean,
    val resetAtMillis: Long?,
)

/**
 * 一条自定义时间段限额，例如「每 5 小时最多 40 次请求」或「每天最多 200 万 token」。
 *
 * 窗口有两种算法：
 *
 * - [ProviderQuotaWindowMode.ROLLING]：从当前时刻往前推 [amount] 个 [unit]，用量随时间逐条释放；
 * - [ProviderQuotaWindowMode.FIXED]：以 [anchorDate] [anchorTime] 为起算点，每满一个
 *   [amount] × [unit] 的周期整段重置，像订阅额度那样从某刻起算、到某刻归零；
 * - [ProviderQuotaWindowMode.FIRST_REQUEST]：设定（[effectiveAt]）之后的第一条请求起算，
 *   到周期末尾整段归零；归零后如果没有请求，就没有进行中的周期，下一条请求再重新起算。
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
    // 新增字段放在末尾，旧备份缺字段时按滚动窗口处理，与旧版行为一致
    val mode: ProviderQuotaWindowMode = ProviderQuotaWindowMode.ROLLING,
    // FIXED 的起算日期 yyyy-MM-dd；空串表示按固定网格原点对齐（本地 0 点 / 周一 0 点 / 每月 1 日 0 点）
    val anchorDate: String = "",
    // FIXED 的起算时刻 HH:mm
    val anchorTime: String = "00:00",
    // FIRST_REQUEST 的设定时刻（毫秒）；0 表示没有记录，退化为按最早一条流水起算
    val effectiveAt: Long = 0L,
) {
    /** 窗口长度；MONTH 按 30 天估值，精确边界见 periodEndFrom */
    fun durationMillis(): Long = unit.millis * safeAmount()

    /** 需要保留的流水长度；MONTH 按 32 天留出日历差异的余量 */
    fun retentionMillis(): Long = when (unit) {
        ProviderQuotaWindowUnit.MONTH -> 32L * ProviderQuotaWindowUnit.DAY.millis * safeAmount()
        else -> durationMillis()
    }

    val isEnabled: Boolean
        get() = when (metric) {
            ProviderQuotaMetric.COST -> costLimit > 0
            else -> limit > 0
        }

    /** 旧备份或手改的配置可能给出夸张的数值，夹一下避免毫秒计算溢出 */
    private fun safeAmount(): Long = amount.coerceIn(1, 100_000).toLong()

    /**
     * 固定窗口的网格原点。日期为空时退化为固定的历史日期，而不是「今天」，
     * 否则「每 2 天」这类周期会随日期漂移：天/小时/分钟从 1970-01-01 0 点起算（0 点对齐），
     * 周从 1969-12-29（周一）起算，月从 1970-01 起算。
     */
    private fun fixedAnchorAt(zone: ZoneId): LocalDateTime {
        val explicit = anchorDate.takeIf { it.isNotBlank() }
            ?.let { text -> runCatching { LocalDate.parse(text) }.getOrNull() }
        if (explicit != null) {
            val time = runCatching { LocalTime.parse(anchorTime) }.getOrDefault(LocalTime.MIDNIGHT)
            return LocalDateTime.of(explicit, time)
        }
        val date = if (unit == ProviderQuotaWindowUnit.WEEK) NATURAL_WEEK_ANCHOR else NATURAL_ANCHOR
        return date.atStartOfDay()
    }

    /** 固定窗口里包含 [nowMillis] 的周期 [start, end)；起点可以早于起算日期，网格照常往前延伸 */
    private fun fixedBounds(nowMillis: Long, zone: ZoneId): Pair<Long, Long> {
        val anchor = fixedAnchorAt(zone)
        val count = safeAmount()
        if (unit == ProviderQuotaWindowUnit.MONTH) {
            val nowLocal = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
            var index = ChronoUnit.MONTHS.between(anchor.toLocalDate(), nowLocal.toLocalDate()) / count
            var step = 0
            fun startOf(i: Long): Long =
                anchor.plusMonths(i * count).atZone(zone).toInstant().toEpochMilli()
            while (startOf(index) > nowMillis && step++ < 64) index--
            while (startOf(index + 1) <= nowMillis && step++ < 64) index++
            return startOf(index) to startOf(index + 1)
        }
        val anchorMillis = anchor.atZone(zone).toInstant().toEpochMilli()
        val period = unit.millis * count
        val index = Math.floorDiv(nowMillis - anchorMillis, period)
        val start = anchorMillis + index * period
        return start to start + period
    }

    /** 从一个周期起点算到该周期结束；MONTH 按日历月份算 */
    fun periodEndFrom(startMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        if (unit == ProviderQuotaWindowUnit.MONTH) {
            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(startMillis), zone)
                .plusMonths(safeAmount())
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        } else {
            startMillis + durationMillis()
        }

    /**
     * 首条请求窗口：把 [usageAtMillis]（按时间升序）按周期切段，每段从段内第一条请求起算。
     * 设定（[effectiveAt]）之前的流水不参与；当前没有进行中的段时返回 null，下一条请求再起算。
     */
    private fun firstRequestBounds(
        usageAtMillis: List<Long>,
        nowMillis: Long,
        zone: ZoneId,
    ): Pair<Long, Long>? {
        val from = effectiveAt.takeIf { it > 0 } ?: Long.MIN_VALUE
        var start: Long? = null
        var end = 0L
        for (stamp in usageAtMillis) {
            if (stamp > nowMillis) break
            if (stamp < from) continue
            if (start == null || stamp >= end) {
                start = stamp
                end = periodEndFrom(stamp, zone)
            }
        }
        val current = start ?: return null
        return if (nowMillis < end) current to end else null
    }

    /** 当前周期的统计口径；[usageAtMillis] 是已经发生的请求时刻（升序） */
    fun periodAt(
        usageAtMillis: List<Long>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ProviderQuotaPeriod =
        when (mode) {
            ProviderQuotaWindowMode.ROLLING -> ProviderQuotaPeriod(
                fromMillis = if (unit == ProviderQuotaWindowUnit.MONTH) {
                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
                        .minusMonths(safeAmount())
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                } else {
                    nowMillis - durationMillis()
                },
                fromExclusive = true,
                resetAtMillis = null,
            )

            ProviderQuotaWindowMode.FIXED -> {
                val (start, end) = fixedBounds(nowMillis, zone)
                ProviderQuotaPeriod(fromMillis = start, fromExclusive = false, resetAtMillis = end)
            }

            ProviderQuotaWindowMode.FIRST_REQUEST ->
                firstRequestBounds(usageAtMillis, nowMillis, zone)
                    ?.let { ProviderQuotaPeriod(it.first, false, it.second) }
                    ?: ProviderQuotaPeriod(fromMillis = null, fromExclusive = false, resetAtMillis = null)
        }

    /** 滑动窗口里一条用量滚出窗口的时刻 */
    fun releaseAt(startedAt: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        if (unit == ProviderQuotaWindowUnit.MONTH) {
            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(startedAt), zone)
                .plusMonths(safeAmount())
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        } else {
            startedAt + durationMillis()
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
