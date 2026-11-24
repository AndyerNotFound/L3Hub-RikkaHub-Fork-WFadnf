package me.rerere.rikkahub.ui.components.ai

import kotlin.uuid.Uuid
import me.rerere.ai.provider.AccountBinding
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.OAuthCredential
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型页的账号分组：一份登录一页，最后一页收没有绑定账号的模型，与多 Key 同一套规则。
 */
class ModelAccountGroupTest {

    private fun binding(name: String, disabled: Boolean = false) = AccountBinding(
        credential = OAuthCredential(accessToken = "token-$name", displayName = name),
        disabled = disabled,
    )

    private fun provider(vararg accounts: AccountBinding) =
        ProviderSetting.DeepSeekAccount(accounts = accounts.toList())

    @Test
    fun `只有一份登录时不分组`() {
        assertTrue(provider(binding("甲")).accountGroups().isEmpty())
    }

    @Test
    fun `停用的账号既不分组也不参与路由`() {
        val setting = provider(binding("甲"), binding("乙", disabled = true))
        assertTrue(setting.accountGroups().isEmpty())
        val available = setting.availableAccountIds()
        assertEquals(listOf("甲"), setting.accounts.filter { it.id in available }.map { it.credential.displayName })
    }

    @Test
    fun `分组名取昵称，绑定后只归到对应页`() {
        val first = binding("甲")
        val second = binding("乙")
        val setting = provider(first, second)
        assertEquals(listOf("甲", "乙"), setting.accountGroups().map { it.name })

        val page = setting.accountGroups().first { it.key == second.id.toString() }.key
        val bound = Model(modelId = "deepseek-chat", accountIds = listOf(second.id))
        assertTrue(bound.belongsToAccountGroup(page, setting.availableAccountIds()))
        assertFalse(bound.belongsToAccountGroup(null, setting.availableAccountIds()))
    }

    @Test
    fun `没有绑定的模型落在全部账号页`() {
        val setting = provider(binding("甲"), binding("乙"))
        assertTrue(Model(modelId = "deepseek-chat").belongsToAccountGroup(null, setting.availableAccountIds()))
    }

    @Test
    fun `卡片标签写明账号，绑定的账号没了就说不可用`() {
        val first = binding("甲")
        val second = binding("乙")
        val setting = provider(first, second)
        val names = mapOf(first.id to "甲", second.id to "乙")

        fun label(model: Model) = model.accountGroupLabel(
            provider = setting,
            availableAccountIds = setting.availableAccountIds(),
            groupNames = names,
            allAccountsName = "全部账号",
            missingName = "账号不可用",
        )

        assertEquals("乙", label(Model(modelId = "m", accountIds = listOf(second.id))))
        assertEquals("全部账号", label(Model(modelId = "m")))
        assertEquals("账号不可用", label(Model(modelId = "m", accountIds = listOf(Uuid.random()))))
    }

    @Test
    fun `单账号提供商不显示账号标签`() {
        val only = binding("甲")
        val setting = provider(only)
        assertNull(
            Model(modelId = "m", accountIds = listOf(only.id)).accountGroupLabel(
                provider = setting,
                availableAccountIds = setting.availableAccountIds(),
                groupNames = mapOf(only.id to "甲"),
                allAccountsName = "全部账号",
                missingName = "账号不可用",
            )
        )
    }
}
