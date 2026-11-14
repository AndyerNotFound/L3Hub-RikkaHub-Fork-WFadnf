package me.rerere.rikkahub.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.ModelPricing
import me.rerere.ai.provider.PriceRuleContext
import me.rerere.ai.provider.ProviderLockState
import me.rerere.ai.provider.ProviderQuotaMetric
import me.rerere.ai.provider.ProviderQuotaPeriod
import me.rerere.ai.provider.ProviderQuotaWindow
import me.rerere.ai.provider.ProviderQuotaWindowUnit
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TOKENS_PER_MILLION
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.UsageCost
import me.rerere.ai.provider.estimateCost
import me.rerere.ai.provider.lockState
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.dao.ProviderUsageEvent
import me.rerere.rikkahub.data.db.dao.UsageRecordDAO
import me.rerere.rikkahub.data.model.estimateMessageTokens
import me.rerere.rikkahub.utils.formatCost
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Instant

// 费用是浮点数，比较释放量时留一点余量
private const val COST_EPSILON = 1e-9

/** 触发本机限额或定时锁定时抛出，界面直接展示 [message]（已带恢复时间） */
class ProviderLimitException(
    message: String,
    val retryAt: Instant? = null,
) : IllegalStateException(message)

/** 限额错误的文案来源；Android 之外（单测）用 [PlainProviderGateMessages] */
interface ProviderGateMessages {
    fun locked(providerName: String, until: Instant): String
    fun rpm(providerName: String, limit: Int, retryAt: Instant): String
    fun tpm(providerName: String, limit: Int, estimatedTokens: Int, retryAt: Instant): String
    fun windowRequests(providerName: String, window: ProviderQuotaWindow, retryAt: Instant): String
    fun windowTokens(providerName: String, window: ProviderQuotaWindow, retryAt: Instant): String
    fun windowCost(providerName: String, window: ProviderQuotaWindow, retryAt: Instant): String
    fun windowLabel(window: ProviderQuotaWindow): String
    fun time(instant: Instant): String
}

/** 不依赖 Android 资源的英文兜底，单测和异常日志使用 */
object PlainProviderGateMessages : ProviderGateMessages {
    override fun locked(providerName: String, until: Instant): String =
        "$providerName is locked until ${time(until)}"

    override fun rpm(providerName: String, limit: Int, retryAt: Instant): String =
        "$providerName reached the RPM limit of $limit, retry after ${time(retryAt)}"

    override fun tpm(providerName: String, limit: Int, estimatedTokens: Int, retryAt: Instant): String =
        "$providerName reached the TPM limit of $limit, this request needs about $estimatedTokens tokens, retry after ${time(retryAt)}"

    override fun windowRequests(providerName: String, window: ProviderQuotaWindow, retryAt: Instant): String =
        "$providerName reached ${windowLabel(window)} request limit of ${window.limit}, retry after ${time(retryAt)}"

    override fun windowTokens(providerName: String, window: ProviderQuotaWindow, retryAt: Instant): String =
        "$providerName reached ${windowLabel(window)} token limit of ${window.limit}, retry after ${time(retryAt)}"

    override fun windowCost(providerName: String, window: ProviderQuotaWindow, retryAt: Instant): String =
        "$providerName reached ${windowLabel(window)} cost limit of ${window.costLimit}, retry after ${time(retryAt)}"

    override fun windowLabel(window: ProviderQuotaWindow): String =
        "per ${window.amount.coerceAtLeast(1)} ${window.unit.name.lowercase()}"

    override fun time(instant: Instant): String =
        instant.toString()
}

/** 用应用资源拼文案；界面和聊天错误提示都以中文/当前语言显示 */
class AndroidProviderGateMessages(private val context: Context) : ProviderGateMessages {
    override fun locked(providerName: String, until: Instant): String =
        context.getString(R.string.provider_limit_locked, providerName, time(until))

    override fun rpm(providerName: String, limit: Int, retryAt: Instant): String =
        context.getString(R.string.provider_limit_rpm, providerName, limit, time(retryAt))

    override fun tpm(providerName: String, limit: Int, estimatedTokens: Int, retryAt: Instant): String =
        context.getString(R.string.provider_limit_tpm, providerName, limit, estimatedTokens, time(retryAt))

    override fun windowRequests(providerName: String, window: ProviderQuotaWindow, retryAt: Instant): String =
        context.getString(
            R.string.provider_limit_window_requests,
            providerName,
            windowLabel(window),
            window.limit,
            time(retryAt),
        )

