package me.rerere.rikkahub.ui.pages.setting

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelPricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型编辑器草稿的对齐规则：外部写 settings（余额、联网搜索、WebUI 工具、价目表）不能覆盖用户
 * 正在编辑的草稿，但重新进入编辑器时必须读到最新已保存值。
 */
class ModelDraftStateTest {
    private val key = ModelDraftKey(providerId = "provider-1", modelId = "model-1")

    private fun model(id: String = "model-1", warn: Int? = 128, price: Double = 0.0) = Model(
        modelId = id,
        displayName = "Display $id",
        warnContextTokens = warn,
        pricing = ModelPricing(input = price),
    )

    @Test
    fun `first composition seeds from the saved model`() {
        val state = (null as ModelDraftState?).reconcile(key, model(), providerLoaded = true)

        assertEquals(key, state.key)
        assertEquals("Display model-1", state.model.displayName)
        assertEquals(128, state.model.warnContextTokens)
        assertTrue(state.locked)
    }

    @Test
    fun `provider not loaded yet does not lock the draft`() {
        val loading = (null as ModelDraftState?).reconcile(key, null, providerLoaded = false)
        assertFalse(loading.locked)

        // settings 读出来之后补上已保存值
        val loaded = loading.reconcile(key, model(), providerLoaded = true)
        assertEquals("Display model-1", loaded.model.displayName)
        assertTrue(loaded.locked)
    }

    @Test
    fun `external write does not clobber the draft`() {
        val initial = (null as ModelDraftState?).reconcile(key, model(), providerLoaded = true)
        val edited = initial.withModel(initial.model.copy(displayName = "编辑中"))

        // 别处写了同一条 provider/model：新的 Model 实例换了价目和门限
        val afterExternalWrite = edited.reconcile(key, model(warn = 512, price = 9.0), providerLoaded = true)

        assertEquals("编辑中", afterExternalWrite.model.displayName)
        assertEquals(128, afterExternalWrite.model.warnContextTokens)
        assertEquals(0.0, afterExternalWrite.model.pricing.input, 0.0)
    }

    @Test
    fun `switching to another model rebuilds the draft`() {
        val initial = (null as ModelDraftState?).reconcile(key, model(), providerLoaded = true)
        val edited = initial.withModel(initial.model.copy(displayName = "编辑中"))

        val other = ModelDraftKey(providerId = "provider-1", modelId = "model-2")
        val switched = edited.reconcile(other, model(id = "model-2", warn = 256), providerLoaded = true)

        assertEquals(other, switched.key)
        assertEquals("Display model-2", switched.model.displayName)
        assertEquals(256, switched.model.warnContextTokens)
    }

    @Test
    fun `re-entering the editor shows externally saved changes`() {
        // 上次编辑保存了 512，重新进入是新的组合：状态为 null
        val reentered = (null as ModelDraftState?).reconcile(key, model(warn = 512), providerLoaded = true)

        assertEquals(512, reentered.model.warnContextTokens)
    }

    @Test
    fun `edits made before the provider loads are dropped when it arrives`() {
        // provider 还没读出来时页面上是一张空表单，这时敲进去的东西不能当成用户的编辑
        val loading = (null as ModelDraftState?).reconcile(key, null, providerLoaded = false)
        val typedTooEarly = loading.withModel(loading.model.copy(displayName = "还没加载"), lock = false)

        val loaded = typedTooEarly.reconcile(key, model(), providerLoaded = true)
        assertEquals("Display model-1", loaded.model.displayName)
        assertTrue(loaded.locked)
    }

    @Test
    fun `new model starts empty and keeps what the user typed`() {
        val newKey = ModelDraftKey(providerId = "provider-1", modelId = null)
        val state = (null as ModelDraftState?).reconcile(newKey, null, providerLoaded = true)

        assertEquals("", state.model.modelId)
        assertTrue(state.locked)

        val edited = state.withModel(state.model.copy(modelId = "gpt-5.2", displayName = "gpt-5.2"))
        assertEquals("gpt-5.2", edited.reconcile(newKey, null, providerLoaded = true).model.modelId)
    }
}
