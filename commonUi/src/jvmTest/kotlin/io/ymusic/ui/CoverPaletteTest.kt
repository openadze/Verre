package io.ymusic.ui

import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverPaletteTest {
    private fun solid(argb: Long, n: Int = 100) = IntArray(n) { argb.toInt() }

    @Test
    fun emptyIsNeutral() = assertEquals(CoverPalette.Neutral, coverPaletteFromPixels(IntArray(0)))

    @Test
    fun redCoverGivesReddishPaletteDarkEnoughForWhiteText() {
        val p = coverPaletteFromPixels(solid(0xFFD03030))
        assertTrue(p.top.red > p.top.green && p.top.red > p.top.blue, "top should be red: ${p.top}")
        assertTrue(p.top.luminance() < 0.35f, "top too bright for white text: ${p.top.luminance()}")
        assertTrue(p.bottom.luminance() < p.top.luminance() + 0.001f)
    }

    @Test
    fun grayscaleCoverGivesGrayPalette() {
        val p = coverPaletteFromPixels(solid(0xFF808080))
        assertTrue(kotlin.math.abs(p.top.red - p.top.green) < 0.05f, "gray expected: ${p.top}")
    }

    @Test
    fun smallVividAreaBeatsBigDullArea() {
        val dull = solid(0xFF7A7570, 90) // low saturation, ignored by the saturation floor
        val vivid = solid(0xFF2060E0, 10)
        val p = coverPaletteFromPixels(dull + vivid)
        assertTrue(p.top.blue > p.top.red, "blue expected: ${p.top}")
    }

    @Test
    fun transparentPixelsAreIgnored() {
        val p = coverPaletteFromPixels(solid(0x00FF0000))
        assertEquals(CoverPalette.Neutral, p)
    }
}