    override fun windowTokens(providerName: String, window: ProviderQuotaWindow, retryAt: Instant): String =
        context.getString(
            R.string.provider_limit_window_tokens,
            providerName,
            windowLabel(window),
            window.limit,
            time(retryAt),
        )

    override fun windowCost(providerName: String, window: ProviderQuotaWindow, retryAt: Instant): String =
        context.getString(
            R.string.provider_limit_window_cost,
            providerName,
            windowLabel(window),
            window.costLimit.formatCost(),
            time(retryAt),
        )

    override fun windowLabel(window: ProviderQuotaWindow): String = window.label(context)

    override fun time(instant: Instant): String = formatGuardTime(context, instant)
}

data class ProviderWindowUsage(
    val window: ProviderQuotaWindow,
    val used: Int,
    // COST 计量时已经花掉的费用，其余计量方式为 0
    val costUsed: Double = 0.0,
    val resetAt: Instant?,
)

/** 高级设置里展示的实时状态 */
data class ProviderGateStatus(
    val lockedUntil: Instant?,
    val nextLockAt: Instant?,
    val rpmUsed: Int,
    val tpmUsed: Int,
    val windows: List<ProviderWindowUsage>,
)

/**
 * 在每次模型请求前后检查提供商自己的 RPM / TPM / 时间段限额和定时锁定。
 *
 * 用量分两部分：进程启动前的历史从 usage_record 读回，进程启动后的请求全部记在内存里。
 * 这样长窗口（按周、按月）在重启后仍然有效，短窗口也不必每次查库。请求开始时就按估算的
 * 输入 token 占位，结束后换成真实用量，避免并发请求一起冲破 TPM。
 */
