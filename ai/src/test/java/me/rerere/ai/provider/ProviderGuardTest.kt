package me.rerere.ai.provider

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Instant

class ProviderGuardTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(text: String): Instant {
        val local = LocalDateTime.parse(text)
        return Instant.fromEpochMilliseconds(local.atZone(zone).toInstant().toEpochMilli())
    }

    private fun schedule(
        startDate: String = "2026-11-14",
        startTime: String = "23:00",
        minutes: Int = 8 * 60,
        repeat: ProviderLockRepeat = ProviderLockRepeat.ONCE,
        until: String = "",
        weekday: Int = 0,
    ) = ProviderLockSchedule(
        startDate = startDate,
        startTime = startTime,
        durationMinutes = minutes,
        repeat = repeat,
        repeatUntil = until,
        repeatWeekday = weekday,
    )

    @Test
    fun `one-off lock is active only in its own window`() {
        val lock = schedule()
        val window = lock.activeWindowAt(at("2026-11-14T23:30:00"), zone)
        assertNotNull(window)
        assertEquals(at("2026-11-14T23:00:00"), window!!.first)
        assertEquals(at("2026-11-15T07:00:00"), window.second)

        assertNull(lock.activeWindowAt(at("2026-11-14T22:59:00"), zone))
        assertNull(lock.activeWindowAt(at("2026-11-15T07:00:00"), zone))
        assertNull(lock.activeWindowAt(at("2026-11-15T23:30:00"), zone))
    }

    @Test
    fun `daily lock repeats across midnight and stops at repeatUntil`() {
        val lock = schedule(repeat = ProviderLockRepeat.DAILY, until = "2026-11-16")

        assertNotNull(lock.activeWindowAt(at("2026-11-15T00:30:00"), zone))
        assertNotNull(lock.activeWindowAt(at("2026-11-16T23:30:00"), zone))
        // 11-16 这一天的窗口跨到 11-17 07:00，结束之后才失效
        assertNotNull(lock.activeWindowAt(at("2026-11-17T00:30:00"), zone))
        assertNull(lock.activeWindowAt(at("2026-11-17T08:00:00"), zone))
        assertNull(lock.activeWindowAt(at("2026-11-14T22:30:00"), zone))
    }

    @Test
    fun `weekly lock only fires on the selected weekday`() {
        // 2026-11-14 是周六，计划从周六开始，每周一 23:00 锁定 8 小时
        val lock = schedule(repeat = ProviderLockRepeat.WEEKLY, weekday = DayOfWeek.MONDAY.value)

        assertNull(lock.activeWindowAt(at("2026-11-15T23:30:00"), zone))
        assertNull(lock.activeWindowAt(at("2026-11-16T22:59:00"), zone))
        assertEquals(
            at("2026-11-16T23:00:00") to at("2026-11-17T07:00:00"),
            lock.activeWindowAt(at("2026-11-17T01:00:00"), zone),
        )
        assertNotNull(lock.activeWindowAt(at("2026-11-23T23:30:00"), zone))
        assertEquals(at("2026-11-23T23:00:00"), lock.nextStartAfter(at("2026-11-17T08:00:00"), zone))
    }

    @Test
    fun `weekly lock starts from start date and honours repeatUntil`() {
        val lock = schedule(
            startDate = "2026-11-16",
            repeat = ProviderLockRepeat.WEEKLY,
            until = "2026-11-23",
            weekday = DayOfWeek.MONDAY.value,
        )

        assertNull(lock.activeWindowAt(at("2026-11-09T23:30:00"), zone))
        assertNotNull(lock.activeWindowAt(at("2026-11-16T23:30:00"), zone))
        assertNotNull(lock.activeWindowAt(at("2026-11-23T23:30:00"), zone))
        assertNull(lock.activeWindowAt(at("2026-11-30T23:30:00"), zone))
        assertNull(lock.nextStartAfter(at("2026-11-24T08:00:00"), zone))
    }

    @Test
    fun `weekly lock without an explicit weekday follows the start date`() {
        val lock = schedule(startDate = "2026-11-14", repeat = ProviderLockRepeat.WEEKLY)

        assertEquals(listOf(DayOfWeek.SATURDAY), lock.weekdays(LocalDate.parse("2026-11-14")))
        assertNotNull(lock.activeWindowAt(at("2026-11-21T23:30:00"), zone))
        assertNull(lock.activeWindowAt(at("2026-11-22T23:30:00"), zone))
    }

    @Test
    fun `weekly lock accepts several weekdays and keeps midnight windows on the start day`() {
        val lock = ProviderLockSchedule(
            startDate = "2026-11-14",
            startTime = "23:00",
            durationMinutes = 8 * 60,
            repeat = ProviderLockRepeat.WEEKLY,
            repeatWeekdays = listOf(DayOfWeek.MONDAY.value, DayOfWeek.WEDNESDAY.value),
        )

        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            lock.weekdays(LocalDate.parse("2026-11-14")),
        )
        // 周一 23:00 开始的窗口延续到周二凌晨
        assertNotNull(lock.activeWindowAt(at("2026-11-17T02:00:00"), zone))
        // 周二 23:00 不是所选星期
        assertNull(lock.activeWindowAt(at("2026-11-18T00:30:00"), zone))
        // 周三 23:00 的窗口延续到周四凌晨
        assertNotNull(lock.activeWindowAt(at("2026-11-19T02:00:00"), zone))
        assertEquals(at("2026-11-18T23:00:00"), lock.nextStartAfter(at("2026-11-17T08:00:00"), zone))
    }

    @Test
    fun `next start is reported before the schedule begins`() {
        val lock = schedule(repeat = ProviderLockRepeat.DAILY)
        assertEquals(at("2026-11-14T23:00:00"), lock.nextStartAfter(at("2026-11-14T12:00:00"), zone))
        assertEquals(at("2026-11-15T23:00:00"), lock.nextStartAfter(at("2026-11-15T07:00:00"), zone))
        assertNull(schedule().nextStartAfter(at("2026-11-15T12:00:00"), zone))
    }

    @Test
    fun `guard combines multiple lock schedules`() {
        val guard = ProviderGuard(
            locks = listOf(
                schedule(startDate = "2026-11-14", startTime = "23:00"),
                schedule(startDate = "2026-11-14", startTime = "23:00", minutes = 9 * 60),
            )
        )

        val state = guard.lockState(at("2026-11-15T01:00:00"), zone)
        assertTrue(state.isLocked)
        assertEquals(at("2026-11-15T08:00:00"), state.lockedUntil)
    }

    @Test
    fun `guard survives serialization and direct data class copies`() {
        val guard = ProviderGuard(
            rpmLimit = 10,
            tpmLimit = 20_000,
            windows = listOf(ProviderQuotaWindow(amount = 5, limit = 40, costLimit = 12.5)),
            quotaRefreshSeconds = 30,
            requestOverride = RequestOverride(enabled = true, curl = "curl https://example.com"),
        )
        val setting = ProviderSetting.OpenAI(name = "Test", guard = guard)
        val json = Json.encodeToString(ProviderSetting.serializer(), setting)
        val decoded = Json.decodeFromString(ProviderSetting.serializer(), json)

        assertEquals(10, decoded.guard.rpmLimit)
        assertEquals(20_000, decoded.guard.tpmLimit)
        assertEquals(30, decoded.guard.quotaRefreshSeconds)
        assertEquals("curl https://example.com", decoded.guard.requestOverride.curl)
        assertEquals(5, decoded.guard.windows.single().amount)
        assertEquals(40, decoded.guard.windows.single().limit)
        assertEquals(12.5, decoded.guard.windows.single().costLimit, 0.0)

        // KeyFailoverProvider 用 copy(apiKey = ...) 换 Key，不能把限流配置丢掉
        val withKey = setting.copy(apiKey = "sk-next")
        assertEquals(10, withKey.guard.rpmLimit)

        // 设置页统一走 copyProvider 更新提供商
        val renamed = setting.copyProvider(name = "Renamed")
        assertEquals(10, renamed.guard.rpmLimit)
        assertEquals("Renamed", renamed.name)
    }

    @Test
    fun `cost windows are enabled by their own limit`() {
        val cost = ProviderQuotaWindow(metric = ProviderQuotaMetric.COST, costLimit = 5.0)
        assertTrue(cost.isEnabled)
        assertFalse(cost.copy(costLimit = 0.0).isEnabled)

        val tokens = ProviderQuotaWindow(metric = ProviderQuotaMetric.TOKENS, limit = 100)
        assertTrue(tokens.isEnabled)
        assertFalse(tokens.copy(limit = 0).isEnabled)
    }

    @Test
    fun `usage retention covers the longest window`() {
        val guard = ProviderGuard(
            windows = listOf(
                ProviderQuotaWindow(amount = 5, unit = ProviderQuotaWindowUnit.HOUR, limit = 1),
                ProviderQuotaWindow(amount = 2, unit = ProviderQuotaWindowUnit.DAY, limit = 1),
            ),
            locks = listOf(schedule(minutes = 480)),
        )
        assertEquals(2 * 24 * 60 * 60 * 1000L, guard.usageRetentionMillis())
    }

    private fun window(
        amount: Int = 1,
        unit: ProviderQuotaWindowUnit = ProviderQuotaWindowUnit.DAY,
        mode: ProviderQuotaWindowMode = ProviderQuotaWindowMode.FIXED,
        anchorDate: String = "",
        anchorTime: String = "00:00",
        limit: Int = 1,
        metric: ProviderQuotaMetric = ProviderQuotaMetric.REQUESTS,
    ) = ProviderQuotaWindow(
        amount = amount,
        unit = unit,
        metric = metric,
        limit = limit,
        mode = mode,
        anchorDate = anchorDate,
        anchorTime = anchorTime,
    )

    private fun ProviderQuotaWindow.startAt(text: String): Instant =
        Instant.fromEpochMilliseconds(windowStartAt(at(text).toEpochMilliseconds(), zone))

    private fun ProviderQuotaWindow.resetAt(text: String) =
        nextResetAt(at(text).toEpochMilliseconds(), zone)
            ?.let { Instant.fromEpochMilliseconds(it) }

    @Test
    fun `fixed window counts from the anchor and resets on the period grid`() {
        val w = window(
            amount = 5,
            unit = ProviderQuotaWindowUnit.HOUR,
            anchorDate = "2026-11-01",
            anchorTime = "08:00",
        )

        assertEquals(at("2026-11-01T08:00:00"), w.startAt("2026-11-01T09:30:00"))
        assertEquals(at("2026-11-01T13:00:00"), w.resetAt("2026-11-01T09:30:00"))
        assertEquals(at("2026-11-01T08:00:00"), w.startAt("2026-11-01T12:59:00"))
        // 边界就在重置点：13:00 已经进入下一个周期
        assertEquals(at("2026-11-01T13:00:00"), w.startAt("2026-11-01T13:00:00"))
        assertEquals(at("2026-11-01T08:00:00"), w.startAt("2026-11-01T08:00:00"))
        // 起算点之前也按同一网格往前推，不会出现「未起算就不限额」的空档
        assertEquals(at("2026-10-31T22:00:00"), w.startAt("2026-11-01T02:30:00"))
        assertEquals(at("2026-11-01T03:00:00"), w.resetAt("2026-11-01T02:30:00"))
    }

    @Test
    fun `fixed window without an anchor follows calendar boundaries`() {
        val daily = window(amount = 1, unit = ProviderQuotaWindowUnit.DAY)
        assertEquals(at("2026-11-14T00:00:00"), daily.startAt("2026-11-14T12:00:00"))
        assertEquals(at("2026-11-15T00:00:00"), daily.resetAt("2026-11-14T12:00:00"))
        // 数量大于 1 时网格不能随「今天」漂移：间隔 5 小时的两个时刻必然落在同一个 2 天周期里
        val everyTwoDays = window(amount = 2, unit = ProviderQuotaWindowUnit.DAY)
        assertEquals(
            everyTwoDays.startAt("2026-11-15T20:00:00"),
            everyTwoDays.startAt("2026-11-16T01:00:00"),
        )
        val blockStart = everyTwoDays.startAt("2026-11-15T20:00:00").toEpochMilliseconds()
        assertEquals(
            blockStart + 2L * 24 * 60 * 60 * 1000,
            everyTwoDays.resetAt("2026-11-15T20:00:00")?.toEpochMilliseconds(),
        )
        val blockStartLocal = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(blockStart),
            zone,
        )
        assertEquals(0, blockStartLocal.hour)
        assertEquals(0, blockStartLocal.minute)

        val weekly = window(amount = 1, unit = ProviderQuotaWindowUnit.WEEK)
        // 2026-11-14 是周六，自然边界是本周一 11-09 0 点
        assertEquals(at("2026-11-09T00:00:00"), weekly.startAt("2026-11-14T12:00:00"))
        assertEquals(at("2026-11-16T00:00:00"), weekly.resetAt("2026-11-14T12:00:00"))
    }

    @Test
    fun `fixed monthly window keeps the anchor day and clamps short months`() {
        val monthly = window(
            amount = 1,
            unit = ProviderQuotaWindowUnit.MONTH,
            anchorDate = "2026-01-31",
        )

        assertEquals(at("2026-01-31T00:00:00"), monthly.startAt("2026-02-10T00:00:00"))
        assertEquals(at("2026-02-28T00:00:00"), monthly.resetAt("2026-02-10T00:00:00"))
        // 2 月只有 28 天，下一个周期到 3-31 结束
        assertEquals(at("2026-02-28T00:00:00"), monthly.startAt("2026-03-15T00:00:00"))
        assertEquals(at("2026-03-31T00:00:00"), monthly.resetAt("2026-03-15T00:00:00"))
        assertEquals(at("2026-03-31T00:00:00"), monthly.startAt("2026-04-15T00:00:00"))
        assertEquals(at("2026-04-30T00:00:00"), monthly.resetAt("2026-04-15T00:00:00"))
    }

    @Test
    fun `rolling window keeps releasing usage instead of resetting`() {
        val now = at("2026-11-14T12:00:00")
        val rolling = window(amount = 5, unit = ProviderQuotaWindowUnit.HOUR, mode = ProviderQuotaWindowMode.ROLLING)

        assertEquals(
            at("2026-11-14T07:00:00").toEpochMilliseconds(),
            rolling.windowStartAt(now.toEpochMilliseconds(), zone),
        )
        assertNull(rolling.nextResetAt(now.toEpochMilliseconds(), zone))
        assertTrue(rolling.countsAt(now.toEpochMilliseconds(), at("2026-11-14T11:00:00").toEpochMilliseconds(), zone))
        assertFalse(rolling.countsAt(now.toEpochMilliseconds(), at("2026-11-14T07:00:00").toEpochMilliseconds(), zone))
        assertEquals(
            at("2026-11-14T16:00:00").toEpochMilliseconds(),
            rolling.releaseAt(at("2026-11-14T11:00:00").toEpochMilliseconds(), zone),
        )
    }

    @Test
    fun `monthly retention leaves room for calendar months`() {
        val guard = ProviderGuard(
            windows = listOf(window(amount = 1, unit = ProviderQuotaWindowUnit.MONTH)),
        )
        assertEquals(32L * 24 * 60 * 60 * 1000, guard.usageRetentionMillis())
    }

    @Test
    fun `fixed windows survive serialization and old backups stay rolling`() {
        val fixed = window(
            amount = 2,
            unit = ProviderQuotaWindowUnit.MONTH,
            anchorDate = "2026-11-01",
            anchorTime = "08:30",
        )
        val decoded = Json.decodeFromString(
            ProviderQuotaWindow.serializer(),
            Json.encodeToString(ProviderQuotaWindow.serializer(), fixed),
        )
        assertEquals(ProviderQuotaWindowMode.FIXED, decoded.mode)
        assertEquals("2026-11-01", decoded.anchorDate)
        assertEquals("08:30", decoded.anchorTime)

        // 旧备份里没有这三个字段，按滚动窗口补齐，与旧版行为一致
        val legacy = Json.decodeFromString(ProviderQuotaWindow.serializer(), "{}")
        assertEquals(ProviderQuotaWindowMode.ROLLING, legacy.mode)
        assertEquals("", legacy.anchorDate)
    }

    @Test
    fun `disabled schedules are ignored`() {
        val guard = ProviderGuard(locks = listOf(schedule().copy(enabled = false)))
        assertFalse(guard.lockState(at("2026-11-14T23:30:00"), zone).isLocked)
        assertNull(guard.lockState(at("2026-11-14T23:30:00"), zone).nextStart)
    }
}
