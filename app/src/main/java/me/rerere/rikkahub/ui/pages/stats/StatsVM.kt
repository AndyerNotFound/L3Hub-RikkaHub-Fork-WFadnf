package me.rerere.rikkahub.ui.pages.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ModelPricing
import me.rerere.ai.provider.PriceRuleContext
import me.rerere.ai.provider.estimateCost
import me.rerere.ai.provider.priceUnitTokens
import me.rerere.rikkahub.data.ai.UsageSource
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.MessageNodeDAO
import me.rerere.rikkahub.data.db.dao.ModelBucketUsage
import me.rerere.rikkahub.data.db.dao.ModelUsage
import me.rerere.rikkahub.data.db.dao.SourceModelUsage
import me.rerere.rikkahub.data.db.dao.UnpricedUsageRow
import me.rerere.rikkahub.data.db.dao.UsageRecordDAO
import me.rerere.rikkahub.data.db.dao.UsageTotals
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.uuid.Uuid

data class AppStats(
    val isLoading: Boolean = true,
    val totalConversations: Int = 0,
    val totalMessages: Int = 0,
    // 热力图：每天的对话请求次数
    val requestsPerDay: Map<LocalDate, Int> = emptyMap(),
    val launchCount: Int = 0,
)

/** 用量统计的时间范围；[days] 为空表示全部，按月分桶 */
enum class UsageRange(val days: Int?) {
    WEEK(7),
    MONTH(30),
    QUARTER(90),
    ALL(null),
}

data class UsagePoint(
    // 按天时是当天，按月时是当月 1 号
    val date: LocalDate,
    val promptTokens: Long,
    val completionTokens: Long,
    val cachedTokens: Long,
    // 这天入账的费用，币种与模型价目一致
    val cost: Double = 0.0,
) {
    val totalTokens get() = promptTokens + completionTokens
}

data class ModelUsageItem(
    // 模型已被删除且没有名称快照时为空
    val name: String?,
    val totalTokens: Long,
    val cost: Double = 0.0,
)

data class SourceUsageItem(
    val source: String,
    val totalTokens: Long,
    val cost: Double = 0.0,
)

data class UsageStats(
    val range: UsageRange,
    val totals: UsageTotals,
    // 本期已入账的费用总和
    val totalCost: Double,
    // 本期是否有可看的费用：有入账费用，或用到的模型配置了价目
    val hasPricing: Boolean,
    val points: List<UsagePoint>,
    val byMonth: Boolean,
    val models: List<ModelUsageItem>,
    val sources: List<SourceUsageItem>,
)

