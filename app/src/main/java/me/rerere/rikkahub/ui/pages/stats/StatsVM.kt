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
import me.rerere.rikkahub.data.db.dao.PriceRuleUsageRow
import me.rerere.rikkahub.data.db.dao.SourceModelUsage
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
    // 这天的估算费用，单位的币种与模型价目一致
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
    // 已设置价目的模型按当前价目估算出的费用
    val totalCost: Double,
    // 至少有一个用到的模型设置了价目；否则费用一栏没有可信数字
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
            // 用量流水包含标题、翻译等不落到消息里的请求，删掉的会话也还算在内
            val totals = usageRecordDAO.getTotals(since)
            val settings = settingsStore.awaitLoaded()
            // 价目按「每 1M / 每 Mi」token 填写，和设置里的计数口径一起换算
            val unitTokens = priceUnitTokens(settings.displaySetting.binaryUnits)
            val byMonth = range.days == null
            val bucketFormat = if (byMonth) "%Y-%m" else "%Y-%m-%d"
            // 时间型变价规则依赖每条流水自己的时间，聚合后的桶没有这个信息；
            // 只有确实配置了启用的规则时才逐条算一遍
            val hasPriceRules = settings.providers.any { provider ->
                provider.models.any { model -> model.pricing.rules.any { it.enabled } }
            }
            val ruleCosts = if (hasPriceRules) {
                withContext(Dispatchers.Default) {
                    loadRuleCosts(
                        rows = usageRecordDAO.getPriceRuleUsageRows(bucketFormat, since),
                        settings = settings,
                        unitTokens = unitTokens,
                    )
                }
            } else {
                null
            }
            val modelRows = usageRecordDAO.getTotalsByModel(since)
            val models = modelRows.map { usage ->
                ModelUsageItem(
                    name = usage.displayName(settings),
                    totalTokens = usage.totalTokens,
                    cost = ruleCosts?.byModel?.get(usage.costKey())
                        ?: if (ruleCosts == null) usage.costOf(settings, unitTokens) else 0.0,
                )
            }
            val buckets = bucketTotals(
                rows = usageRecordDAO.getTotalsByModelBucket(format = bucketFormat, since = since),
                settings = settings,
                unitTokens = unitTokens,
                ruleCosts = ruleCosts,
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
                        cost = if (ruleCosts == null) {
                            rows.sumOf { it.costOf(settings, unitTokens) }
                        } else {
                            rows.sumOf { ruleCosts.bySourceModel[it.source to it.costKey()] ?: 0.0 }
                        },
                    )
                }
                .sortedByDescending { it.totalTokens }
            _usage.value = UsageStats(
                range = range,
                totals = totals,
                totalCost = models.sumOf { it.cost },
                hasPricing = modelRows.any { it.modelId.pricingOf(settings) != null },
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
}

private val ModelUsage.totalTokens get() = promptTokens + completionTokens
private val SourceModelUsage.totalTokens get() = promptTokens + completionTokens

/** 按模型分桶的原始汇总，加上按当前价目算出的费用 */
private class BucketTotals {
    var promptTokens: Long = 0
    var completionTokens: Long = 0
    var cachedTokens: Long = 0
    var cost: Double = 0.0
}

private fun bucketTotals(
    rows: List<ModelBucketUsage>,
    settings: Settings,
    unitTokens: Double,
    ruleCosts: RuleCostTotals?,
): Map<String, BucketTotals> {
    val totals = mutableMapOf<String, BucketTotals>()
    rows.forEach { row ->
        val bucket = totals.getOrPut(row.bucket) { BucketTotals() }
        bucket.promptTokens += row.promptTokens
        bucket.completionTokens += row.completionTokens
        bucket.cachedTokens += row.cachedTokens
        bucket.cost += if (ruleCosts == null) {
            row.costOf(settings, unitTokens)
        } else {
            ruleCosts.byBucketModel[row.bucket to row.costKey()] ?: 0.0
        }
    }
    return totals
}

/** 按逐条流水套用变价规则后的费用，三个维度各汇总一份 */
private class RuleCostTotals {
    val byModel = mutableMapOf<String, Double>()
    val byBucketModel = mutableMapOf<Pair<String, String>, Double>()
    val bySourceModel = mutableMapOf<Pair<String, String>, Double>()
}

private fun loadRuleCosts(
    rows: List<PriceRuleUsageRow>,
    settings: Settings,
    unitTokens: Double,
): RuleCostTotals {
    val totals = RuleCostTotals()
    rows.forEach { row ->
        val pricing = row.modelId
            ?.toModelUuid()
            ?.let { settings.findModelById(it) }
            ?.pricing
            ?: return@forEach
        val cost = pricing.applyRules(
            PriceRuleContext(
                atMillis = row.createdAt,
                promptTokens = row.promptTokens.toLong(),
                completionTokens = row.completionTokens.toLong(),
                cachedTokens = row.cachedTokens.toLong(),
            )
        ).estimateCost(
            promptTokens = row.promptTokens.toLong(),
            completionTokens = row.completionTokens.toLong(),
            cachedTokens = row.cachedTokens.toLong(),
            unitTokens = unitTokens,
            calls = 1,
        ).total
        if (cost == 0.0) return@forEach
        val key = row.costKey()
        totals.byModel[key] = (totals.byModel[key] ?: 0.0) + cost
        val bucketKey = row.bucket to key
        totals.byBucketModel[bucketKey] = (totals.byBucketModel[bucketKey] ?: 0.0) + cost
        val sourceKey = row.source to key
        totals.bySourceModel[sourceKey] = (totals.bySourceModel[sourceKey] ?: 0.0) + cost
    }
    return totals
}

private fun costKey(modelId: String?, modelName: String?): String =
    modelId?.let { "id:$it" } ?: "name:${modelName.orEmpty()}"

private fun ModelUsage.costKey(): String = costKey(modelId, modelName)

private fun ModelBucketUsage.costKey(): String = costKey(modelId, modelName)

private fun SourceModelUsage.costKey(): String = costKey(modelId, modelName)

private fun PriceRuleUsageRow.costKey(): String = costKey(modelId, modelName)

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

private fun ModelUsage.costOf(settings: Settings, unitTokens: Double): Double =
    costOf(modelId, promptTokens, completionTokens, cachedTokens, calls, settings, unitTokens)

private fun ModelBucketUsage.costOf(settings: Settings, unitTokens: Double): Double =
    costOf(modelId, promptTokens, completionTokens, cachedTokens, calls, settings, unitTokens)

private fun SourceModelUsage.costOf(settings: Settings, unitTokens: Double): Double =
    costOf(modelId, promptTokens, completionTokens, cachedTokens, calls, settings, unitTokens)

private fun costOf(
    modelId: String?,
    promptTokens: Long,
    completionTokens: Long,
    cachedTokens: Long,
    calls: Int,
    settings: Settings,
    unitTokens: Double,
): Double {
    val pricing = modelId.pricingOf(settings) ?: return 0.0
    // usage_record 没有缓存写入字段，写入缓存的 token 会按输入价计入
    return pricing.estimateCost(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        cachedTokens = cachedTokens,
        unitTokens = unitTokens,
        calls = calls,
    ).total
}

private fun String?.pricingOf(settings: Settings): ModelPricing? =
    toModelUuid()?.let { settings.findModelById(it) }?.pricing?.takeIf { it.isConfigured }

private fun String?.toModelUuid(): Uuid? =
    this?.let { runCatching { Uuid.parse(it) }.getOrNull() }
