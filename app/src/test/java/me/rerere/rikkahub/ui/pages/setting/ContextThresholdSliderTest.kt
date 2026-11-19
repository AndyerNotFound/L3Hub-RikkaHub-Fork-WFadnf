package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.data.model.ContextThresholdPresets
import org.junit.Assert.assertEquals
import org.junit.Test

/** 门限滑杆的档位与落点：21 档，非档位值停在相邻两档之间。 */
class ContextThresholdSliderTest {
    private val stops = ContextThresholdPresets.stops

    @Test
    fun `stops are the documented 21 values in ki`() {
        assertEquals(
            listOf(
                4, 8, 16, 24, 32, 64, 96, 128, 192, 216, 256,
                288, 320, 352, 384, 448, 512, 640, 768, 896, 1024,
            ).map { it * 1024 },
            stops,
        )
    }

    @Test
    fun `a stop value sits on its own index`() {
        assertEquals(3f, contextStopPosition(stops, stops[3]), 1e-4f)
        assertEquals(0f, contextStopPosition(stops, stops.first()), 1e-4f)
        assertEquals(stops.lastIndex.toFloat(), contextStopPosition(stops, stops.last()), 1e-4f)
    }

    @Test
    fun `a value between two stops sits between them`() {
        val lower = stops[2]
        val upper = stops[3]
        assertEquals(2.5f, contextStopPosition(stops, (lower + upper) / 2), 1e-4f)
    }

    @Test
    fun `out of range values stick to the ends`() {
        assertEquals(0f, contextStopPosition(stops, null), 1e-4f)
        assertEquals(0f, contextStopPosition(stops, 1), 1e-4f)
        assertEquals(stops.lastIndex.toFloat(), contextStopPosition(stops, Int.MAX_VALUE), 1e-4f)
    }
}
