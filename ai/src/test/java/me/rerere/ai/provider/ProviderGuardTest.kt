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
    ) = ProviderLockSchedule(
        startDate = startDate,
        startTime = startTime,
        durationMinutes = minutes,
        repeat = repeat,
        repeatUntil = until,
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
            windows = listOf(ProviderQuotaWindow(amount = 5, limit = 40)),
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

        // KeyFailoverProvider 用 copy(apiKey = ...) 换 Key，不能把限流配置丢掉
        val withKey = setting.copy(apiKey = "sk-next")
        assertEquals(10, withKey.guard.rpmLimit)

        // 设置页统一走 copyProvider 更新提供商
        val renamed = setting.copyProvider(name = "Renamed")
        assertEquals(10, renamed.guard.rpmLimit)
        assertEquals("Renamed", renamed.name)
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

    @Test
    fun `disabled schedules are ignored`() {
        val guard = ProviderGuard(locks = listOf(schedule().copy(enabled = false)))
        assertFalse(guard.lockState(at("2026-11-14T23:30:00"), zone).isLocked)
        assertNull(guard.lockState(at("2026-11-14T23:30:00"), zone).nextStart)
    }
}