class StatsVM(
    private val conversationDAO: ConversationDAO,
    private val messageNodeDAO: MessageNodeDAO,
    private val usageRecordDAO: UsageRecordDAO,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _stats = MutableStateFlow(AppStats())
    val stats = _stats.asStateFlow()

    // 切换范围时先保留旧数据，查完再替换，避免页面闪成加载状态
    private val _usage = MutableStateFlow<UsageStats?>(null)
    val usage = _usage.asStateFlow()

    private val _range = MutableStateFlow(UsageRange.MONTH)
    val range = _range.asStateFlow()

    private var usageJob: Job? = null

    init {
        viewModelScope.launch { loadStats() }
        loadUsage(_range.value)
    }

    fun setRange(range: UsageRange) {
        if (range == _range.value) return
        _range.value = range
        loadUsage(range)
    }

    private suspend fun loadStats() {
        delay(50)

        val today = LocalDate.now()

        // 热力图起始日期（52 周前的周日）
        val startDate = today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .minusWeeks(52)
        val since = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 按对话请求数统计活跃度，走 usage_record 的 created_at 索引，不用解析消息 JSON；
        // 多步工具调用和重试会多算几次，热力图按四分位分档，只看相对活跃程度
        val requestsPerDay = usageRecordDAO.getRequestCountPerDay(UsageSource.CHAT.name, since)
            .mapNotNull { entry ->
                runCatching { LocalDate.parse(entry.day) to entry.count }.getOrNull()
            }
            .toMap()

        val totalConversations = conversationDAO.countAll()
        val totalMessages = messageNodeDAO.countAll()
        val launchCount = settingsStore.launchCountFlow.first()

        _stats.value = AppStats(
            isLoading = false,
            totalConversations = totalConversations,
            totalMessages = totalMessages,
            requestsPerDay = requestsPerDay,
            launchCount = launchCount,
        )
    }

    private fun loadUsage(range: UsageRange) {
        usageJob?.cancel()
        usageJob = viewModelScope.launch {
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val startDate = range.days?.let { today.minusDays(it - 1L) }
            val since = startDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L
            val settings = settingsStore.awaitLoaded()
            // 价目按「每 1M / 每 Mi」token 填写，和设置里的计数口径一起换算
            val unitTokens = priceUnitTokens(settings.displaySetting.binaryUnits)
            // 账本上线前的流水没有费用快照，先按当前价目补一次；之后统计只读 cost 列
            backfillUsageCosts(settings, unitTokens, since)
            // 用量流水包含标题、翻译等不落到消息里的请求，删掉的会话也还算在内
            val totals = usageRecordDAO.getTotals(since)
            val modelRows = usageRecordDAO.getTotalsByModel(since)
            val models = modelRows.map { usage ->
                ModelUsageItem(
                    name = usage.displayName(settings),
                    totalTokens = usage.totalTokens,
                    cost = usage.cost,
                )
            }
            val byMonth = range.days == null
            val buckets = bucketTotals(
                usageRecordDAO.getTotalsByModelBucket(
                    format = if (byMonth) "%Y-%m" else "%Y-%m-%d",
                    since = since,
                )
            )
            val points = if (byMonth) {
                monthlyPoints(buckets, today)
            } else {
                dailyPoints(buckets, startDate!!, today)
            }
            val sources = usageRecordDAO.getTotalsBySourceModel(since)
                .groupBy { it.source }
                .map { (source, rows) ->
                    SourceUsageItem(
                        source = source,
                        totalTokens = rows.sumOf { it.totalTokens },
                        cost = rows.sumOf { it.cost },
                    )
                }
                .sortedByDescending { it.totalTokens }
            _usage.value = UsageStats(
                range = range,
                totals = totals,
                totalCost = totals.cost,
                hasPricing = totals.cost > 0 || modelRows.any { it.modelId.pricingOf(settings) != null },
                points = points,
                byMonth = byMonth,
                models = models,
                sources = sources,
            )
        }
    }

    // 没有用量的日子补 0，柱子才能按日期均匀排开
    private fun dailyPoints(
        buckets: Map<String, BucketTotals>,
        start: LocalDate,
        today: LocalDate,
    ): List<UsagePoint> = generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { date -> buckets[date.toString()].toPoint(date) }
        .toList()

    // 全部范围按月，从最早有用量的月份排到本月
    private fun monthlyPoints(buckets: Map<String, BucketTotals>, today: LocalDate): List<UsagePoint> {
        val byMonth = buckets.mapNotNull { (bucket, totals) ->
            runCatching { YearMonth.parse(bucket) to totals }.getOrNull()
        }.toMap()
        val first = byMonth.keys.minOrNull() ?: return emptyList()
        val current = YearMonth.from(today)
        return generateSequence(first) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(current) }
            .map { month -> byMonth[month].toPoint(month.atDay(1)) }
            .toList()
    }

    /**
     * 旧流水没有费用快照：按当前价目和规则补算一次，之后价目/规则的改动只影响新请求。
     * 处理过的行会写回 cost，下一次统计不再重复计算。
     */
    private suspend fun backfillUsageCosts(settings: Settings, unitTokens: Double, since: Long) {
        // 一个价目都没填时先不补算，免得把历史写成 0；等用户填了价目再处理
        val anyPriced = settings.providers.any { provider ->
            provider.models.any { it.pricing.isConfigured }
        }
        if (!anyPriced) return
        withContext(Dispatchers.Default) {
            costBackfillMutex.withLock {
                val pricingById = settings.providers
                    .flatMap { it.models }
                    .associate { it.id to it.pricing }
                var afterId = 0L
                while (true) {
                    val rows = usageRecordDAO.getUnpricedUsageRows(COST_BACKFILL_BATCH, since, afterId)
                    if (rows.isEmpty()) break
                    rows.forEach { row ->
                        // 模型没价目时保持 NULL，等用户填了价目再补；不要把历史提前写成 0
                        row.costOf(pricingById, unitTokens)?.let { cost ->
                            usageRecordDAO.updateCost(row.id, cost)
                        }
                    }
                    afterId = rows.last().id
                }
            }
        }
    }

    private companion object {
        const val COST_BACKFILL_BATCH = 500
        val costBackfillMutex = Mutex()
    }
}

private val ModelUsage.totalTokens get() = promptTokens + completionTokens
private val SourceModelUsage.totalTokens get() = promptTokens + completionTokens

/** 按模型分桶的原始汇总，费用直接取流水里的快照 */
private class BucketTotals {
    var promptTokens: Long = 0
    var completionTokens: Long = 0
    var cachedTokens: Long = 0
    var cost: Double = 0.0
}

private fun bucketTotals(rows: List<ModelBucketUsage>): Map<String, BucketTotals> {
    val totals = mutableMapOf<String, BucketTotals>()
    rows.forEach { row ->
        val bucket = totals.getOrPut(row.bucket) { BucketTotals() }
        bucket.promptTokens += row.promptTokens
        bucket.completionTokens += row.completionTokens
        bucket.cachedTokens += row.cachedTokens
        bucket.cost += row.cost
    }
    return totals
}

private fun BucketTotals?.toPoint(date: LocalDate) = UsagePoint(
    date = date,
    promptTokens = this?.promptTokens ?: 0,
    completionTokens = this?.completionTokens ?: 0,
    cachedTokens = this?.cachedTokens ?: 0,
    cost = this?.cost ?: 0.0,
)

private fun ModelUsage.displayName(settings: Settings): String? {
    val model = modelId.toModelUuid()?.let { settings.findModelById(it) }
    return model?.displayName?.ifBlank { null } ?: model?.modelId ?: modelName
}

private fun UnpricedUsageRow.costOf(pricingById: Map<Uuid, ModelPricing>, unitTokens: Double): Double? {
    val pricing = modelId.toModelUuid()
        ?.let { pricingById[it] }
        ?.takeIf { it.isConfigured }
        ?: return null
    return pricing.estimateCost(
        promptTokens = promptTokens.toLong(),
        completionTokens = completionTokens.toLong(),
        cachedTokens = cachedTokens.toLong(),
        unitTokens = unitTokens,
        calls = 1,
        context = PriceRuleContext(
            atMillis = createdAt,
            promptTokens = promptTokens.toLong(),
            completionTokens = completionTokens.toLong(),
            cachedTokens = cachedTokens.toLong(),
        ),
    ).total
}

private fun String?.pricingOf(settings: Settings): ModelPricing? =
    toModelUuid()?.let { settings.findModelById(it) }?.pricing?.takeIf { it.isConfigured }

private fun String?.toModelUuid(): Uuid? =
    this?.let { runCatching { Uuid.parse(it) }.getOrNull() }
