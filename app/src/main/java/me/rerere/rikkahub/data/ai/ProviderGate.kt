package me.rerere.rikkahub.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.ProviderLockState
import me.rerere.ai.provider.ProviderQuotaMetric
import me.rerere.ai.provider.ProviderQuotaWindow
import me.rerere.ai.provider.ProviderQuotaWindowUnit
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.lockState
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.dao.ProviderUsageEvent
import me.rerere.rikkahub.data.db.dao.UsageRecordDAO
import me.rerere.rikkahub.data.model.estimateMessageTokens
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Instant

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

    override fun windowLabel(window: ProviderQuotaWindow): String = window.label(context)

    override fun time(instant: Instant): String = formatGuardTime(context, instant)
}

data class ProviderWindowUsage(
    val window: ProviderQuotaWindow,
    val used: Int,
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
    )

    class RequestTicket internal constructor(
        internal val providerId: String,
        internal val stamp: RequestStamp,
    )

    suspend fun begin(
        setting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): RequestTicket {
        val now = clock()
        val guard = setting.guard
        val estimatedTokens = messages.sumOf { estimateMessageTokens(it) }
        if (guard.hasRateLimit) {
            ensureHydrated(setting.id.toString(), now - guard.usageRetentionMillis())
        }
        synchronized(ledgerLock) {
            val providerId = setting.id.toString()
            val list = stamps.getOrPut(providerId) { mutableListOf() }
            prune(list, now - guard.usageRetentionMillis())

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
                checkWindow(list, now, window, estimatedTokens, setting.name)
            }

            val stamp = RequestStamp(startedAt = now, tokens = estimatedTokens)
            list += stamp
            return RequestTicket(providerId, stamp)
        }
    }

    fun finish(ticket: RequestTicket, usage: TokenUsage?) {
        val actual = usage?.let { it.promptTokens + it.completionTokens } ?: 0
        if (actual <= 0) return
        synchronized(ledgerLock) {
            ticket.stamp.tokens = actual
        }
    }

    suspend fun status(setting: ProviderSetting): ProviderGateStatus {
        val now = clock()
        val guard = setting.guard
        ensureHydrated(setting.id.toString(), now - guard.usageRetentionMillis())
        synchronized(ledgerLock) {
            val list = stamps.getOrPut(setting.id.toString()) { mutableListOf() }
            prune(list, now - guard.usageRetentionMillis())
            val lock = guard.lockState(Instant.fromEpochMilliseconds(now))
            val minuteEvents = list.filter { now - it.startedAt < 60_000L }
            return ProviderGateStatus(
                lockedUntil = lock.lockedUntil,
                nextLockAt = lock.nextStart,
                rpmUsed = minuteEvents.size,
                tpmUsed = minuteEvents.sumOf { it.tokens },
                windows = guard.windows.map { window ->
                    val events = list.filter { now - it.startedAt < window.durationMillis() }
                    val used = when (window.metric) {
                        ProviderQuotaMetric.REQUESTS -> events.size
                        ProviderQuotaMetric.TOKENS -> events.sumOf { it.tokens }
                    }
                    ProviderWindowUsage(
                        window = window,
                        used = used,
                        resetAt = if (window.limit > 0 && used >= window.limit) {
                            windowResetAt(events, window)
                        } else null,
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
        val retryAt = resolveRetryAt(recent, used + estimatedTokens - limit, 60_000L, now, tokens = true)
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
        providerName: String,
    ) {
        if (window.limit <= 0) return
        val duration = window.durationMillis()
        val recent = events.filter { now - it.startedAt < duration }
        val used = when (window.metric) {
            ProviderQuotaMetric.REQUESTS -> recent.size
            ProviderQuotaMetric.TOKENS -> recent.sumOf { it.tokens }
        }
        val needed = when (window.metric) {
            ProviderQuotaMetric.REQUESTS -> used - window.limit + 1
            ProviderQuotaMetric.TOKENS -> used + estimatedTokens - window.limit
        }
        if (needed <= 0) return
        val retryAt = resolveRetryAt(recent, needed, duration, now, tokens = window.metric == ProviderQuotaMetric.TOKENS)
        throw ProviderLimitException(
            when (window.metric) {
                ProviderQuotaMetric.REQUESTS -> texts.windowRequests(providerName, window, retryAt)
                ProviderQuotaMetric.TOKENS -> texts.windowTokens(providerName, window, retryAt)
            },
            retryAt = retryAt,
        )
    }

    /** 还要释放多少用量才能通过，以及最早什么时候能释放够 */
    private fun resolveRetryAt(
        events: List<RequestStamp>,
        needed: Int,
        durationMillis: Long,
        now: Long,
        tokens: Boolean,
    ): Instant {
        var freed = 0
        for (event in events) {
            freed += if (tokens) event.tokens else 1
            if (freed >= needed) {
                return Instant.fromEpochMilliseconds(event.startedAt + durationMillis)
            }
        }
        return Instant.fromEpochMilliseconds(now + durationMillis)
    }

    private fun windowResetAt(
        events: List<RequestStamp>,
        window: ProviderQuotaWindow,
    ): Instant? {
        val needed = when (window.metric) {
            ProviderQuotaMetric.REQUESTS -> events.size - window.limit + 1
            ProviderQuotaMetric.TOKENS -> events.sumOf { it.tokens } - window.limit + 1
        }
        if (needed <= 0) return null
        var freed = 0
        for (event in events) {
            freed += when (window.metric) {
                ProviderQuotaMetric.REQUESTS -> 1
                ProviderQuotaMetric.TOKENS -> event.tokens
            }
            if (freed >= needed) {
                return Instant.fromEpochMilliseconds(event.startedAt + window.durationMillis())
            }
        }
        return null
    }

    private fun prune(events: MutableList<RequestStamp>, since: Long) {
        events.removeAll { it.startedAt < since }
    }

    private suspend fun ensureHydrated(providerId: String, since: Long) {
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
                events.forEach { event ->
                    list += RequestStamp(startedAt = event.createdAt, tokens = event.tokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                }
                list.sortBy { it.startedAt }
                hydratedFrom[providerId] = minOf(recheck.first, processStart)
            }
        }
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