class ProviderGate(
    private val dao: UsageRecordDAO,
    private val texts: ProviderGateMessages = PlainProviderGateMessages,
    private val clock: () -> Long = { System.currentTimeMillis() },
    // 计费用的 token 单价口径（1M 或 Mi），跟设置里的「以二进制量计数」保持一致
    private val unitTokens: () -> Double = { TOKENS_PER_MILLION },
    // 时间窗按本地时区对齐；单测固定时区，避免依赖运行机器的时区
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val processStart = clock()
    private val ledgerLock = Any()
    private val hydrationMutex = Mutex()
    private val stamps = mutableMapOf<String, MutableList<RequestStamp>>()
    // 已经从那之前的历史都读过了；null 表示这个提供商还没读过
    private val hydratedFrom = mutableMapOf<String, Long>()

    internal data class RequestStamp(
        val startedAt: Long,
        var tokens: Int,
        var cost: Double = 0.0,
    )

    class RequestTicket internal constructor(
        internal val providerId: String,
        internal val stamp: RequestStamp,
        internal val pricing: ModelPricing,
        internal val unitTokens: Double,
    )

    suspend fun begin(
        setting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): RequestTicket {
        val now = clock()
        val guard = setting.guard
        val estimatedTokens = messages.sumOf { estimateMessageTokens(it) }
        val priceUnit = unitTokens()
        val estimatedCost = usageCostOf(
            pricing = params.model.pricing,
            usage = TokenUsage(promptTokens = estimatedTokens),
            unitTokens = priceUnit,
            atMillis = now,
        ).total
        if (guard.hasRateLimit) {
            ensureHydrated(setting, guard.usageSinceMillis(now))
        }
        synchronized(ledgerLock) {
            val providerId = setting.id.toString()
            val list = stamps.getOrPut(providerId) { mutableListOf() }
            prune(list, guard.usageSinceMillis(now))

            val lock = guard.lockState(Instant.fromEpochMilliseconds(now))
            lock.lockedUntil?.let { until ->
                throw ProviderLimitException(
                    texts.locked(setting.name, until),
                    retryAt = until,
                )
            }

            checkRpm(list, now, guard.rpmLimit, setting.name)
            checkTpm(list, now, guard.tpmLimit, estimatedTokens, setting.name)
            guard.windows.forEach { window ->
                checkWindow(list, now, window, estimatedTokens, estimatedCost, setting.name)
            }

            val stamp = RequestStamp(
                startedAt = now,
                tokens = estimatedTokens,
                cost = estimatedCost,
            )
            list += stamp
            return RequestTicket(providerId, stamp, params.model.pricing, priceUnit)
        }
    }

    /**
     * 请求结束，返回这次请求按当时价目和规则算出的费用，调用方据此写入 usage_record。
     * 费用在这里用完整的 usage 算一次（含缓存写入），之后所有汇总只读这份快照。
     */
    fun finish(ticket: RequestTicket, usage: TokenUsage?): UsageCost? {
        val actual = usage?.let { it.promptTokens + it.completionTokens } ?: 0
        val actualCost = usage?.let {
            usageCostOf(
                pricing = ticket.pricing,
                usage = it,
                unitTokens = ticket.unitTokens,
                atMillis = ticket.stamp.startedAt,
            )
        }
        synchronized(ledgerLock) {
            if (actual > 0) ticket.stamp.tokens = actual
            ticket.stamp.cost = actualCost?.total ?: 0.0
        }
        return actualCost
    }

    suspend fun status(setting: ProviderSetting): ProviderGateStatus {
        val now = clock()
        val guard = setting.guard
        ensureHydrated(setting, guard.usageSinceMillis(now))
        synchronized(ledgerLock) {
            val list = stamps.getOrPut(setting.id.toString()) { mutableListOf() }
            prune(list, guard.usageSinceMillis(now))
            val lock = guard.lockState(Instant.fromEpochMilliseconds(now))
            val minuteEvents = list.filter { now - it.startedAt < 60_000L }
            return ProviderGateStatus(
                lockedUntil = lock.lockedUntil,
                nextLockAt = lock.nextStart,
                rpmUsed = minuteEvents.size,
                tpmUsed = minuteEvents.sumOf { it.tokens },
                windows = guard.windows.map { window ->
                    val period = window.periodAt(list.map { it.startedAt }, now, zone)
                    val events = list.within(period)
                    val used = when (window.metric) {
                        ProviderQuotaMetric.REQUESTS -> events.size
                        ProviderQuotaMetric.TOKENS -> events.sumOf { it.tokens }
                        ProviderQuotaMetric.COST -> 0
                    }
                    val costUsed = if (window.metric == ProviderQuotaMetric.COST) {
                        events.sumOf { it.cost }
                    } else {
                        0.0
                    }
                    val full = when (window.metric) {
                        ProviderQuotaMetric.REQUESTS,
                        ProviderQuotaMetric.TOKENS -> used >= window.limit
                        ProviderQuotaMetric.COST -> costUsed >= window.costLimit
                    }
                    val periodResetAt = period.resetAtMillis
                        ?.let { Instant.fromEpochMilliseconds(it) }
                    ProviderWindowUsage(
                        window = window,
                        used = used,
                        costUsed = costUsed,
                        // 固定与首条请求窗口直接给出下一次重置；滑动窗口只在满额时算恢复时间
                        resetAt = when {
                            !window.isEnabled -> null
                            periodResetAt != null -> periodResetAt
                            full -> windowResetAt(events, window)
                            else -> null
                        },
                    )
                },
            )
        }
    }

    fun lockState(setting: ProviderSetting, now: Instant = Clock.System.now()): ProviderLockState =
        setting.guard.lockState(now)

    private fun checkRpm(events: List<RequestStamp>, now: Long, limit: Int, providerName: String) {
        if (limit <= 0) return
        val recent = events.filter { now - it.startedAt < 60_000L }
        if (recent.size < limit) return
        val retryAt = Instant.fromEpochMilliseconds(recent.first().startedAt + 60_000L)
        throw ProviderLimitException(
            texts.rpm(providerName, limit, retryAt),
            retryAt = retryAt,
        )
    }

    private fun checkTpm(
        events: List<RequestStamp>,
        now: Long,
        limit: Int,
        estimatedTokens: Int,
        providerName: String,
    ) {
        if (limit <= 0) return
        val recent = events.filter { now - it.startedAt < 60_000L }
        val used = recent.sumOf { it.tokens }
        if (used + estimatedTokens <= limit) return
        val retryAt = resolveRetryAt(recent, used + estimatedTokens - limit, now, tokens = true) {
            it + 60_000L
        }
        throw ProviderLimitException(
            texts.tpm(providerName, limit, estimatedTokens, retryAt),
            retryAt = retryAt,
        )
    }

    private fun checkWindow(
        events: List<RequestStamp>,
        now: Long,
        window: ProviderQuotaWindow,
        estimatedTokens: Int,
        estimatedCost: Double,
        providerName: String,
    ) {
        if (!window.isEnabled) return
        val period = window.periodAt(events.map { it.startedAt }, now, zone)
        val recent = events.within(period)
        // 固定与首条请求窗口的重置点就是周期末尾，滑动窗口才需要算用量释放到何时
        val resetAt = period.resetAtMillis?.let { Instant.fromEpochMilliseconds(it) }
        when (window.metric) {
            ProviderQuotaMetric.REQUESTS -> {
                val used = recent.size
                val needed = used - window.limit + 1
                if (needed <= 0) return
                val retryAt = resetAt ?: resolveRetryAt(recent, needed, now, tokens = false) {
                    window.releaseAt(it, zone)
                }
                throw ProviderLimitException(
                    texts.windowRequests(providerName, window, retryAt),
                    retryAt = retryAt,
                )
            }

            ProviderQuotaMetric.TOKENS -> {
                val used = recent.sumOf { it.tokens }
                val needed = used + estimatedTokens - window.limit
                if (needed <= 0) return
                val retryAt = resetAt ?: resolveRetryAt(recent, needed, now, tokens = true) {
                    window.releaseAt(it, zone)
                }
                throw ProviderLimitException(
                    texts.windowTokens(providerName, window, retryAt),
                    retryAt = retryAt,
                )
            }

            ProviderQuotaMetric.COST -> {
                val used = recent.sumOf { it.cost }
                val needed = used + estimatedCost - window.costLimit
                if (needed <= 0) return
                val retryAt = resetAt ?: resolveRetryAtCost(recent, needed, window, now)
                throw ProviderLimitException(
                    texts.windowCost(providerName, window, retryAt),
                    retryAt = retryAt,
                )
            }
        }
    }

    /** 周期起点为空表示当前没有进行中的周期，一条用量都不计入 */
    private fun List<RequestStamp>.within(period: ProviderQuotaPeriod): List<RequestStamp> {
        val from = period.fromMillis ?: return emptyList()
        return if (period.fromExclusive) {
            filter { it.startedAt > from }
        } else {
            filter { it.startedAt >= from }
        }
    }

    /** 还要释放多少用量才能通过，以及最早什么时候能释放够；[releaseAt] 给出某条用量滚出窗口的时刻 */
    private fun resolveRetryAt(
        events: List<RequestStamp>,
        needed: Int,
        now: Long,
        tokens: Boolean,
        releaseAt: (Long) -> Long,
    ): Instant {
        var freed = 0
        for (event in events) {
            freed += if (tokens) event.tokens else 1
            if (freed >= needed) {
                return Instant.fromEpochMilliseconds(releaseAt(event.startedAt))
            }
        }
        return Instant.fromEpochMilliseconds(releaseAt(now))
    }

    /** 费用口径的释放时间：从最早的一条开始累加，直到释放够需要的花费 */
    private fun resolveRetryAtCost(
        events: List<RequestStamp>,
        needed: Double,
        window: ProviderQuotaWindow,
        now: Long,
    ): Instant {
        var freed = 0.0
        for (event in events) {
            freed += event.cost
            if (freed + COST_EPSILON >= needed) {
                return Instant.fromEpochMilliseconds(window.releaseAt(event.startedAt, zone))
            }
        }
        return Instant.fromEpochMilliseconds(window.releaseAt(now, zone))
    }

    private fun windowResetAt(
        events: List<RequestStamp>,
        window: ProviderQuotaWindow,
    ): Instant? {
        return when (window.metric) {
            ProviderQuotaMetric.REQUESTS -> {
                val needed = events.size - window.limit + 1
                if (needed <= 0) return null
                var freed = 0
                for (event in events) {
                    freed += 1
                    if (freed >= needed) {
                        return Instant.fromEpochMilliseconds(window.releaseAt(event.startedAt, zone))
                    }
                }
                null
            }

            ProviderQuotaMetric.TOKENS -> {
                val needed = events.sumOf { it.tokens } - window.limit + 1
                if (needed <= 0) return null
                var freed = 0
                for (event in events) {
                    freed += event.tokens
                    if (freed >= needed) {
                        return Instant.fromEpochMilliseconds(window.releaseAt(event.startedAt, zone))
                    }
                }
                null
            }

            ProviderQuotaMetric.COST -> {
                val needed = events.sumOf { it.cost } - window.costLimit
                if (needed <= 0) return null
                var freed = 0.0
                for (event in events) {
                    freed += event.cost
                    if (freed + COST_EPSILON >= needed) {
                        return Instant.fromEpochMilliseconds(window.releaseAt(event.startedAt, zone))
                    }
                }
                null
            }
        }
    }

    private fun prune(events: MutableList<RequestStamp>, since: Long) {
        events.removeAll { it.startedAt < since }
    }

    private suspend fun ensureHydrated(setting: ProviderSetting, since: Long) {
        val providerId = setting.id.toString()
        val to = synchronized(ledgerLock) {
            val covered = hydratedFrom[providerId]
            when {
                covered == null -> processStart
                since < covered -> covered
                else -> null
            }
        } ?: return
        val from = minOf(since, processStart)
        if (from >= to) {
            synchronized(ledgerLock) { hydratedFrom[providerId] = minOf(from, processStart) }
            return
        }
        hydrationMutex.withLock {
            // 等锁期间可能已经被另一个请求补过同一段
            val recheck = synchronized(ledgerLock) {
                val covered = hydratedFrom[providerId]
                when {
                    covered == null -> from to processStart
                    from < covered -> from to covered
                    else -> null
                }
            } ?: return
            val events: List<ProviderUsageEvent> = withContext(Dispatchers.IO) {
                dao.getProviderUsageEvents(providerId, recheck.first, recheck.second)
            }
            synchronized(ledgerLock) {
                val list = stamps.getOrPut(providerId) { mutableListOf() }
                val priceUnit = unitTokens()
                events.forEach { event ->
                    list += RequestStamp(
                        startedAt = event.createdAt,
                        tokens = event.tokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        cost = eventCost(event, setting, priceUnit),
                    )
                }
                list.sortBy { it.startedAt }
                hydratedFrom[providerId] = minOf(recheck.first, processStart)
            }
        }
    }

    /**
     * 新流水直接用入账时的费用快照，重启前后不会变；
     * 只有账本上线前的旧流水没有 cost，才按当前价目和当时的时间回退估算。
     */
    private fun eventCost(event: ProviderUsageEvent, setting: ProviderSetting, unitTokens: Double): Double {
        event.cost?.let { return it }
        val model = event.modelId
            ?.let { id -> setting.models.firstOrNull { model -> model.id.toString() == id } }
            ?: return 0.0
        return usageCostOf(
            pricing = model.pricing,
            usage = TokenUsage(
                promptTokens = event.promptTokens,
                completionTokens = event.completionTokens,
                cachedTokens = event.cachedTokens,
            ),
            unitTokens = unitTokens,
            atMillis = event.createdAt,
        ).total
    }

    private fun usageCostOf(
        pricing: ModelPricing,
        usage: TokenUsage,
        unitTokens: Double,
        atMillis: Long,
    ): UsageCost = if (!pricing.isConfigured) {
        UsageCost()
    } else {
        pricing.estimateCost(
            usage = usage,
            unitTokens = unitTokens,
            calls = 1,
            context = PriceRuleContext(
                atMillis = atMillis,
                promptTokens = usage.promptTokens.toLong(),
                completionTokens = usage.completionTokens.toLong(),
                cachedTokens = usage.cachedTokens.toLong(),
            ),
        )
    }
}

