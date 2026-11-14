package com.metrolist.music.ui.theme

import org.junit.Assert.*
import org.junit.Test

class ArtworkPaletteTest {
    @Test fun `prominent chromatic color beats a neutral border`() {
        assertEquals(0xff195fdc.toInt(), ArtworkPalette.select(intArrayOf(0xffb0b0b0.toInt(),0xff195fdc.toInt()), intArrayOf(100,70)))
    }
    @Test fun `tiny saturated detail cannot displace the main color`() {
        assertEquals(0xff248447.toInt(), ArtworkPalette.select(intArrayOf(0xff248447.toInt(),0xffff1111.toInt()), intArrayOf(100,2)))
    }
    @Test fun `monochrome covers stay monochrome`() {
        assertEquals(0xffb0b0b0.toInt(), ArtworkPalette.select(intArrayOf(0xffb0b0b0.toInt(),0xff222222.toInt()), intArrayOf(100,30)))
    }
    @Test fun `bright surfaces remain readable without turning gray`() {
        val color = ArtworkPalette.surface(0xffffd510.toInt())
        assertTrue((1.05 / (ArtworkPalette.luminance(color) + .05)) >= 5.4)
        assertTrue(((color shr 16) and 255) > (color and 255) * 4)
    }
    @Test fun `empty palettes have a neutral fallback`() {
        assertEquals(0xff252529.toInt(), ArtworkPalette.select(intArrayOf(), intArrayOf()))
    }
}
