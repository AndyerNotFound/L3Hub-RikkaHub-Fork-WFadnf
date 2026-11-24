package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 设置里出现当前代码不认识的子类型时（例如从别的分支写进来的 `test-unknown-provider`），
 * 只丢掉那一条，不能让整个字段解码失败——整份设置读不出来，应用就直接起不来了。
 */
class SettingsLenientDecodeTest {
    @Test
    fun `unknown provider type is skipped instead of failing the whole list`() {
        val decoded = decodeListLenient<ProviderSetting>(
            """[{"type":"test-unknown-provider","id":"4a1f3d1e-0000-0000-0000-000000000000"}]"""
        )

        assertEquals(0, decoded.size)
    }

    @Test
    fun `known providers survive next to an unknown one`() {
        val providers = DEFAULT_PROVIDERS.take(2)
        val encoded = JsonInstant.encodeToString<List<ProviderSetting>>(providers)
        // 在数组尾部插一条本仓库不认识的提供商，等价于别的分支写进来的数据
        val mixed = encoded.dropLast(1) + """,{"type":"test-unknown-provider","id":"4a1f3d1e-0000-0000-0000-000000000000"}]"""

        assertEquals(providers, decodeListLenient<ProviderSetting>(mixed))
    }
}