/** 窗口名称，例如「每 5 小时」 */
fun ProviderQuotaWindow.label(context: Context): String {
    val count = amount.coerceAtLeast(1)
    val unit = context.resources.getQuantityString(
        when (unit) {
            ProviderQuotaWindowUnit.MINUTE -> R.plurals.provider_guard_duration_minutes
            ProviderQuotaWindowUnit.HOUR -> R.plurals.provider_guard_duration_hours
            ProviderQuotaWindowUnit.DAY -> R.plurals.provider_guard_duration_days
            ProviderQuotaWindowUnit.WEEK -> R.plurals.provider_guard_duration_weeks
            ProviderQuotaWindowUnit.MONTH -> R.plurals.provider_guard_duration_months
        },
        count,
        count,
    )
    return context.getString(R.string.provider_guard_window_every, unit)
}

/** 时长文案，例如 8 小时、90 分钟 */
fun formatGuardDuration(context: Context, minutes: Int): String {
    val safe = minutes.coerceAtLeast(1)
    return when {
        safe % (7 * 24 * 60) == 0 -> context.resources.getQuantityString(
            R.plurals.provider_guard_duration_weeks, safe / (7 * 24 * 60), safe / (7 * 24 * 60)
        )
        safe % (24 * 60) == 0 -> context.resources.getQuantityString(
            R.plurals.provider_guard_duration_days, safe / (24 * 60), safe / (24 * 60)
        )
        safe % 60 == 0 -> context.resources.getQuantityString(
            R.plurals.provider_guard_duration_hours, safe / 60, safe / 60
        )
        else -> context.resources.getQuantityString(
            R.plurals.provider_guard_duration_minutes, safe, safe
        )
    }
}

/** 恢复时间：当天只显示时刻，第二天加「明天」，再远显示日期 */
fun formatGuardTime(context: Context, instant: Instant): String {
    val zone = ZoneId.systemDefault()
    val local = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()), zone)
    val today = LocalDate.now(zone)
    val time = local.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (local.toLocalDate()) {
        today -> context.getString(R.string.provider_guard_time_today, time)
        today.plusDays(1) -> context.getString(R.string.provider_guard_time_tomorrow, time)
        else -> local.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }
}
