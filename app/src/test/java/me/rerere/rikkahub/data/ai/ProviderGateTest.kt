package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.test.runTest
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderGuard
import me.rerere.ai.provider.ProviderLockSchedule
import me.rerere.ai.provider.ProviderQuotaMetric
import me.rerere.ai.provider.ProviderQuotaWindow
import me.rerere.ai.provider.ProviderQuotaWindowUnit
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.dao.ProviderUsageEvent
import me.rerere.rikkahub.data.db.dao.UsageRecordDAO
import me.rerere.rikkahub.data.db.dao.UsageTotals
import me.rerere.rikkahub.data.db.entity.UsageRecordEntity
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
    fun `no limits means every request passes`() = runTest {
        val provider = setting(ProviderGuard())
        repeat(5) {
            assertTrue(begin(provider).isSuccess)
            now += 1_000L
        }
    }
}

private class EmptyUsageDao : UsageRecordDAO {
    override suspend fun insert(record: UsageRecordEntity) = Unit

    override suspend fun getTotals(since: Long) = UsageTotals()

    override suspend fun getTotalsByBucket(format: String, since: Long) = emptyList<me.rerere.rikkahub.data.db.dao.UsageBucket>()

    override suspend fun getTotalsByModel(since: Long) = emptyList<me.rerere.rikkahub.data.db.dao.ModelUsage>()

    override suspend fun getRequestCountPerDay(source: String, since: Long) = emptyList<me.rerere.rikkahub.data.db.dao.DayCount>()

    override suspend fun getTotalsBySource(since: Long) = emptyList<me.rerere.rikkahub.data.db.dao.SourceUsage>()

    override suspend fun getProviderUsageEvents(
        providerId: String,
        since: Long,
        before: Long,
    ) = emptyList<ProviderUsageEvent>()
}
