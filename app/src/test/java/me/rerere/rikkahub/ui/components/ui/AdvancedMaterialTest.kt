package me.rerere.rikkahub.ui.components.ui

import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.OpticalSizeValue
import me.rerere.rikkahub.data.model.MAX_LIQUID_GLASS_REFRACTION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedMaterialTest {
    @Test
    fun `glass optics accept the full setting range`() {
        val zero = GlassOptics.regular.withAppearanceRefraction(0f, 12.dp)
        val clear = GlassOptics.regular.withAppearanceRefraction(9f, 20.dp)
        val max = GlassOptics.regular.withAppearanceRefraction(MAX_LIQUID_GLASS_REFRACTION, 24.dp)
        assertEquals(0f, zero.refractionStrength, 0f)
        assertEquals(OpticalSizeValue.Fixed(0f), zero.depth)
        assertTrue(clear.refractionStrength in 0f..1f && clear.refractionStrength < max.refractionStrength)
        assertEquals(1f, max.refractionStrength, 0f)
        assertEquals(OpticalSizeValue.Fixed(1f), max.depth)
        assertEquals(OpticalSizeValue.Fixed(24.dp), max.blurRadius)
    }

    @Test
    fun `background effect limits respect Android version and disabled effects`() {
        assertEquals(0f, resolvedLiveAppearanceRadius(40f, 30, true), 0f)
        assertEquals(12f, resolvedLiveAppearanceRadius(40f, 32, true), 0f)
        assertEquals(40f, resolvedLiveAppearanceRadius(50f, 33, true), 0f)
        assertEquals(0f, resolvedLiveAppearanceRadius(40f, 33, false), 0f)
    }
}
