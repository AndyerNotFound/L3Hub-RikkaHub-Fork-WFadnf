package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelPricing
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelRepricingTest {
    @Test
    fun `repriced swaps the price table and keeps the model identity`() {
        val model = Model(modelId = "m", pricing = ModelPricing(perCall = 1.0))
        val repriced = model.repriced(ModelPricing(perCall = 2.5))

        assertEquals(model.id, repriced.id)
        assertEquals(model.modelId, repriced.modelId)
        assertEquals(2.5, repriced.pricing.perCall, 1e-9)
        assertEquals(1.0, model.pricing.perCall, 1e-9)
    }

    @Test
    fun `missing price table keeps the snapshot`() {
        val model = Model(modelId = "m", pricing = ModelPricing(perCall = 1.0))
        assertEquals(model, model.repriced(null))
    }
}
