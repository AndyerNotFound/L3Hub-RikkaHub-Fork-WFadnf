package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.test.runTest
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelPricing
import me.rerere.ai.provider.ProviderGuard
import me.rerere.ai.provider.ProviderLockSchedule
import me.rerere.ai.provider.ProviderQuotaMetric
import me.rerere.ai.provider.ProviderQuotaWindow
import me.rerere.ai.provider.ProviderQuotaWindowMode
import me.rerere.ai.provider.ProviderQuotaWindowUnit
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.dao.ProviderUsageEvent
import me.rerere.rikkahub.data.db.dao.UsageRecordDAO
import me.rerere.rikkahub.data.db.dao.UsageTotals
import me.rerere.rikkahub.data.db.entity.UsageRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ProviderGateTest {
    private var now = 1_000_000_000L
    private val gate = ProviderGate(dao = EmptyUsageDao(), clock = { now })
    private val model = Model(modelId = "m")
    private val params = TextGenerationParams(model = model)
    private val messages = listOf(UIMessage.user("hello"))

    private fun setting(guard: ProviderGuard) = ProviderSetting.OpenAI(name = "Provider", guard = guard)

    private fun millis(zone: ZoneId, text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private suspend fun begin(setting: ProviderSetting): Result<ProviderGate.RequestTicket> =
        runCatching { gate.begin(setting, messages, params) }

    @Test
    fun `rpm blocks until the oldest request leaves the minute`() = runTest {
        val provider = setting(ProviderGuard(rpmLimit = 1))
        assertTrue(begin(provider).isSuccess)

        val blocked = begin(provider).exceptionOrNull()
        assertTrue(blocked is ProviderLimitException)
        assertTrue(blocked!!.message!!.contains("RPM"))

        now += 60_000L
        assertTrue(begin(provider).isSuccess)
    }

    @Test
    fun `request window blocks until the window slides`() = runTest {
        val provider = setting(
            ProviderGuard(
                windows = listOf(
                    ProviderQuotaWindow(
                        amount = 5,
                        unit = ProviderQuotaWindowUnit.HOUR,
                        metric = ProviderQuotaMetric.REQUESTS,
                        limit = 1,
                    )
                )
            )
        )
        assertTrue(begin(provider).isSuccess)
        assertTrue(begin(provider).exceptionOrNull() is ProviderLimitException)

        now += 5 * 60 * 60 * 1000L
        assertTrue(begin(provider).isSuccess)
    }

    @Test
    fun `token window accounts for the estimated input`() = runTest {
        val provider = setting(
            ProviderGuard(
                windows = listOf(
                    ProviderQuotaWindow(
                        amount = 1,
                        unit = ProviderQuotaWindowUnit.HOUR,
                        metric = ProviderQuotaMetric.TOKENS,
                        limit = 1,
                    )
                )
            )
        )
        // 输入估算已经超过 1 token，第一条请求就会被挡
        assertTrue(begin(provider).exceptionOrNull() is ProviderLimitException)

        val empty = setting(ProviderGuard(tpmLimit = 1))
        assertTrue(runCatching { gate.begin(empty, emptyList(), params) }.isSuccess)
    }

    @Test
    fun `active lock blocks every request`() = runTest {
        val zone = ZoneId.systemDefault()
        now = LocalDateTime.of(2026, 11, 14, 23, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val provider = setting(
            ProviderGuard(
                locks = listOf(
                    ProviderLockSchedule(
                        startDate = "2026-11-14",
                        startTime = "23:00",
                        durationMinutes = 8 * 60,
                    )
                )
            )
        )

        val blocked = begin(provider).exceptionOrNull()
        assertTrue(blocked is ProviderLimitException)
        assertTrue(blocked!!.message!!.contains("locked"))

        now = LocalDateTime.of(2026, 11, 15, 8, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertTrue(begin(provider).isSuccess)
    }

    @Test
    fun `cost window blocks after the budget is spent`() = runTest {
        val provider = setting(
            ProviderGuard(
                windows = listOf(
                    ProviderQuotaWindow(
                        amount = 1,
                        unit = ProviderQuotaWindowUnit.HOUR,
                        metric = ProviderQuotaMetric.COST,
                        costLimit = 1.5,
                    )
                )
            )
        )
        val priced = params.copy(model = Model(modelId = "m", pricing = ModelPricing(perCall = 1.0)))
        val first = gate.begin(provider, messages, priced)
        gate.finish(first, TokenUsage(promptTokens = 1, completionTokens = 1))

        // 已经花了 1.0，下一次预估又要 1.0，超过 1.5 的窗口上限
        assertTrue(
            runCatching { gate.begin(provider, messages, priced) }
                .exceptionOrNull() is ProviderLimitException
        )

        now += 60 * 60 * 1000L
        assertTrue(runCatching { gate.begin(provider, messages, priced) }.isSuccess)
    }

    @Test
    fun `cost window uses actual usage after finish`() = runTest {
        val provider = setting(
            ProviderGuard(
                windows = listOf(
                    ProviderQuotaWindow(
                        amount = 1,
                        unit = ProviderQuotaWindowUnit.HOUR,
                        metric = ProviderQuotaMetric.COST,
                        costLimit = 0.005,
                    )
                )
            )
        )
        // 每 token 1.0：空输入时第一次请求能过，finish 记下 1000 的花费后就被挡住
        val priced = params.copy(model = Model(modelId = "m", pricing = ModelPricing(input = 1_000_000.0)))
        val ticket = gate.begin(provider, emptyList(), priced)
        gate.finish(ticket, TokenUsage(promptTokens = 1_000))

        assertTrue(
            runCatching { gate.begin(provider, emptyList(), priced) }
                .exceptionOrNull() is ProviderLimitException
        )
    }

    @Test
    fun `cost window includes historical usage`() = runTest {
        val pricedModel = Model(
            modelId = "m",
            pricing = ModelPricing(input = 1_000_000.0),
        )
        val provider = ProviderSetting.OpenAI(
            name = "Provider",
            models = listOf(pricedModel),
            guard = ProviderGuard(
                windows = listOf(
                    ProviderQuotaWindow(
                        amount = 1,
                        unit = ProviderQuotaWindowUnit.HOUR,
                        metric = ProviderQuotaMetric.COST,
                        costLimit = 0.5,
                    )
                )
            ),
        )
        // 重启后从 usage_record 恢复：1000 token 按每 token 1.0 记，已经超过 0.5 的上限
        val event = ProviderUsageEvent(
            createdAt = now - 1_000L,
            tokens = 1_000,
            promptTokens = 1_000,
            completionTokens = 0,
            cachedTokens = 0,
            modelId = pricedModel.id.toString(),
        )
        val historicalGate = ProviderGate(dao = EmptyUsageDao(listOf(event)), clock = { now })

        assertTrue(
            runCatching {
                historicalGate.begin(
                    provider,
                    emptyList(),
                    TextGenerationParams(model = pricedModel),
                )
            }.exceptionOrNull() is ProviderLimitException
        )
    }

    @Test
    fun `cost window ignores models without prices`() = runTest {
        val provider = setting(
            ProviderGuard(
                windows = listOf(
                    ProviderQuotaWindow(
                        amount = 1,
                        unit = ProviderQuotaWindowUnit.HOUR,
                        metric = ProviderQuotaMetric.COST,
                        costLimit = 0.01,
                    )
                )
            )
        )
        repeat(3) {
            assertTrue(begin(provider).isSuccess)
            now += 1_000L
        }
    }

    @Test
    fun `fixed window blocks until the period boundary`() = runTest {
        val zone = ZoneId.of("Asia/Shanghai")
        now = millis(zone, "2026-11-14T09:00:00")
        val fixedGate = ProviderGate(dao = EmptyUsageDao(), clock = { now }, zone = zone)
        val provider = setting(
            ProviderGuard(
                windows = listOf(
                    ProviderQuotaWindow(
                        amount = 5,
                        unit = ProviderQuotaWindowUnit.HOUR,
                        metric = ProviderQuotaMetric.REQUESTS,
                        limit = 1,
                        mode = ProviderQuotaWindowMode.FIXED,
                        anchorDate = "2026-11-14",
                        anchorTime = "08:00",
                    )
                )
            )
        )

        assertTrue(runCatching { fixedGate.begin(provider, messages, params) }.isSuccess)

        now = millis(zone, "2026-11-14T09:01:00")
        val blocked = runCatching { fixedGate.begin(provider, messages, params) }.exceptionOrNull()
        assertTrue(blocked is ProviderLimitException)
        val limited = blocked as ProviderLimitException
        // 固定窗口的恢复时间就是周期结束，不是「最早一条滚出去」的时刻
        assertEquals(
            millis(zone, "2026-11-14T13:00:00"),
            limited.retryAt?.toEpochMilliseconds(),
        )

        now = millis(zone, "2026-11-14T12:59:00")
        assertTrue(
            runCatching { fixedGate.begin(provider, messages, params) }
                .exceptionOrNull() is ProviderLimitException
        )

        // 13:00 进入下一个周期，旧用量整段作废
        now = millis(zone, "2026-11-14T13:00:00")
        assertTrue(runCatching { fixedGate.begin(provider, messages, params) }.isSuccess)
        now = millis(zone, "2026-11-14T13:05:00")
        assertTrue(
            runCatching { fixedGate.begin(provider, messages, params) }
                .exceptionOrNull() is ProviderLimitException
        )
        now = millis(zone, "2026-11-14T18:00:00")
        assertTrue(runCatching { fixedGate.begin(provider, messages, params) }.isSuccess)
    }

    @Test
    fun `fixed window drops usage from earlier periods while rolling still counts it`() = runTest {
        val zone = ZoneId.of("Asia/Shanghai")
        now = millis(zone, "2026-11-14T09:00:00")
        val event = ProviderUsageEvent(
            createdAt = millis(zone, "2026-11-13T23:50:00"),
            tokens = 1_000,
            promptTokens = 1_000,
            completionTokens = 0,
            cachedTokens = 0,
            modelId = null,
        )
        val fixed = ProviderQuotaWindow(
            amount = 1,
            unit = ProviderQuotaWindowUnit.DAY,
            metric = ProviderQuotaMetric.REQUESTS,
            limit = 1,
            mode = ProviderQuotaWindowMode.FIXED,
            anchorDate = "2026-11-01",
            anchorTime = "00:00",
        )
        val rolling = fixed.copy(mode = ProviderQuotaWindowMode.ROLLING)
        val nightlyGate = ProviderGate(dao = EmptyUsageDao(listOf(event)), clock = { now }, zone = zone)

        // 昨天的请求不在今天的固定窗口里
        val fixedProvider = setting(ProviderGuard(windows = listOf(fixed)))
        assertTrue(runCatching { nightlyGate.begin(fixedProvider, messages, params) }.isSuccess)

        // 同样的请求落在滚动 24 小时窗口里，仍然计入
        val rollingProvider = setting(ProviderGuard(windows = listOf(rolling)))
        assertTrue(
            runCatching { nightlyGate.begin(rollingProvider, messages, params) }
                .exceptionOrNull() is ProviderLimitException
        )
    }

    @Test
    fun `fixed window status always reports the next reset`() = runTest {
        val zone = ZoneId.of("Asia/Shanghai")
        now = millis(zone, "2026-11-14T09:00:00")
        val fixedGate = ProviderGate(dao = EmptyUsageDao(), clock = { now }, zone = zone)
        val provider = setting(
            ProviderGuard(
                windows = listOf(
                    ProviderQuotaWindow(
                        amount = 1,
                        unit = ProviderQuotaWindowUnit.WEEK,
                        metric = ProviderQuotaMetric.REQUESTS,
                        limit = 10,
                        mode = ProviderQuotaWindowMode.FIXED,
                        anchorDate = "2026-11-09",
                        anchorTime = "00:00",
                    )
                )
            )
        )
        fixedGate.begin(provider, messages, params)

        now = millis(zone, "2026-11-14T09:30:00")
        val usage = fixedGate.status(provider).windows.single()
        assertEquals(1, usage.used)
        assertEquals(millis(zone, "2026-11-16T00:00:00"), usage.resetAt?.toEpochMilliseconds())
    }

    @Test
    fun `first request window starts counting at the first request and restarts after a reset`() = runTest {
        val zone = ZoneId.of("Asia/Shanghai")
        now = millis(zone, "2026-11-14T09:00:00")
        val firstGate = ProviderGate(dao = EmptyUsageDao(), clock = { now }, zone = zone)
        val provider = setting(
            ProviderGuard(
                windows = listOf(
                    ProviderQuotaWindow(
                        amount = 3,
                        unit = ProviderQuotaWindowUnit.HOUR,
                        metric = ProviderQuotaMetric.REQUESTS,
                        limit = 1,
                        mode = ProviderQuotaWindowMode.FIRST_REQUEST,
                        effectiveAt = millis(zone, "2026-11-14T09:00:00"),
                    )
                )
            )
        )

        // 设定之后还没有请求：没有进行中的周期，不会拦
        assertEquals(0, firstGate.status(provider).windows.single().used)
        assertNull(firstGate.status(provider).windows.single().resetAt)

        now = millis(zone, "2026-11-14T10:00:00")
        assertTrue(runCatching { firstGate.begin(provider, messages, params) }.isSuccess)
        val usage = firstGate.status(provider).windows.single()
        assertEquals(1, usage.used)
        assertEquals(millis(zone, "2026-11-14T13:00:00"), usage.resetAt?.toEpochMilliseconds())

        // 周期内的下一个请求被挡，恢复时间就是本周期末尾
        now = millis(zone, "2026-11-14T10:05:00")
        val blocked = runCatching { firstGate.begin(provider, messages, params) }.exceptionOrNull()
        assertTrue(blocked is ProviderLimitException)
        assertEquals(
            millis(zone, "2026-11-14T13:00:00"),
            (blocked as ProviderLimitException).retryAt?.toEpochMilliseconds(),
        )

        // 周期结束后一直没有请求：用量归零，等下一条请求再起算
        now = millis(zone, "2026-11-14T15:00:00")
        val idle = firstGate.status(provider).windows.single()
        assertEquals(0, idle.used)
        assertNull(idle.resetAt)

        now = millis(zone, "2026-11-14T16:00:00")
        assertTrue(runCatching { firstGate.begin(provider, messages, params) }.isSuccess)
        val restarted = firstGate.status(provider).windows.single()
        assertEquals(1, restarted.used)
        assertEquals(millis(zone, "2026-11-14T19:00:00"), restarted.resetAt?.toEpochMilliseconds())
    }

    @Test
    fun `no limits means every request passes`() = runTest {
        val provider = setting(ProviderGuard())
        repeat(5) {
            assertTrue(begin(provider).isSuccess)
            now += 1_000L
        }
    }
}

private class EmptyUsageDao(
    private val events: List<ProviderUsageEvent> = emptyList(),
) : UsageRecordDAO {
    override suspend fun insert(record: UsageRecordEntity) = Unit

    override suspend fun getTotals(since: Long) = UsageTotals()

    override suspend fun getTotalsByModelBucket(format: String, since: Long) = emptyList<me.rerere.rikkahub.data.db.dao.ModelBucketUsage>()

    override suspend fun getTotalsByModel(since: Long) = emptyList<me.rerere.rikkahub.data.db.dao.ModelUsage>()

    override suspend fun getRequestCountPerDay(source: String, since: Long) = emptyList<me.rerere.rikkahub.data.db.dao.DayCount>()

    override suspend fun getTotalsBySourceModel(since: Long) = emptyList<me.rerere.rikkahub.data.db.dao.SourceModelUsage>()

    override suspend fun getUnpricedUsageRows(limit: Int, since: Long, afterId: Long) =
        emptyList<me.rerere.rikkahub.data.db.dao.UnpricedUsageRow>()

    override suspend fun updateCost(id: Long, cost: Double) = Unit

    override suspend fun getProviderUsageEvents(
        providerId: String,
        since: Long,
        before: Long,
    ) = events
}
